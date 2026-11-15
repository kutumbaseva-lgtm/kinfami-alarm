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
