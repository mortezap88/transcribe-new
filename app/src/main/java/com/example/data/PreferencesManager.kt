package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("transcribe_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model_name"
        private const val KEY_CORRECTION_MODEL = "correction_model_name"
        private const val KEY_FONT_SIZE = "font_size_sp"
        private const val KEY_SILENCE_DURATION = "silence_duration_ms"
        private const val KEY_SENSITIVITY = "sensitivity_threshold"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_ENABLE_SMOOTHING = "enable_smoothing"
        private const val KEY_ENABLE_GRAMMAR = "enable_grammar"

        const val DEFAULT_MODEL = "gemini-3.5-transcribe-live"
        const val DEFAULT_CORRECTION_MODEL = "gemini-3.5-flash-lite"
        const val DEFAULT_FONT_SIZE = 24f
        const val DEFAULT_SILENCE_DURATION_MS = 1000L
        const val DEFAULT_SENSITIVITY = 450
    }

    var apiKey: String
        get() {
            val saved = prefs.getString(KEY_API_KEY, null)
            if (!saved.isNullOrBlank()) {
                return saved
            }
            val buildConfigKey = try {
                BuildConfig.GEMINI_API_KEY
            } catch (e: Throwable) {
                ""
            }
            return if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY") {
                buildConfigKey
            } else {
                ""
            }
        }
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value.trim()).apply()
        }

    var modelName: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL)?.ifBlank { DEFAULT_MODEL } ?: DEFAULT_MODEL
        set(value) {
            prefs.edit().putString(KEY_MODEL, value.trim()).apply()
        }

    var correctionModelName: String
        get() = prefs.getString(KEY_CORRECTION_MODEL, DEFAULT_CORRECTION_MODEL)?.ifBlank { DEFAULT_CORRECTION_MODEL } ?: DEFAULT_CORRECTION_MODEL
        set(value) {
            prefs.edit().putString(KEY_CORRECTION_MODEL, value.trim()).apply()
        }

    var fontSizeSp: Float
        get() = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        set(value) {
            prefs.edit().putFloat(KEY_FONT_SIZE, value.coerceIn(14f, 48f)).apply()
        }

    var silenceDurationMs: Long
        get() = prefs.getLong(KEY_SILENCE_DURATION, DEFAULT_SILENCE_DURATION_MS)
        set(value) {
            prefs.edit().putLong(KEY_SILENCE_DURATION, value.coerceIn(400L, 4000L)).apply()
        }

    var sensitivityThreshold: Int
        get() = prefs.getInt(KEY_SENSITIVITY, DEFAULT_SENSITIVITY)
        set(value) {
            prefs.edit().putInt(KEY_SENSITIVITY, value.coerceIn(200, 2500)).apply()
        }

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) {
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()
        }

    var enableSmoothing: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_SMOOTHING, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLE_SMOOTHING, value).apply()
        }

    var enableGrammarCheck: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_GRAMMAR, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLE_GRAMMAR, value).apply()
        }

    var savedTranscript: String
        get() = prefs.getString("saved_transcript", "") ?: ""
        set(value) {
            prefs.edit().putString("saved_transcript", value).apply()
        }
}
