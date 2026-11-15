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
