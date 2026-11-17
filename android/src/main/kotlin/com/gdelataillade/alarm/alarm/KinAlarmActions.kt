package com.gdelataillade.alarm.alarm

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gdelataillade.alarm.api.AlarmApiImpl
import com.gdelataillade.alarm.models.AlarmSettings
import com.gdelataillade.alarm.models.NotificationSettings
import com.gdelataillade.alarm.services.AlarmStorage
import com.google.gson.Gson
import io.flutter.Log
import java.util.Date

/**
 * KinFami fork (2026-11-17, Smart Reminders): Snooze / Done / Stop and the best-effort
 * notification, shared by [AlarmReceiver] and [AlarmService].
 *
 * - The buttons are handled here (or by a plain startService while the alarm service shows
 *   that alarm), never by starting the service in the foreground without calling
 *   startForeground (Android 12+ crashed the app process for that).
 * - Without "Alarms & reminders" (Android 12+) Android does not let the app start its alarm
 *   service from the background, so the alarm is shown as a normal high-priority
 *   notification with Snooze and Done instead (it may come a few minutes late).
 * - repeatCount 0 = no sound (the app's "Alarm sound" switch is off); the notification is
 *   still shown.
 */
object KinAlarmActions {
    const val SNOOZE_BIT = 0x40000000
    const val DONE_KEY = "flutter.kinfami_alarm_done"

    private const val CHANNEL_SOUND = "kinfami_smart_reminders"
    private const val CHANNEL_SILENT = "kinfami_smart_reminders_silent"
    private const val CHANNEL_NAME = "Smart Reminders"
    private const val CHANNEL_SILENT_NAME = "Smart Reminders (silent)"

    private const val PENDING_PREFIX = "flutter.kinfami_pending_"

    /** Auto-snooze: how many times an unanswered alarm comes back by itself. */
    const val MAX_AUTO_SNOOZES = 3
    private const val AUTO_PREFIX = "flutter.kinfami_auto_"

    private fun prefs(context: Context) =
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    /**
     * The alarm behind a shown notification. The app removes past alarms from the package's
     * storage when it opens, so a copy is kept while the notification is shown ([remember]);
     * Snooze and Done still find the reminder then.
     */
    fun savedAlarm(context: Context, id: Int): AlarmSettings? =
        AlarmStorage(context).getSavedAlarms().firstOrNull { it.id == id }
            ?: prefs(context).getString(PENDING_PREFIX + id, null)?.let { AlarmSettings.fromJson(it) }

    /** Keep a copy of the alarm while its notification is shown. */
    fun remember(context: Context, id: Int) {
        val saved = AlarmStorage(context).getSavedAlarms().firstOrNull { it.id == id } ?: return
        prefs(context).edit().putString(PENDING_PREFIX + id, saved.toJson()).apply()
    }

    fun forgetPending(context: Context, id: Int) {
        prefs(context).edit().remove(PENDING_PREFIX + id).apply()
    }

    /** Stop and forget one alarm that is not ringing in the service (fallback notification). */
    fun forget(context: Context, id: Int) {
        AlarmStorage(context).unsaveAlarm(id)
        forgetPending(context, id)
        cancelNotification(context, id)
        AlarmPlugin.alarmTriggerApi?.alarmStopped(id.toLong()) {}
    }

    /**
     * Set [saved] again in its snooze minutes under the snooze id (id with bit 30 set), saved so
     * it survives a reboot. Same title, texts, repeat count, pause and tag (reminder id). Call it
     * after the ringing alarm was forgotten: a snoozed alarm snoozed again keeps the same id.
     * Returns false when the alarm has no snooze (Snooze off) or is unknown.
     */
    fun snooze(context: Context, saved: AlarmSettings?): Boolean {
        if (saved == null) return false
        val minutes = saved.notificationSettings.snoozeMinutes ?: return false
        if (minutes <= 0L) return false
        val snoozed = saved.copy(
            id = saved.id or SNOOZE_BIT,
            dateTime = Date(System.currentTimeMillis() + minutes.coerceIn(1L, 120L) * 60_000L)
        )
        AlarmStorage(context).saveAlarm(snoozed)
        AlarmApiImpl(context).setAlarm(snoozed) // exact when allowed, else inexact
        Log.d("KinAlarmActions", "Alarm ${saved.id} snoozed until ${snoozed.dateTime}")
        return true
    }

    /**
     * Done: cancel a pending snooze of this alarm and leave a note ("tag|time") for the app,
     * which marks the reminder done the next time it runs. [saved] is read before forgetting.
     */
    fun done(context: Context, id: Int, saved: AlarmSettings?) {
        val snoozeId = id or SNOOZE_BIT
        if (snoozeId != id) {
            AlarmApiImpl(context).stopAlarm(snoozeId.toLong())
            AlarmStorage(context).unsaveAlarm(snoozeId)
        } else {
            // Auto-snooze: a snoozed alarm re-arms itself under its own id while it rings;
            // Done must cancel that too (without stopping the service, which handles Done).
            cancelScheduled(context, snoozeId)
            AlarmStorage(context).unsaveAlarm(snoozeId)
        }
        resetAuto(context, id)
        val tag = saved?.notificationSettings?.tag ?: return
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val old = prefs.getString(DONE_KEY, "") ?: ""
        prefs.edit().putString(DONE_KEY, old + tag + "|" + System.currentTimeMillis() + "\n").apply()
    }

    // ------------------------------------------------------------------ auto-snooze
    // KinFami fork (2026-11-17, auto-snooze): when an alarm starts ringing, its snooze is set
    // right away, as if Snooze had been pressed. If nobody presses anything (asleep, phone in
    // another room) it rings again after the snooze minutes, up to MAX_AUTO_SNOOZES times.
    // Snooze replaces it (same id), Done and Stop cancel it. Everything is native, so it works
    // with KinFami closed. Snooze Off or "Alarm sound" off = no auto-snooze.

    /** Cancel the AlarmManager entry of [id] only (no service stop, storage untouched). */
    fun cancelScheduled(context: Context, id: Int) {
        try {
            val pi = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
            (context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).cancel(pi)
            pi.cancel()
        } catch (e: Exception) {
            Log.e("KinAlarmActions", "Could not cancel alarm $id", e)
        }
    }

    /** Call when alarm [id] starts ringing (after [remember]). */
    fun armAutoSnooze(context: Context, id: Int) {
        val saved = savedAlarm(context, id) ?: return
        val minutes = saved.notificationSettings.snoozeMinutes ?: return
        if (minutes <= 0L || (saved.notificationSettings.repeatCount ?: 1L) <= 0L) return
        val base = id and SNOOZE_BIT.inv()
        val p = prefs(context)
        // The reminder's own alarm starts a new count; a snoozed alarm continues it.
        val count = if ((id and SNOOZE_BIT) == 0) 0 else p.getInt(AUTO_PREFIX + base, 0)
        if (count >= MAX_AUTO_SNOOZES) {
            p.edit().remove(AUTO_PREFIX + base).apply()
            return
        }
        p.edit().putInt(AUTO_PREFIX + base, count + 1).apply()
        snooze(context, saved)
        Log.d("KinAlarmActions", "Alarm $id auto-snooze ${count + 1}/$MAX_AUTO_SNOOZES armed")
    }

    /** Stop: the user heard it — no automatic ring again. */
    fun cancelAutoSnooze(context: Context, id: Int) {
        val snoozeId = id or SNOOZE_BIT
        cancelScheduled(context, snoozeId)
        AlarmStorage(context).unsaveAlarm(snoozeId)
        forgetPending(context, snoozeId)
        resetAuto(context, id)
        AlarmPlugin.alarmTriggerApi?.alarmStopped(snoozeId.toLong()) {}
    }

    /** Snooze / Done / Stop pressed: the next unanswered ring gets the full count again. */
    fun resetAuto(context: Context, id: Int) {
        prefs(context).edit().remove(AUTO_PREFIX + (id and SNOOZE_BIT.inv())).apply()
    }

    fun cancelNotification(context: Context, id: Int) {
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id)
        } catch (e: Exception) {
            Log.e("KinAlarmActions", "Could not cancel notification $id", e)
        }
    }

    /** A notification button: a broadcast to [AlarmReceiver] carrying the alarm id. */
    fun buttonIntent(context: Context, actionName: String, alarmId: Int, slot: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = actionName
            putExtra("id", alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            (alarmId.toLong() * 4L + slot).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun notificationSettingsOf(intent: Intent): NotificationSettings {
        val json = intent.getStringExtra("notificationSettings")
        if (json != null) {
            try {
                return Gson().fromJson(json, NotificationSettings::class.java)
            } catch (e: Exception) {
                Log.e("KinAlarmActions", "Bad notification settings", e)
            }
        }
        return NotificationSettings(
            intent.getStringExtra("notificationTitle") ?: "Smart Reminder",
            intent.getStringExtra("notificationBody") ?: ""
        )
    }

    /**
     * Best-effort alarm: a normal high-priority notification (sound and vibration from the
     * channel; silent channel when the alarm has no sound) with Snooze and Done. The alarm stays
     * saved until Snooze / Done, so Snooze finds it.
     */
    @SuppressLint("DiscouragedApi")
    fun showFallback(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        if (id == 0) return
        val ns = notificationSettingsOf(intent)
        val silent = (ns.repeatCount ?: 1L) <= 0L
        remember(context, id)
        // Auto-snooze: an unanswered earlier notification of the same reminder is replaced.
        val base = id and SNOOZE_BIT.inv()
        if (base != id) {
            cancelNotification(context, base)
            forgetPending(context, base)
        }
        armAutoSnooze(context, id)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SOUND, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
                    .apply { enableVibration(true) }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SILENT, CHANNEL_SILENT_NAME, NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        setSound(null, null)
                        enableVibration(true)
                    }
            )
        }
        val appInfoIcon = context.packageManager.getApplicationInfo(context.packageName, 0).icon
        val icon = ns.icon?.let {
            val r = context.resources.getIdentifier(it, "drawable", context.packageName)
            if (r != 0) r else null
        } ?: appInfoIcon
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val open = PendingIntent.getActivity(
            context, id, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(context, if (silent) CHANNEL_SILENT else CHANNEL_SOUND)
            .setSmallIcon(icon)
            .setContentTitle(ns.title)
            .setContentText(ns.body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (ns.snoozeButton != null && (ns.snoozeMinutes ?: 0L) > 0L) {
            b.addAction(0, ns.snoozeButton, buttonIntent(context, AlarmReceiver.ACTION_ALARM_SNOOZE, id, 1))
        }
        if (ns.doneButton != null) {
            b.addAction(0, ns.doneButton, buttonIntent(context, AlarmReceiver.ACTION_ALARM_DONE, id, 2))
        }
        try {
            nm.notify(id, b.build())
        } catch (e: SecurityException) {
            Log.e("KinAlarmActions", "Notifications not allowed", e)
        }
        AlarmPlugin.alarmTriggerApi?.alarmRang(id.toLong()) {}
    }
}
