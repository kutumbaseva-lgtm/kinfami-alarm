package com.gdelataillade.alarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.flutter.Log

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ALARM_STOP = "com.gdelataillade.alarm.ACTION_STOP"
        // KinFami fork: notification buttons Snooze and Done.
        const val ACTION_ALARM_SNOOZE = "com.gdelataillade.alarm.ACTION_SNOOZE"
        const val ACTION_ALARM_DONE = "com.gdelataillade.alarm.ACTION_DONE"
        const val EXTRA_ALARM_ACTION = "EXTRA_ALARM_ACTION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val button = when (intent.action) {
            ACTION_ALARM_STOP -> "STOP_ALARM"
            ACTION_ALARM_SNOOZE -> "SNOOZE_ALARM"
            ACTION_ALARM_DONE -> "DONE_ALARM"
            else -> null
        }

        // KinFami fork (2026-11-17): a button. If the alarm service shows this alarm, it stops the
        // sound: a plain startService (allowed while the service is in the foreground; no
        // foreground start, so no "did not call startForeground" crash). Otherwise (best-effort
        // notification, or the service is gone) the button is handled right here.
        if (button != null) {
            val id = intent.getIntExtra("id", 0)
            if (id == 0) return
            if (AlarmService.shownIds.contains(id)) {
                try {
                    val serviceIntent = Intent(context, AlarmService::class.java)
                    serviceIntent.putExtras(intent)
                    serviceIntent.putExtra(EXTRA_ALARM_ACTION, button)
                    context.startService(serviceIntent)
                    return
                } catch (e: Exception) {
                    Log.e("AlarmReceiver", "Could not reach the alarm service", e)
                }
            }
            val saved = KinAlarmActions.savedAlarm(context, id)
            KinAlarmActions.forget(context, id)
            when (button) {
                "SNOOZE_ALARM" -> {
                    KinAlarmActions.resetAuto(context, id)
                    KinAlarmActions.snooze(context, saved) // replaces a pending auto-snooze
                }
                "DONE_ALARM" -> KinAlarmActions.done(context, id, saved)
                "STOP_ALARM" -> KinAlarmActions.cancelAutoSnooze(context, id)
            }
            return
        }

        // KinFami fork: without "Alarms & reminders" (Android 12+) the alarm came as an inexact
        // alarm and Android does not let the app start its alarm service from the background, so
        // the reminder is shown as a normal notification instead (best effort, Snooze + Done).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (am != null && !am.canScheduleExactAlarms()) {
                KinAlarmActions.showFallback(context, intent)
                return
            }
        }

        // Start Alarm Service
        val serviceIntent = Intent(context, AlarmService::class.java)
        serviceIntent.putExtras(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val pendingIntent = PendingIntent.getForegroundService(
                context,
                1,
                serviceIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            pendingIntent.send()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
