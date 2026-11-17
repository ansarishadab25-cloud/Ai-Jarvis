package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    var isHandsFreeEnabled: Boolean
        get() = prefs.getBoolean("hands_free_enabled", false)
        set(value) = prefs.edit().putBoolean("hands_free_enabled", value).apply()

    var handsFreeNoticeAcknowledged: Boolean
        get() = prefs.getBoolean("hands_free_acknowledged", false)
        set(value) = prefs.edit().putBoolean("hands_free_acknowledged", value).apply()

    var isHistoryEnabled: Boolean
        get() = prefs.getBoolean("history_enabled", true)
        set(value) = prefs.edit().putBoolean("history_enabled", value).apply()

    var preferredLanguageCode: String
        get() = prefs.getString("preferred_lang", "hi-IN") ?: "hi-IN"
        set(value) = prefs.edit().putString("preferred_lang", value).apply()

    var speechRate: Float
        get() = prefs.getFloat("speech_rate", 1.0f)
        set(value) = prefs.edit().putFloat("speech_rate", value).apply()
}
