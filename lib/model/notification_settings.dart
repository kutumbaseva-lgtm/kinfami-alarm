import 'package:alarm/src/generated/platform_bindings.g.dart';
import 'package:flutter/widgets.dart';

/// Model for notification settings.
///
/// KinFami fork: adds [snoozeButton], [doneButton], [snoozeMinutes],
/// [repeatCount], [repeatGapMs] and [tag] (Android only). All are optional;
/// without them the package behaves exactly like alarm 4.1.1.
@immutable
class NotificationSettings {
  /// Constructs an instance of `NotificationSettings`.
  const NotificationSettings({
    required this.title,
    required this.body,
    this.stopButton,
    this.icon,
    this.snoozeButton,
    this.doneButton,
    this.snoozeMinutes,
    this.repeatCount,
    this.repeatGapMs,
    this.tag,
  });

  /// Converts from wire datatype.
  NotificationSettings.fromWire(NotificationSettingsWire wire)
      : title = wire.title,
        body = wire.body,
        stopButton = wire.stopButton,
        icon = wire.icon,
        snoozeButton = wire.snoozeButton,
        doneButton = wire.doneButton,
        snoozeMinutes = wire.snoozeMinutes,
        repeatCount = wire.repeatCount,
        repeatGapMs = wire.repeatGapMs,
        tag = wire.tag;

  /// Constructs an instance of `NotificationSettings` from a JSON object.
  factory NotificationSettings.fromJson(Map<String, dynamic> json) =>
      NotificationSettings(
        title: json['title'] as String,
        body: json['body'] as String,
        stopButton: json['stopButton'] as String?,
        icon: json['icon'] as String?,
        snoozeButton: json['snoozeButton'] as String?,
        doneButton: json['doneButton'] as String?,
        snoozeMinutes: (json['snoozeMinutes'] as num?)?.toInt(),
        repeatCount: (json['repeatCount'] as num?)?.toInt(),
        repeatGapMs: (json['repeatGapMs'] as num?)?.toInt(),
        tag: json['tag'] as String?,
      );

  /// Title of the notification to be shown when alarm is triggered.
  final String title;

  /// Body of the notification to be shown when alarm is triggered.
  final String body;

  /// The text to display on the stop button of the notification.
  ///
  /// Won't work on iOS if app was killed.
  /// If null, button will not be shown. Null by default.
  final String? stopButton;

  /// The icon to display on the notification (Android `res/drawable` name).
  /// If `null`, the default app icon will be used.
  final String? icon;

  /// KinFami fork (Android): text of the Snooze button; null = no button.
  final String? snoozeButton;

  /// KinFami fork (Android): text of the Done button; null = no button.
  final String? doneButton;

  /// KinFami fork (Android): snooze length in minutes (default 10).
  final int? snoozeMinutes;

  /// KinFami fork (Android): how many times the sound plays (1–5, default 1).
  final int? repeatCount;

  /// KinFami fork (Android): pause between plays in milliseconds (default 0).
  final int? repeatGapMs;

  /// KinFami fork (Android): app tag (e.g. reminder id), reported back
  /// when Done is pressed.
  final String? tag;

  /// Converts to wire datatype which is used for host platform communication.
  NotificationSettingsWire toWire() => NotificationSettingsWire(
        title: title,
        body: body,
        stopButton: stopButton,
        icon: icon,
        snoozeButton: snoozeButton,
        doneButton: doneButton,
        snoozeMinutes: snoozeMinutes,
        repeatCount: repeatCount,
        repeatGapMs: repeatGapMs,
        tag: tag,
      );

  /// Converts the `NotificationSettings` instance to a JSON object.
  Map<String, dynamic> toJson() => {
        'title': title,
        'body': body,
        'stopButton': stopButton,
        'icon': icon,
        'snoozeButton': snoozeButton,
        'doneButton': doneButton,
        'snoozeMinutes': snoozeMinutes,
        'repeatCount': repeatCount,
        'repeatGapMs': repeatGapMs,
        'tag': tag,
      };

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is NotificationSettings &&
          runtimeType == other.runtimeType &&
          title == other.title &&
          body == other.body &&
          stopButton == other.stopButton &&
          icon == other.icon &&
          snoozeButton == other.snoozeButton &&
          doneButton == other.doneButton &&
          snoozeMinutes == other.snoozeMinutes &&
          repeatCount == other.repeatCount &&
          repeatGapMs == other.repeatGapMs &&
          tag == other.tag;

  @override
  int get hashCode => Object.hash(
        title,
        body,
        stopButton,
        icon,
        snoozeButton,
        doneButton,
        snoozeMinutes,
        repeatCount,
        repeatGapMs,
        tag,
      );

  @override
  String toString() => 'NotificationSettings: ${toJson()}';

  /// Creates a copy of `NotificationSettings` but with the given fields
  /// replaced with the new values.
  NotificationSettings copyWith({
    String? title,
    String? body,
    String? stopButton,
    String? icon,
    String? snoozeButton,
    String? doneButton,
    int? snoozeMinutes,
    int? repeatCount,
    int? repeatGapMs,
    String? tag,
  }) {
    assert(title != null, 'NotificationSettings.title cannot be null');
    assert(body != null, 'NotificationSettings.body cannot be null');

    return NotificationSettings(
      title: title ?? this.title,
      body: body ?? this.body,
      stopButton: stopButton ?? this.stopButton,
      icon: icon ?? this.icon,
      snoozeButton: snoozeButton ?? this.snoozeButton,
      doneButton: doneButton ?? this.doneButton,
      snoozeMinutes: snoozeMinutes ?? this.snoozeMinutes,
      repeatCount: repeatCount ?? this.repeatCount,
      repeatGapMs: repeatGapMs ?? this.repeatGapMs,
      tag: tag ?? this.tag,
    );
  }
}
