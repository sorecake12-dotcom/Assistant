package com.jarvis.assistant.action

enum class MessagePlatform {
    SMS,
    WHATSAPP
}

enum class VideoPlatform {
    WHATSAPP,
    DEFAULT
}

enum class MediaPlatform {
    SPOTIFY,
    YOUTUBE,
    DEFAULT
}

enum class ScreenNavType {
    SCROLL_DOWN,
    SCROLL_UP,
    BACK,
    HOME,
    CLICK_TEXT
}

enum class VisionMode {
    CAMERA,
    SCREEN
}

/**
 * Global holder for recent media context to support:
 * "Play Barsaat on YouTube" -> then "Play that on Spotify"
 */
object MediaContextHolder {
    @Volatile
    var lastPlayedTrack: String? = null
    @Volatile
    var lastPlatform: MediaPlatform = MediaPlatform.DEFAULT
}

/**
 * Structured Actions for Assistant Command Architecture.
 * Natural language queries are transformed into these strongly typed actions.
 */
sealed interface AssistantAction {
    data class OpenAppAction(val appName: String, val packageName: String? = null) : AssistantAction
    data object CloseAssistantAction : AssistantAction
    data object GoHomeAction : AssistantAction
    data object OpenAssistantHomeAction : AssistantAction
    data class OpenUrlAction(val url: String, val siteName: String = "", val preferChrome: Boolean = false) : AssistantAction
    data class OpenMultipleUrlsAction(val destinations: List<SiteTarget>, val preferChrome: Boolean = false) : AssistantAction
    data class PlayMediaAction(
        val query: String? = null,
        val platform: MediaPlatform = MediaPlatform.DEFAULT
    ) : AssistantAction
    data object PauseMediaAction : AssistantAction
    data object ResumeMediaAction : AssistantAction
    data object NextMediaAction : AssistantAction
    data object PreviousMediaAction : AssistantAction
    data object StopMediaAction : AssistantAction
    data class CallContactAction(val target: String, val phoneNumber: String? = null) : AssistantAction
    data class SendMessageAction(
        val contact: String,
        val text: String,
        val platform: MessagePlatform = MessagePlatform.SMS,
        val phoneNumber: String? = null
    ) : AssistantAction
    data class VideoCallAction(
        val contact: String,
        val platform: VideoPlatform = VideoPlatform.WHATSAPP,
        val phoneNumber: String? = null
    ) : AssistantAction
    data class OpenPlayStoreAction(val appName: String, val packageName: String? = null) : AssistantAction
    data class CameraAction(val openFloating: Boolean = true) : AssistantAction
    data class VisionAction(val mode: VisionMode) : AssistantAction
    data class AccessibilityAction(val navType: ScreenNavType, val targetText: String? = null) : AssistantAction
    data object SettingsAction : AssistantAction
    data object EndSessionAction : AssistantAction
    data object WeatherAction : AssistantAction
    data class UnknownAction(val rawQuery: String) : AssistantAction
}

data class SiteTarget(
    val name: String,
    val url: String
)
