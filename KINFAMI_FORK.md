# KinFami fork of `alarm` 4.1.1

Fork of [gdelataillade/alarm](https://github.com/gdelataillade/alarm) 4.1.1 (MIT licence, unchanged — see LICENSE),
used by the KinFami app. The first commit is the package exactly as published on pub.dev; changes are in later commits.

Changes (2026-11-15):
1. `android/src/main/AndroidManifest.xml`: removed `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`,
   `READ_EXTERNAL_STORAGE` and `ACCESS_NOTIFICATION_POLICY` (Google Play policy: KinFami is not an alarm-clock,
   calendar or calling app; the last two were not used). `SCHEDULE_EXACT_ALARM` stays (the user allows
   "Alarms & reminders").
2. `AlarmApiImpl.kt`: if exact alarms are not allowed (Android 12+), set an inexact `setAndAllowWhileIdle`
   alarm instead of failing silently.
3. `pubspec.yaml` version `4.1.1+kinfami.1`.

No other code is changed. Not published to pub.dev.

Changes (2026-11-15, second set — Reminders Stage 3/4):
4. `NotificationSettings` (Dart + Android, incl. the generated bindings) gains optional `snoozeButton`, `doneButton`,
   `snoozeMinutes`, `repeatCount`, `repeatGapMs`, `tag`. Without them the package behaves as 4.1.1.
5. `AudioService.kt`: the sound plays `repeatCount` times with `repeatGapMs` in between; Stop/Done cancels the rest.
6. `NotificationService.kt` / `AlarmReceiver.kt` / `AlarmService.kt`: Snooze and Done buttons on the alarm
   notification. Snooze sets the same alarm again after `snoozeMinutes` under id `id | 0x40000000` (saved, so it
   survives a reboot) — works with the app closed. Done stops the ring, cancels a pending snooze of it and appends
   `tag|time` to SharedPreferences key `kinfami_alarm_done` for the app to apply.

Changes (2026-11-17, third set — Smart Reminders):
7. New `KinAlarmActions.kt`: Snooze / Done / Stop logic shared by the receiver and the service, and the best-effort
   notification. `AlarmReceiver.kt` handles a button itself unless the alarm service shows that alarm (then a plain
   `startService`). Before, every button started the service with `startForegroundService` without
   `startForeground`, which Android 12+ answers by crashing the app process.
8. Without "Alarms & reminders" (Android 12+), the alarm is shown as a normal high-priority notification (channel
   "Smart Reminders", or "Smart Reminders (silent)") with Snooze and Done, instead of a foreground service that
   Android does not allow from the background (the alarm used to disappear). It may come a few minutes late.
9. `repeatCount` 0 = no sound (the app's "Alarm sound" switch is off): the notification is still shown, a short
   vibration, no Stop button. `snoozeMinutes` null or 0 = no Snooze button.
10. Alarm notification channel name "Alarm Notification" -> "Smart Reminders" (channel id unchanged).
11. `pubspec.yaml` version `4.1.1+kinfami.2`. No new permissions. No Dart API change.

Changes (2026-11-17, fourth set — auto-snooze):
12. Auto-snooze: when an alarm starts ringing (alarm service or best-effort notification), its snooze is set at once
    under the snooze id (`id | 0x40000000`), as if Snooze had been pressed. If nobody answers, it rings again after
    `snoozeMinutes`, at most 3 times (`KinAlarmActions.MAX_AUTO_SNOOZES`; count in SharedPreferences
    `kinfami_auto_<id>`, reset when the reminder's own alarm rings or a button is pressed). Snooze replaces it (same
    id), Done and Stop cancel it. Snooze Off or no sound (`repeatCount` 0) = no auto-snooze. Native only, works with
    the app closed; no Dart change (the app already shows it as "Snoozed until HH:MM").
13. `AlarmService.kt`: "already ringing" is now worked out when an alarm starts (`AudioService.getActiveIds`: playing
    or between plays). Before, an alarm left unanswered counted as ringing until the service stopped, so later alarms
    — including its snooze — were dropped. An earlier unanswered ring of the same reminder is replaced by the new one.
14. `KinAlarmActions.done`: Done on a snoozed alarm also cancels its own re-armed auto-snooze.
15. `pubspec.yaml` version `4.1.1+kinfami.3`. No new permissions (still no `USE_EXACT_ALARM`). No Dart API change.
