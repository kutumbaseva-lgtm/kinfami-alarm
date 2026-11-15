package com.gdelataillade.alarm.models

import NotificationSettingsWire
import com.google.gson.Gson

// KinFami fork: snoozeButton, doneButton, snoozeMinutes, repeatCount, repeatGapMs and tag
// are optional (nullable, so alarms saved by older versions still load with Gson).
data class NotificationSettings(
    val title: String,
    val body: String,
    val stopButton: String? = null,
    val icon: String? = null,
    val snoozeButton: String? = null,
    val doneButton: String? = null,
    val snoozeMinutes: Long? = null,
    val repeatCount: Long? = null,
    val repeatGapMs: Long? = null,
    val tag: String? = null
) {
    companion object {
        fun fromWire(e: NotificationSettingsWire): NotificationSettings {
            return NotificationSettings(
                e.title,
                e.body,
                e.stopButton,
                e.icon,
                e.snoozeButton,
                e.doneButton,
                e.snoozeMinutes,
                e.repeatCount,
                e.repeatGapMs,
                e.tag,
            )
        }
    }

    fun toWire(): NotificationSettingsWire {
        return NotificationSettingsWire(
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
        )
    }

    fun toJson(): String {
        return Gson().toJson(this)
    }
}
