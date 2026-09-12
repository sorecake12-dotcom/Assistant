package com.jarvis.assistant.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jarvis.assistant.data.model.ChatTurn
import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.personality.PersonalityMode

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREF_NAME = "jarvis_prefs"
        private const val KEY_API_KEY = "key_api_key"
        private const val KEY_MODEL = "key_model"
        private const val KEY_VOICE = "key_voice"
        private const val KEY_PERSONALITY = "key_personality"
        private const val KEY_USER_NAME = "key_user_name"
        private const val KEY_MIC_MUTED = "key_mic_muted"
        private const val KEY_CHAT_HISTORY = "key_chat_history"
        private const val KEY_THEME = "key_theme"
        private const val KEY_PREFERRED_SIM = "key_preferred_sim"
        private const val KEY_BACKGROUND_VOICE = "key_background_voice"
        private const val KEY_WAKE_WORD = "key_wake_word"
        private const val KEY_ASSISTANT_NAME = "key_assistant_name"
        private const val KEY_FIRST_LAUNCH_COMPLETE = "key_first_launch_complete"
    }

    var isFirstLaunchComplete: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH_COMPLETE, value).apply()

    var isBackgroundVoiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_VOICE, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_VOICE, value).apply()

    var assistantName: String
        get() = prefs.getString(KEY_ASSISTANT_NAME, "Jarvis")?.trim()?.ifEmpty { "Jarvis" } ?: "Jarvis"
        set(value) {
            val normalized = value.trim().ifEmpty { "Jarvis" }
            prefs.edit().putString(KEY_ASSISTANT_NAME, normalized).apply()
            wakeWord = "Hello $normalized"
        }

    var wakeWord: String
        get() {
            val stored = prefs.getString(KEY_WAKE_WORD, null)
            return if (!stored.isNullOrBlank()) stored else "Hello $assistantName"
        }
        set(value) = prefs.edit().putString(KEY_WAKE_WORD, value.trim()).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var aiModel: String
        get() {
            val stored = prefs.getString(KEY_MODEL, GeminiConstants.DEFAULT_MODEL) ?: GeminiConstants.DEFAULT_MODEL
            return if (GeminiConstants.SUPPORTED_MODELS.contains(stored)) {
                stored
            } else {
                GeminiConstants.DEFAULT_MODEL
            }
        }
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var voice: String
        get() = prefs.getString(KEY_VOICE, "Aoede") ?: "Aoede"
        set(value) = prefs.edit().putString(KEY_VOICE, value).apply()

    var personality: String
        get() = prefs.getString(KEY_PERSONALITY, GeminiConstants.PERSONALITY_AI_ASSISTANT) ?: GeminiConstants.PERSONALITY_AI_ASSISTANT
        set(value) = prefs.edit().putString(KEY_PERSONALITY, value).apply()

    var personalityMode: PersonalityMode
        get() = PersonalityMode.fromId(personality)
        set(value) { personality = value.id }

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "Boss") ?: "Boss"
        set(value) = prefs.edit().putString(KEY_USER_NAME, value.trim()).apply()

    var isMicMuted: Boolean
        get() = prefs.getBoolean(KEY_MIC_MUTED, false)
        set(value) = prefs.edit().putBoolean(KEY_MIC_MUTED, value).apply()

    var theme: String
        get() = prefs.getString(KEY_THEME, "ARC_BLUE") ?: "ARC_BLUE"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var preferredSim: String
        get() = prefs.getString(KEY_PREFERRED_SIM, "SIM 1") ?: "SIM 1"
        set(value) = prefs.edit().putString(KEY_PREFERRED_SIM, value).apply()

    fun loadChatHistory(): List<ChatTurn> {
        val json = prefs.getString(KEY_CHAT_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ChatTurn>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveChatHistory(history: List<ChatTurn>) {
        val json = gson.toJson(history)
        prefs.edit().putString(KEY_CHAT_HISTORY, json).apply()
    }

    fun clearChatHistory() {
        prefs.edit().remove(KEY_CHAT_HISTORY).apply()
    }
}
