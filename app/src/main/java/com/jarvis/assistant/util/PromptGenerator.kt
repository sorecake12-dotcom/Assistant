package com.jarvis.assistant.util

import com.jarvis.assistant.personality.PersonalityManager
import com.jarvis.assistant.personality.PersonalityMode

object PromptGenerator {
    fun generateSystemPrompt(personality: String, userName: String): String {
        val mode = PersonalityMode.fromId(personality)
        return PersonalityManager.getSystemPrompt(mode, userName)
    }

    fun generateSystemPrompt(mode: PersonalityMode, userName: String): String {
        return PersonalityManager.getSystemPrompt(mode, userName)
    }
}
