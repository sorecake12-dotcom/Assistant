package com.jarvis.assistant.action

import java.util.Locale

object ActionParser {

    val KNOWN_WEBSITES = mapOf(
        "vercel" to "https://vercel.com",
        "netflix" to "https://www.netflix.com",
        "prime video" to "https://www.primevideo.com",
        "primevideo" to "https://www.primevideo.com",
        "amazon prime" to "https://www.primevideo.com",
        "youtube" to "https://www.youtube.com",
        "github" to "https://github.com",
        "gmail" to "https://mail.google.com",
        "google" to "https://www.google.com",
        "reddit" to "https://www.reddit.com",
        "twitter" to "https://x.com",
        "x" to "https://x.com",
        "wikipedia" to "https://www.wikipedia.org",
        "linkedin" to "https://www.linkedin.com",
        "amazon" to "https://www.amazon.com",
        "facebook" to "https://www.facebook.com",
        "instagram" to "https://www.instagram.com",
        "chatgpt" to "https://chatgpt.com",
        "stackoverflow" to "https://stackoverflow.com",
        "medium" to "https://medium.com"
    )

    val KNOWN_APPS = mapOf(
        "whatsapp" to "com.whatsapp",
        "spotify" to "com.spotify.music",
        "chrome" to "com.android.chrome",
        "youtube" to "com.google.android.youtube",
        "play store" to "com.android.vending",
        "google play" to "com.android.vending",
        "google play store" to "com.android.vending",
        "settings" to "com.android.settings",
        "camera" to "camera_intent",
        "assistant" to "assistant_home",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "telegram" to "org.telegram.messenger",
        "instagram" to "com.instagram.android"
    )

    fun parse(rawQuery: String, assistantName: String = "Jarvis"): AssistantAction {
        var query = rawQuery.trim()
        if (query.isEmpty()) return AssistantAction.UnknownAction("")

        val lowerAssistant = assistantName.lowercase(Locale.ROOT)

        // Strip assistant wake prefixes:
        val prefixes = listOf(
            "hello $lowerAssistant",
            "hey $lowerAssistant",
            "hi $lowerAssistant",
            "ok $lowerAssistant",
            lowerAssistant,
            "assistant",
            "please"
        )

        var stripped = query
        for (prefix in prefixes) {
            val lower = stripped.lowercase(Locale.ROOT)
            if (lower.startsWith("$prefix,")) {
                stripped = stripped.substring(prefix.length + 1).trim()
            } else if (lower.startsWith("$prefix ")) {
                stripped = stripped.substring(prefix.length + 1).trim()
            }
        }

        val q = stripped.lowercase(Locale.ROOT).trim()

        // 1. Session Termination ("bye", "goodbye", "end call", "stop session")
        if (isEndSessionCommand(q, lowerAssistant)) {
            return AssistantAction.EndSessionAction
        }

        // 2. Close App ("close the app", "close app", "close assistant")
        if (isCloseAssistantCommand(q, lowerAssistant)) {
            return AssistantAction.CloseAssistantAction
        }

        // 3. Return to Assistant Home
        if (isAssistantHomeCommand(q, lowerAssistant)) {
            return AssistantAction.OpenAssistantHomeAction
        }

        // 4. Android Home Screen Command ("go to home screen", "go home")
        if (isHomeScreenCommand(q)) {
            return AssistantAction.GoHomeAction
        }

        // 5. Open Settings Command
        if (isSettingsCommand(q)) {
            return AssistantAction.SettingsAction
        }

        // 6. Play Store / Install Application Command
        if (isPlayStoreCommand(q)) {
            return parsePlayStoreCommand(stripped)
        }

        // 7. Video Call Command
        if (isVideoCallCommand(q)) {
            return parseVideoCall(stripped)
        }

        // 8. Phone Call Command
        if (isCallCommand(q)) {
            return parsePhoneCall(stripped)
        }

        // 9. Messaging Command
        if (isMessageCommand(q)) {
            return parseMessage(stripped)
        }

        // 10. Media / Music Controls & Context Resolution
        if (isMediaCommand(q)) {
            return parseMedia(stripped)
        }

        // 11. Vision / Camera Command
        if (isVisionCommand(q)) {
            return if (q.contains("screen")) {
                AssistantAction.VisionAction(VisionMode.SCREEN)
            } else {
                AssistantAction.CameraAction(openFloating = true)
            }
        }

        // 12. Accessibility / Screen Navigation
        if (isNavigationCommand(q)) {
            return parseNavigation(stripped)
        }

        // 13. Weather Command
        if (q.contains("weather") || q.contains("temperature") || q.contains("forecast")) {
            return AssistantAction.WeatherAction
        }

        // 14. Website / App Launching
        if (q.startsWith("open ") || q.startsWith("launch ") || q.startsWith("browse ") || q.startsWith("start ")) {
            return parseOpenCommand(stripped)
        }

        // Direct URL check (e.g. user says "https://google.com" or "vercel.com")
        if (isDirectUrl(q)) {
            val url = if (!q.startsWith("http://") && !q.startsWith("https://")) "https://$q" else q
            return AssistantAction.OpenUrlAction(url = url, siteName = q)
        }

        return AssistantAction.UnknownAction(rawQuery)
    }

    private fun isEndSessionCommand(q: String, assistantName: String): Boolean {
        return q == "bye" ||
                q == "goodbye" ||
                q == "end call" ||
                q == "stop session" ||
                q == "bye $assistantName" ||
                q == "goodbye $assistantName" ||
                q == "bye boss" ||
                q == "see you"
    }

    private fun isCloseAssistantCommand(q: String, assistantName: String): Boolean {
        return q == "close the app" ||
                q == "close app" ||
                q == "close assistant" ||
                q == "exit assistant" ||
                q == "close $assistantName" ||
                q == "exit $assistantName" ||
                q == "exit" ||
                q == "quit"
    }

    private fun isAssistantHomeCommand(q: String, assistantName: String): Boolean {
        return q == "go to assistant home" ||
                q == "go to assistant home screen" ||
                q == "open assistant home" ||
                q == "assistant home" ||
                q == "go to $assistantName home" ||
                q == "go to $assistantName home screen" ||
                q == "$assistantName home" ||
                q == "return to assistant" ||
                q == "return to $assistantName"
    }

    private fun isHomeScreenCommand(q: String): Boolean {
        return q == "go to home screen" ||
                q == "go to the home screen" ||
                q == "go home" ||
                q == "home screen" ||
                q == "return to home" ||
                q == "back to home screen"
    }

    private fun isSettingsCommand(q: String): Boolean {
        return q == "open settings" ||
                q == "launch settings" ||
                q == "settings" ||
                q == "configure assistant" ||
                q == "open assistant settings" ||
                q == "assistant settings"
    }

    private fun isPlayStoreCommand(q: String): Boolean {
        return q.startsWith("install ") ||
                q.startsWith("download ") ||
                q.startsWith("get ") && q.contains("play store") ||
                q == "open play store" ||
                q == "launch play store" ||
                q == "open google play" ||
                q == "play store"
    }

    private fun parsePlayStoreCommand(raw: String): AssistantAction {
        var clean = raw
            .replace(Regex("(?i)^(install|download|get|open|launch)"), "")
            .replace(Regex("(?i)(from|on) (the )?(google )?play store"), "")
            .trim()

        if (clean.startsWith("the ", ignoreCase = true)) {
            clean = clean.substring(4).trim()
        }

        if (clean.equals("play store", ignoreCase = true) || clean.equals("google play", ignoreCase = true) || clean.isEmpty()) {
            return AssistantAction.OpenPlayStoreAction(appName = "Play Store", packageName = "com.android.vending")
        }

        val appName = clean.capitalizeWords()
        val pkg = KNOWN_APPS[clean.lowercase(Locale.ROOT)]
        return AssistantAction.OpenPlayStoreAction(appName = appName, packageName = pkg)
    }

    private fun isVideoCallCommand(q: String): Boolean {
        return q.startsWith("video call ") ||
                q.startsWith("start a video call") ||
                q.contains("video call")
    }

    private fun parseVideoCall(raw: String): AssistantAction {
        val lower = raw.lowercase(Locale.ROOT)
        val platform = if (lower.contains("whatsapp")) VideoPlatform.WHATSAPP else VideoPlatform.WHATSAPP

        var target = raw
            .replace(Regex("(?i)^(start a video call with|start video call with|video call)"), "")
            .replace(Regex("(?i)on whatsapp"), "")
            .replace(Regex("(?i)using whatsapp"), "")
            .trim()

        if (target.startsWith("to ", ignoreCase = true)) {
            target = target.substring(3).trim()
        }

        return AssistantAction.VideoCallAction(
            contact = target.capitalizeWords(),
            platform = platform
        )
    }

    private fun isCallCommand(q: String): Boolean {
        return q.startsWith("call ") || q.startsWith("dial ")
    }

    private fun parsePhoneCall(raw: String): AssistantAction {
        var target = raw
            .replace(Regex("(?i)^(call|dial)"), "")
            .trim()

        if (target.startsWith("to ", ignoreCase = true)) {
            target = target.substring(3).trim()
        }

        return AssistantAction.CallContactAction(target = target.capitalizeWords())
    }

    private fun isMessageCommand(q: String): Boolean {
        return q.startsWith("message ") ||
                q.startsWith("send message") ||
                q.startsWith("send a message") ||
                q.startsWith("text ") ||
                q.contains("whatsapp message")
    }

    private fun parseMessage(raw: String): AssistantAction {
        val lower = raw.lowercase(Locale.ROOT)
        val isWhatsApp = lower.contains("whatsapp")
        val platform = if (isWhatsApp) MessagePlatform.WHATSAPP else MessagePlatform.SMS

        var contact = "contact"
        var body = "Hello"

        val toIdx = lower.indexOf("to ")
        val forIdx = lower.indexOf("for ")
        val sayingIdx = lower.indexOf("saying ")
        val thatIdx = lower.indexOf("that ")

        val startIdx = when {
            toIdx != -1 -> toIdx + 3
            forIdx != -1 -> forIdx + 4
            lower.startsWith("message ") -> 8
            lower.startsWith("text ") -> 5
            else -> -1
        }

        val splitIdx = when {
            sayingIdx != -1 && sayingIdx > startIdx -> sayingIdx
            thatIdx != -1 && thatIdx > startIdx -> thatIdx
            else -> -1
        }

        if (startIdx != -1 && splitIdx != -1) {
            contact = raw.substring(startIdx, splitIdx).trim()
            val textStart = if (splitIdx == sayingIdx) splitIdx + 7 else splitIdx + 5
            body = raw.substring(textStart).trim()
        } else if (startIdx != -1) {
            contact = raw.substring(startIdx).trim()
        }

        contact = contact.replace(Regex("(?i)on whatsapp"), "").trim().capitalizeWords()

        return AssistantAction.SendMessageAction(
            contact = contact,
            text = body,
            platform = platform
        )
    }

    private fun isMediaCommand(q: String): Boolean {
        return q == "pause" ||
                q == "pause music" ||
                q == "stop music" ||
                q == "pause playback" ||
                q == "resume" ||
                q == "resume music" ||
                q == "resume playback" ||
                q == "next" ||
                q == "next track" ||
                q == "next song" ||
                q == "skip" ||
                q == "skip track" ||
                q == "previous" ||
                q == "previous song" ||
                q == "previous track" ||
                q.startsWith("play ") ||
                q.contains("on youtube") ||
                q.contains("on spotify") ||
                q.contains("play that") ||
                q.contains("play this") ||
                q.contains("play it")
    }

    private fun parseMedia(raw: String): AssistantAction {
        val lower = raw.lowercase(Locale.ROOT).trim()

        if (lower == "pause" || lower == "pause music" || lower == "pause playback") {
            return AssistantAction.PauseMediaAction
        }
        if (lower == "stop" || lower == "stop music" || lower == "stop playback") {
            return AssistantAction.StopMediaAction
        }
        if (lower == "resume" || lower == "resume music" || lower == "resume playback") {
            return AssistantAction.ResumeMediaAction
        }
        if (lower == "next" || lower == "next track" || lower == "next song" || lower == "skip" || lower == "skip song") {
            return AssistantAction.NextMediaAction
        }
        if (lower == "previous" || lower == "previous song" || lower == "previous track") {
            return AssistantAction.PreviousMediaAction
        }

        // Platform detection
        val isYouTube = lower.contains("on youtube") || lower.contains("in youtube")
        val isSpotify = lower.contains("on spotify") || lower.contains("in spotify")
        val platform = when {
            isYouTube -> MediaPlatform.YOUTUBE
            isSpotify -> MediaPlatform.SPOTIFY
            MediaContextHolder.lastPlatform != MediaPlatform.DEFAULT -> MediaContextHolder.lastPlatform
            else -> MediaPlatform.DEFAULT
        }

        // Extract track query
        var track = raw
            .replace(Regex("(?i)^play"), "")
            .replace(Regex("(?i)on youtube"), "")
            .replace(Regex("(?i)in youtube"), "")
            .replace(Regex("(?i)on spotify"), "")
            .replace(Regex("(?i)in spotify"), "")
            .replace(Regex("(?i)music"), "")
            .replace(Regex("(?i)song"), "")
            .trim()

        val trackLower = track.lowercase(Locale.ROOT)

        // Resolve pronoun context: "that", "this", "it"
        if (trackLower == "that" || trackLower == "this" || trackLower == "it" || trackLower.isEmpty()) {
            track = MediaContextHolder.lastPlayedTrack ?: track
        }

        if (track.isNotEmpty() && trackLower != "that" && trackLower != "this" && trackLower != "it") {
            MediaContextHolder.lastPlayedTrack = track
            MediaContextHolder.lastPlatform = platform
        }

        return AssistantAction.PlayMediaAction(
            query = track.ifEmpty { null },
            platform = platform
        )
    }

    private fun isVisionCommand(q: String): Boolean {
        return q.contains("camera vision") ||
                q.contains("open camera") ||
                q.contains("launch camera") ||
                q.contains("take photo") ||
                q.contains("take a photo") ||
                q.contains("screen vision") ||
                q.contains("look at screen") ||
                q.contains("see screen")
    }

    private fun isNavigationCommand(q: String): Boolean {
        return q.contains("scroll down") ||
                q.contains("scroll up") ||
                q == "go back" ||
                q == "back" ||
                q.startsWith("tap ") ||
                q.startsWith("click ")
    }

    private fun parseNavigation(raw: String): AssistantAction {
        val q = raw.lowercase(Locale.ROOT)
        return when {
            q.contains("scroll down") -> AssistantAction.AccessibilityAction(ScreenNavType.SCROLL_DOWN)
            q.contains("scroll up") -> AssistantAction.AccessibilityAction(ScreenNavType.SCROLL_UP)
            q == "go back" || q == "back" -> AssistantAction.AccessibilityAction(ScreenNavType.BACK)
            q.startsWith("tap ") -> AssistantAction.AccessibilityAction(ScreenNavType.CLICK_TEXT, raw.replace(Regex("(?i)^tap\\s+"), "").trim())
            q.startsWith("click ") -> AssistantAction.AccessibilityAction(ScreenNavType.CLICK_TEXT, raw.replace(Regex("(?i)^click\\s+"), "").trim())
            else -> AssistantAction.AccessibilityAction(ScreenNavType.SCROLL_DOWN)
        }
    }

    private fun parseOpenCommand(raw: String): AssistantAction {
        var content = raw
            .replace(Regex("(?i)^(open|launch|browse|start)"), "")
            .trim()

        val lower = content.lowercase(Locale.ROOT)
        val preferChrome = lower.endsWith("in chrome") || lower.contains("chrome")

        if (preferChrome) {
            content = content.replace(Regex("(?i)in chrome"), "").trim()
        }

        if (content.equals("assistant", ignoreCase = true) || content.equals("assistant home", ignoreCase = true)) {
            return AssistantAction.OpenAssistantHomeAction
        }

        if (content.equals("settings", ignoreCase = true)) {
            return AssistantAction.SettingsAction
        }

        if (content.equals("camera", ignoreCase = true)) {
            return AssistantAction.CameraAction(openFloating = true)
        }

        if (content.equals("play store", ignoreCase = true) || content.equals("google play", ignoreCase = true)) {
            return AssistantAction.OpenPlayStoreAction(appName = "Play Store", packageName = "com.android.vending")
        }

        val targets = extractWebOrAppTargets(content)

        if (targets.size > 1) {
            val siteTargets = targets.map { resolveToSiteTarget(it) }
            return AssistantAction.OpenMultipleUrlsAction(
                destinations = siteTargets,
                preferChrome = preferChrome
            )
        } else if (targets.size == 1) {
            val single = targets.first()
            val singleLower = single.lowercase(Locale.ROOT)

            if (isKnownApp(singleLower) && !isDirectUrl(singleLower) && !isExplicitWebsite(singleLower)) {
                return AssistantAction.OpenAppAction(appName = single.capitalizeWords(), packageName = KNOWN_APPS[singleLower])
            }

            val site = resolveToSiteTarget(single)
            return AssistantAction.OpenUrlAction(
                url = site.url,
                siteName = site.name,
                preferChrome = preferChrome
            )
        }

        return AssistantAction.OpenAppAction(appName = content.capitalizeWords())
    }

    private fun extractWebOrAppTargets(text: String): List<String> {
        val clean = text.replace(Regex("(?i)these websites"), "").trim()

        val rawTokens = clean.split(Regex("(?i)(,\\s*and\\s*|,\\s*|\\s+and\\s+)"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val finalTargets = mutableListOf<String>()
        for (token in rawTokens) {
            val words = token.split(Regex("\\s+")).filter { it.isNotEmpty() }
            var i = 0
            val subList = mutableListOf<String>()
            var foundKnown = false
            while (i < words.size) {
                if (i + 1 < words.size) {
                    val twoWord = "${words[i]} ${words[i + 1]}".lowercase(Locale.ROOT)
                    if (KNOWN_WEBSITES.containsKey(twoWord)) {
                        subList.add("${words[i]} ${words[i + 1]}")
                        foundKnown = true
                        i += 2
                        continue
                    }
                }
                val oneWord = words[i].lowercase(Locale.ROOT)
                if (KNOWN_WEBSITES.containsKey(oneWord) || isDirectUrl(oneWord)) {
                    subList.add(words[i])
                    foundKnown = true
                    i++
                    continue
                }
                subList.add(words[i])
                i++
            }
            if (foundKnown && subList.size > 1) {
                finalTargets.addAll(subList)
            } else {
                finalTargets.add(token)
            }
        }

        return if (finalTargets.isNotEmpty()) finalTargets else rawTokens
    }

    private fun resolveToSiteTarget(targetName: String): SiteTarget {
        val lower = targetName.lowercase(Locale.ROOT).trim()

        if (KNOWN_WEBSITES.containsKey(lower)) {
            return SiteTarget(name = targetName.capitalizeWords(), url = KNOWN_WEBSITES[lower]!!)
        }

        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return SiteTarget(name = targetName, url = lower)
        }

        if (lower.contains(".")) {
            return SiteTarget(name = targetName, url = "https://$lower")
        }

        val domain = lower.replace(Regex("[^a-z0-9]"), "")
        return SiteTarget(name = targetName.capitalizeWords(), url = "https://www.$domain.com")
    }

    private fun isDirectUrl(text: String): Boolean {
        return text.startsWith("http://") ||
                text.startsWith("https://") ||
                text.endsWith(".com") ||
                text.endsWith(".org") ||
                text.endsWith(".net") ||
                text.endsWith(".io") ||
                text.endsWith(".dev") ||
                text.endsWith(".app") ||
                text.endsWith(".in") ||
                text.endsWith(".co")
    }

    private fun isExplicitWebsite(text: String): Boolean {
        return KNOWN_WEBSITES.containsKey(text.lowercase(Locale.ROOT))
    }

    private fun isKnownApp(text: String): Boolean {
        return KNOWN_APPS.containsKey(text.lowercase(Locale.ROOT))
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            if (word.isNotEmpty()) word[0].uppercaseChar() + word.substring(1) else word
        }
    }
}
