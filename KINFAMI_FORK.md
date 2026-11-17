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
