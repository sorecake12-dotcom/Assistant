package com.jarvis.assistant.data.model

enum class ConversationState(val displayName: String, val orbKey: String) {
    OFF("OFFLINE", "offline"),
    ENDED("SESSION ENDED", "offline"),
    IDLE("READY", "idle"),
    ACTIVE("ACTIVE", "idle"),
    BACKGROUND_LISTENING("WAKE WORD ACTIVE", "background_listening"),
    WAKE_DETECTED("WAKE DETECTED", "wake_detected"),
    LISTENING("LISTENING", "listening"),
    SILENT("SILENT MODE", "silent"),
    THINKING("THINKING", "thinking"),
    SPEAKING("SPEAKING", "speaking"),
    EXECUTING("EXECUTING", "executing"),
    VISION_CAMERA("CAMERA ACTIVE", "vision_camera"),
    VISION_SCREEN("VISION ACTIVE", "vision_screen"),
    MUTED("MUTED", "muted"),
    ERROR("ERROR", "error")
}
