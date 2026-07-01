package com.kleber.radar.util

import android.content.Context

object AccessibilityDebugStore {
    private const val PREFS_NAME = "accessibility_debug"
    private const val KEY_SERVICE_CONNECTED_AT = "service_connected_at"
    private const val KEY_LAST_EVENT_AT = "last_event_at"
    private const val KEY_LAST_CAPTURE_AT = "last_capture_at"
    private const val KEY_LAST_PACKAGE = "last_package"
    private const val KEY_LAST_CLASS = "last_class"
    private const val KEY_LAST_EVENT_TYPE = "last_event_type"
    private const val KEY_LAST_TEXT_COUNT = "last_text_count"
    private const val KEY_LAST_TEXT = "last_text"

    data class State(
        val serviceConnectedAt: Long,
        val lastEventAt: Long,
        val lastCaptureAt: Long,
        val lastPackage: String,
        val lastClass: String,
        val lastEventType: Int,
        val lastTextCount: Int,
        val lastText: String
    )

    fun markServiceConnected(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SERVICE_CONNECTED_AT, System.currentTimeMillis())
            .apply()
    }

    fun saveEvent(context: Context, packageName: String, className: String, eventType: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_EVENT_AT, System.currentTimeMillis())
            .putString(KEY_LAST_PACKAGE, packageName)
            .putString(KEY_LAST_CLASS, className)
            .putInt(KEY_LAST_EVENT_TYPE, eventType)
            .apply()
    }

    fun saveCapture(
        context: Context,
        packageName: String,
        className: String,
        eventType: Int,
        textCount: Int,
        text: String
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CAPTURE_AT, System.currentTimeMillis())
            .putString(KEY_LAST_PACKAGE, packageName)
            .putString(KEY_LAST_CLASS, className)
            .putInt(KEY_LAST_EVENT_TYPE, eventType)
            .putInt(KEY_LAST_TEXT_COUNT, textCount)
            .putString(KEY_LAST_TEXT, text.take(8_000))
            .apply()
    }

    fun read(context: Context): State {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return State(
            serviceConnectedAt = prefs.getLong(KEY_SERVICE_CONNECTED_AT, 0L),
            lastEventAt = prefs.getLong(KEY_LAST_EVENT_AT, 0L),
            lastCaptureAt = prefs.getLong(KEY_LAST_CAPTURE_AT, 0L),
            lastPackage = prefs.getString(KEY_LAST_PACKAGE, "") ?: "",
            lastClass = prefs.getString(KEY_LAST_CLASS, "") ?: "",
            lastEventType = prefs.getInt(KEY_LAST_EVENT_TYPE, -1),
            lastTextCount = prefs.getInt(KEY_LAST_TEXT_COUNT, 0),
            lastText = prefs.getString(KEY_LAST_TEXT, "") ?: ""
        )
    }
}
