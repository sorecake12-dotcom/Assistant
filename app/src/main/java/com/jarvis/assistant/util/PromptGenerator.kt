package com.jarvis.assistant.util

import com.jarvis.assistant.personality.PersonalityManager
import com.jarvis.assistant.personality.PersonalityMode

object PromptGenerator {
    fun generateSystemPrompt(personality: String, userName: String, assistantName: String = "Jarvis"): String {
        val mode = PersonalityMode.fromId(personality)
        return PersonalityManager.getSystemPrompt(mode, userName, assistantName)
    }

    fun generateSystemPrompt(mode: PersonalityMode, userName: String, assistantName: String = "Jarvis"): String {
        return PersonalityManager.getSystemPrompt(mode, userName, assistantName)
    }
}
