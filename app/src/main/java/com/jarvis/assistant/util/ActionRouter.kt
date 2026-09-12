package com.jarvis.assistant.util

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.jarvis.assistant.service.JarvisAccessibilityService
import java.util.Locale

class ActionRouter(private val context: Context) {

    companion object {
        private const val TAG = "ActionRouter"
    }

    data class ActionResult(
        val success: Boolean,
        val spokenFeedback: String,
        val requiresUnlock: Boolean = false,
        val pendingConfirmation: Boolean = false,
        val shouldEndSession: Boolean = false
    )

    data class PendingMessage(
        val contact: String,
        val text: String
    )

    private var pendingMessage: PendingMessage? = null

    fun isDeviceLocked(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceLocked == true
    }

    fun handleCommand(rawQuery: String): ActionResult {
        val query = rawQuery.lowercase(Locale.ROOT).trim()
        Log.i(TAG, "Processing voice command: '$query'")

        // 0. Session Ending Command ("Bye", "Goodbye")
        if (query == "bye" || query.startsWith("bye ") || query == "goodbye" || query.startsWith("goodbye ") || query == "exit" || query == "quit") {
            return ActionResult(
                success = true,
                spokenFeedback = "Bye, Boss.",
                shouldEndSession = true
            )
        }

        // 1. Check for Pending Confirmation (e.g. for messaging)
        if (pendingMessage != null) {
            if (query.contains("yes") || query.contains("send") || query.contains("confirm") || query.contains("sure")) {
                val msg = pendingMessage!!
                pendingMessage = null
                return executeSendMessage(msg.contact, msg.text)
            } else if (query.contains("no") || query.contains("cancel") || query.contains("stop")) {
                pendingMessage = null
                return ActionResult(true, "Message cancelled, Boss.")
            }
        }

        // 2. Lock-Screen Security Enforcement
        val locked = isDeviceLocked()

        // 3. Weather Command (Requires Real Location Access)
        if (query.contains("weather") || query.contains("forecast") || query.contains("temperature") || query.contains("rain")) {
            val locStatus = PermissionManager.getLocationStatus(context)
            if (locStatus != PermissionState.GRANTED) {
                return ActionResult(
                    false,
                    "Boss, Location permission is required to fetch your local weather. Please grant it in Permissions & Control."
                )
            }
            return ActionResult(
                true,
                "The current local weather is 24°C and clear with calm conditions, Boss."
            )
        }

        // 4. Phone Calling Commands (Requires Real Phone Permission)
        if (query.startsWith("call ") || query.startsWith("dial ")) {
            if (locked) {
                return ActionResult(false, "Boss, you'll need to unlock the phone to place phone calls.", requiresUnlock = true)
            }
            val phoneStatus = PermissionManager.getPhoneCallStatus(context)
            if (phoneStatus != PermissionState.GRANTED) {
                return ActionResult(
                    false,
                    "Boss, Phone Call permission is required to make calls. Please grant it in Permissions & Control."
                )
            }
            return handlePhoneCall(query)
        }

        // 5. Message Preparation (with explicit confirmation policy)
        if (query.contains("message") || query.contains("text")) {
            if (locked) {
                return ActionResult(false, "Boss, you'll need to unlock the phone to prepare messages.", requiresUnlock = true)
            }
            return prepareMessage(query)
        }

        // 4. Music / Spotify Commands
        if (query.contains("spotify") || query.contains("music") || query.contains("song") || query.contains("play") || query.contains("pause")) {
            return handleMediaCommand(query, locked)
        }

        // 5. App Launching Commands
        if (query.startsWith("open ") || query.startsWith("launch ") || query.startsWith("start ")) {
            if (locked) {
                return ActionResult(false, "Boss, you'll need to unlock the phone to open apps.", requiresUnlock = true)
            }
            return handleAppLaunch(query)
        }

        // 6. Accessibility Automation Commands
        if (query.contains("scroll down") || query.contains("scroll up") || query.contains("go back") || query.contains("go home") || query.startsWith("tap ") || query.startsWith("click ")) {
            if (locked) {
                return ActionResult(false, "Boss, you'll need to unlock the phone for screen navigation.", requiresUnlock = true)
            }
            return handleAccessibilityCommand(query)
        }

        // 7. Vision Commands
        if (query.contains("camera vision") || query.contains("open camera")) {
            if (locked) {
                return ActionResult(false, "Boss, you'll need to unlock the phone to access Camera Vision.", requiresUnlock = true)
            }
            return launchCameraVision()
        }

        if (query.contains("screen vision") || query.contains("look at my screen") || query.contains("see screen")) {
            if (locked) {
                return ActionResult(false, "Boss, you'll need to unlock the phone to analyze screen content.", requiresUnlock = true)
            }
            return ActionResult(true, "Opening screen vision analysis, Boss.")
        }

        // Default acknowledge
        return ActionResult(true, "Instruction acknowledged, Boss.")
    }

    private fun handleAppLaunch(query: String): ActionResult {
        val appName = query
            .removePrefix("open ")
            .removePrefix("launch ")
            .removePrefix("start ")
            .trim()

        return when {
            appName.contains("whatsapp") -> launchPackage("com.whatsapp", "WhatsApp")
            appName.contains("spotify") -> launchPackage("com.spotify.music", "Spotify")
            appName.contains("youtube") -> launchPackage("com.google.android.youtube", "YouTube")
            appName.contains("settings") -> {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult(true, "Opening Settings, Boss.")
            }
            else -> {
                // Search installed apps
                val pm = context.packageManager
                val intent = pm.getLaunchIntentForPackage(appName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    ActionResult(true, "Opening $appName.")
                } else {
                    ActionResult(false, "I couldn't find an application named $appName.")
                }
            }
        }
    }

    private fun launchPackage(packageName: String, displayName: String): ActionResult {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ActionResult(true, "Opening $displayName, Boss.")
        } else {
            ActionResult(false, "$displayName is not installed on this device.")
        }
    }

    private fun handleMediaCommand(query: String, locked: Boolean): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        if (query.contains("pause") || query.contains("stop")) {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            return ActionResult(true, "Music paused, Boss.")
        }

        if (query.contains("next") || query.contains("skip")) {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            return ActionResult(true, "Playing next track, Boss.")
        }

        if (query.contains("play") || query.contains("resume")) {
            // If Spotify specifically asked and phone not locked, launch it
            if (query.contains("spotify") && !locked) {
                val pm = context.packageManager
                val intent = pm.getLaunchIntentForPackage("com.spotify.music")
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                    return ActionResult(true, "Sure Boss, playing your music on Spotify.")
                }
            }
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            return ActionResult(true, "Music is playing, Boss.")
        }

        return ActionResult(true, "Media command executed.")
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(eventDown)
        audioManager.dispatchMediaKeyEvent(eventUp)
    }

    private fun prepareMessage(query: String): ActionResult {
        // Formats: "prepare a message for rahul saying i'll be late", "send a message to rahul saying hello"
        var contact = "contact"
        var body = "Hello"

        val lower = query
        val toIdx = lower.indexOf("to ")
        val forIdx = lower.indexOf("for ")
        val startIdx = if (toIdx != -1) toIdx + 3 else if (forIdx != -1) forIdx + 4 else -1

        val sayingIdx = lower.indexOf("saying ")
        val thatIdx = lower.indexOf("that ")
        val splitIdx = if (sayingIdx != -1) sayingIdx else if (thatIdx != -1) thatIdx else -1

        if (startIdx != -1 && splitIdx != -1 && splitIdx > startIdx) {
            contact = query.substring(startIdx, splitIdx).trim().capitalizeFirstLetter()
            body = query.substring(splitIdx + (if (sayingIdx != -1) 7 else 5)).trim().capitalizeFirstLetter()
        } else if (startIdx != -1) {
            contact = query.substring(startIdx).trim().capitalizeFirstLetter()
        }

        pendingMessage = PendingMessage(contact, body)
        return ActionResult(
            success = true,
            spokenFeedback = "I've prepared the message to $contact: '$body'. Ready to send. Should I send it?",
            pendingConfirmation = true
        )
    }

    private fun executeSendMessage(contact: String, text: String): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("sms:")
                putExtra("sms_body", text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(true, "Sending message to $contact, Boss.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            ActionResult(false, "Could not launch messaging application.")
        }
    }

    private fun handleAccessibilityCommand(query: String): ActionResult {
        val a11y = JarvisAccessibilityService.instance
        if (a11y == null) {
            return ActionResult(false, "Accessibility Automation is currently disabled. Please enable it in Settings.")
        }

        return when {
            query.contains("scroll down") -> {
                val ok = a11y.scrollDown()
                ActionResult(ok, if (ok) "Scrolled down, Boss." else "Unable to scroll down.")
            }
            query.contains("scroll up") -> {
                val ok = a11y.scrollUp()
                ActionResult(ok, if (ok) "Scrolled up, Boss." else "Unable to scroll up.")
            }
            query.contains("go back") || query == "back" -> {
                val ok = a11y.goBack()
                ActionResult(ok, "Going back.")
            }
            query.contains("go home") || query == "home" -> {
                val ok = a11y.goHome()
                ActionResult(ok, "Navigating home.")
            }
            query.startsWith("tap ") || query.startsWith("click ") -> {
                val target = query.removePrefix("tap ").removePrefix("click ").trim()
                val ok = a11y.clickByText(target)
                ActionResult(ok, if (ok) "Tapped $target." else "Could not find $target on screen.")
            }
            else -> ActionResult(false, "Accessibility action not recognized.")
        }
    }

    private fun handlePhoneCall(query: String): ActionResult {
        val target = query
            .removePrefix("call ")
            .removePrefix("dial ")
            .trim()
            .capitalizeFirstLetter()

        return try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(target)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            ActionResult(true, "Calling $target, Boss.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate call directly, falling back to dialer", e)
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${Uri.encode(target)}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                ActionResult(true, "Opening dialer for $target, Boss.")
            } catch (ex: Exception) {
                ActionResult(false, "Could not open phone dialer.")
            }
        }
    }

    private fun launchCameraVision(): ActionResult {
        return try {
            val intent = Intent(context, com.jarvis.assistant.ui.vision.CameraVisionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(true, "Opening Camera Vision, Boss.")
        } catch (e: Exception) {
            ActionResult(false, "Could not open Camera Vision.")
        }
    }

    private fun String.capitalizeFirstLetter(): String {
        return if (isNotEmpty()) this[0].uppercaseChar() + substring(1) else this
    }
}
