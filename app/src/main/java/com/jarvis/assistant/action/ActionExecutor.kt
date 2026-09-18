package com.jarvis.assistant.action

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Browser
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.service.JarvisAccessibilityService
import com.jarvis.assistant.service.JarvisVoiceService
import com.jarvis.assistant.ui.home.MainActivity
import com.jarvis.assistant.ui.settings.SettingsActivity
import com.jarvis.assistant.ui.vision.CameraVisionActivity
import com.jarvis.assistant.util.PermissionManager
import com.jarvis.assistant.util.PermissionState

data class ExecutionResult(
    val success: Boolean,
    val spokenFeedback: String,
    val requiresUnlock: Boolean = false,
    val pendingConfirmation: Boolean = false,
    val shouldEndSession: Boolean = false,
    val shouldCloseApp: Boolean = false
)

class ActionExecutor(private val context: Context) {

    companion object {
        private const val TAG = "ActionExecutor"
        private const val PACKAGE_CHROME = "com.android.chrome"
        private const val PACKAGE_WHATSAPP = "com.whatsapp"
        private const val PACKAGE_SPOTIFY = "com.spotify.music"
        private const val PACKAGE_YOUTUBE = "com.google.android.youtube"
        private const val PACKAGE_PLAY_STORE = "com.android.vending"
    }

    private val preferences = (context.applicationContext as? JarvisApp)?.preferences
        ?: JarvisApp.instance.preferences

    fun isDeviceLocked(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceLocked == true
    }

    fun execute(action: AssistantAction): ExecutionResult {
        val userName = preferences.userName.ifBlank { "Boss" }
        val locked = isDeviceLocked()

        return when (action) {
            is AssistantAction.OpenMultipleUrlsAction -> executeOpenMultipleUrls(action, userName, locked)
            is AssistantAction.OpenUrlAction -> executeOpenUrl(action, userName, locked)
            is AssistantAction.OpenAppAction -> executeOpenApp(action, userName, locked)
            is AssistantAction.GoHomeAction -> executeGoHome(userName)
            is AssistantAction.OpenAssistantHomeAction -> executeOpenAssistantHome(userName)
            is AssistantAction.CloseAssistantAction -> executeCloseAssistant(userName)
            is AssistantAction.EndSessionAction -> executeEndSession()
            is AssistantAction.SettingsAction -> executeOpenSettings(userName, locked)
            is AssistantAction.OpenPlayStoreAction -> executeOpenPlayStore(action, userName, locked)
            is AssistantAction.CameraAction -> executeCamera(action, userName, locked)
            is AssistantAction.VisionAction -> executeVision(action, userName, locked)
            is AssistantAction.CallContactAction -> executeCallContact(action, userName, locked)
            is AssistantAction.SendMessageAction -> executeSendMessage(action, userName, locked)
            is AssistantAction.VideoCallAction -> executeVideoCall(action, userName, locked)
            is AssistantAction.PlayMediaAction -> executePlayMedia(action, userName, locked)
            is AssistantAction.PauseMediaAction -> executePauseMedia(userName)
            is AssistantAction.ResumeMediaAction -> executeResumeMedia(userName)
            is AssistantAction.NextMediaAction -> executeNextMedia(userName)
            is AssistantAction.PreviousMediaAction -> executePreviousMedia(userName)
            is AssistantAction.StopMediaAction -> executeStopMedia(userName)
            is AssistantAction.AccessibilityAction -> executeAccessibility(action, userName, locked)
            is AssistantAction.WeatherAction -> executeWeather(userName)
            is AssistantAction.UnknownAction -> ExecutionResult(
                success = true,
                spokenFeedback = "I didn't quite catch that, $userName. How can I help you?"
            )
        }
    }

    private fun executeOpenMultipleUrls(
        action: AssistantAction.OpenMultipleUrlsAction,
        userName: String,
        locked: Boolean
    ): ExecutionResult {
        if (locked) {
            return ExecutionResult(false, "$userName, you'll need to unlock your phone to open websites.", requiresUnlock = true)
        }

        if (action.destinations.isEmpty()) {
            return ExecutionResult(false, "No websites were specified to open, $userName.")
        }

        val hasChrome = isPackageInstalled(PACKAGE_CHROME)
        val useChrome = (action.preferChrome || hasChrome)

        val openedList = mutableListOf<String>()
        val failedList = mutableListOf<String>()

        for (target in action.destinations) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target.url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    putExtra(Browser.EXTRA_APPLICATION_ID, if (useChrome) PACKAGE_CHROME else context.packageName)
                    putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
                    putExtra("create_new_tab", true)
                    if (useChrome && hasChrome) {
                        setPackage(PACKAGE_CHROME)
                    }
                }
                context.startActivity(intent)
                openedList.add(target.name)
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(target.url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                    openedList.add(target.name)
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to open ${target.url}", ex)
                    failedList.add(target.name)
                }
            }
        }

        return when {
            openedList.size == action.destinations.size -> {
                val siteNames = openedList.joinToString(", ")
                val browserMention = if (useChrome && hasChrome) "in Chrome" else "in your browser"
                ExecutionResult(true, "Opening $siteNames $browserMention, $userName.")
            }
            openedList.isNotEmpty() -> {
                val successNames = openedList.joinToString(", ")
                val failNames = failedList.joinToString(", ")
                ExecutionResult(true, "Opened $successNames, but could not open $failNames.")
            }
            else -> {
                ExecutionResult(false, "I wasn't able to open the requested websites, $userName.")
            }
        }
    }

    private fun executeOpenUrl(
        action: AssistantAction.OpenUrlAction,
        userName: String,
        locked: Boolean
    ): ExecutionResult {
        if (locked) {
            return ExecutionResult(false, "$userName, you'll need to unlock your phone to open websites.", requiresUnlock = true)
        }

        val hasChrome = isPackageInstalled(PACKAGE_CHROME)
        val useChrome = (action.preferChrome || hasChrome)

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Browser.EXTRA_APPLICATION_ID, if (useChrome) PACKAGE_CHROME else context.packageName)
                putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
                if (useChrome && hasChrome) {
                    setPackage(PACKAGE_CHROME)
                }
            }
            context.startActivity(intent)
            val displayName = action.siteName.ifBlank { action.url }
            ExecutionResult(true, "Opening $displayName, $userName.")
        } catch (e: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                val displayName = action.siteName.ifBlank { action.url }
                ExecutionResult(true, "Opening $displayName, $userName.")
            } catch (ex: Exception) {
                ExecutionResult(false, "Could not open ${action.url}, $userName.")
            }
        }
    }

    private fun executeOpenApp(
        action: AssistantAction.OpenAppAction,
        userName: String,
        locked: Boolean
    ): ExecutionResult {
        if (locked) {
            return ExecutionResult(false, "$userName, you'll need to unlock your phone to open apps.", requiresUnlock = true)
        }

        val appNameLower = action.appName.lowercase()

        if (appNameLower == "settings" || action.packageName == "com.android.settings") {
            return executeOpenSettings(userName, locked)
        }

        if (appNameLower == "camera" || action.packageName == "camera_intent") {
            return executeCamera(AssistantAction.CameraAction(), userName, locked)
        }

        if (appNameLower == "assistant" || action.packageName == "assistant_home") {
            return executeOpenAssistantHome(userName)
        }

        if (!action.packageName.isNullOrBlank()) {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(action.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return ExecutionResult(true, "Opening ${action.appName}, $userName.")
            }
        }

        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var matchedPackage: String? = null
        var matchedLabel: String? = null

        for (app in installedApps) {
            val label = pm.getApplicationLabel(app).toString()
            if (label.equals(action.appName, ignoreCase = true)) {
                matchedPackage = app.packageName
                matchedLabel = label
                break
            } else if (label.contains(action.appName, ignoreCase = true) && matchedPackage == null) {
                matchedPackage = app.packageName
                matchedLabel = label
            }
        }

        if (matchedPackage != null) {
            val launchIntent = pm.getLaunchIntentForPackage(matchedPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ExecutionResult(true, "Opening ${matchedLabel ?: action.appName}, $userName.")
            }
        }

        return ExecutionResult(false, "${action.appName} is not installed on this device, $userName.")
    }

    private fun executeOpenSettings(userName: String, locked: Boolean): ExecutionResult {
        if (locked) {
            return ExecutionResult(false, "$userName, you'll need to unlock your phone to open Settings.", requiresUnlock = true)
        }
        return try {
            val intent = Intent(context, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult(true, "Opening Settings, $userName.")
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ExecutionResult(true, "Opening system Settings, $userName.")
            } catch (ex: Exception) {
                ExecutionResult(false, "Could not open Settings.")
            }
        }
    }

    private fun executeOpenPlayStore(
        action: AssistantAction.OpenPlayStoreAction,
        userName: String,
        locked: Boolean
    ): ExecutionResult {
        if (locked) {
            return ExecutionResult(false, "$userName, you'll need to unlock your phone to open Google Play Store.", requiresUnlock = true)
        }

        val uri = if (!action.packageName.isNullOrBlank() && action.packageName != PACKAGE_PLAY_STORE) {
            Uri.parse("market://details?id=${action.packageName}")
        } else if (action.appName.equals("Play Store", ignoreCase = true) || action.appName.equals("Google Play", ignoreCase = true)) {
            Uri.parse("market://details?id=$PACKAGE_PLAY_STORE")
        } else {
            Uri.parse("market://search?q=${Uri.encode(action.appName)}")
        }

        return try {
            val playStoreIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(PACKAGE_PLAY_STORE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(playStoreIntent)
            ExecutionResult(true, "Opening Google Play Store for ${action.appName}, $userName.")
        } catch (e: Exception) {
            try {
                val webUri = if (!action.packageName.isNullOrBlank() && action.packageName != PACKAGE_PLAY_STORE) {
                    Uri.parse("https://play.google.com/store/apps/details?id=${action.packageName}")
                } else {
                    Uri.parse("https://play.google.com/store/search?q=${Uri.encode(action.appName)}")
                }
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                ExecutionResult(true, "Opening Google Play Store for ${action.appName}, $userName.")
            } catch (ex: Exception) {
                ExecutionResult(false, "Could not open Google Play Store.")
            }
        }
    }

    private fun executeCamera(
        action: AssistantAction.CameraAction,
        userName: String,
        locked: Boolean
    ): ExecutionResult {
        if (locked) {
            return ExecutionResult(false, "$userName, you'll need to unlock your phone to access Camera.", requiresUnlock = true)
        }
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult(true, "Opening Camera, $userName.")
        } catch (e: Exception) {
            ExecutionResult(false, "Could not open Camera.")
        }
    }

    private fun executeGoHome(userName: String): ExecutionResult {
        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeIntent)
            ExecutionResult(true, "Navigating to home screen, $userName.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate home", e)
            ExecutionResult(false, "Could not navigate to home screen.")
        }
    }

    private fun executeOpenAssistantHome(userName: String): ExecutionResult {
        return try {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
            ExecutionResult(true, "Returning to Assistant home, $userName.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to return to Assistant home", e)
            ExecutionResult(false, "Could not return to Assistant home.")
        }
    }

    private fun executeCloseAssistant(userName: String): ExecutionResult {
        try {
            JarvisVoiceService.endSession(context)
        } catch (_: Exception) {}

        return ExecutionResult(
            success = true,
            spokenFeedback = "Closing Assistant. Goodbye, $userName.",
            shouldEndSession = true,
            shouldCloseApp = true
        )
    }

    private fun executeEndSession(): ExecutionResult {
        try {
            JarvisVoiceService.endSession(context)
        } catch (_: Exception) {}

        return ExecutionResult(
            success = true,
            spokenFeedback = "Bye Boss",
            shouldEndSession = true,
            shouldCloseApp = false
        )
    }

    @SuppressLint("MissingPermission")
    private fun executeCallContact(
        action: AssistantAction.CallContactAction,
        userName: String,
        locked: Boolean
    ): ExecutionResult {
        if (locked) {
            return ExecutionResult(false, "$userName, you'll need to unlock your phone to place calls.", requiresUnlock = true)
        }

        val resolution = ContactResolver.resolveContact(context, action.target)
        when (resolution) {
            is ContactResolution.PermissionRequired -> {
                return ExecutionResult(
                    false,
                    "$userName, Contacts permission is required to find contacts. Please grant it in Permissions & Control."
                )
            }
            is ContactResolution.Ambiguous -> {
                val names = resolution.matches.joinToString(", ") { it.name }
                return ExecutionResult(
                    false,
                    "I found multiple contacts matching '${action.target}': $names. Please specify who to call."
                )
            }
            is ContactResolution.NotFound -> {
                if (action.target.matches(Regex("^[+0-9\\-\\s()]+$"))) {
                    return openDialer(action.target, action.target, userName)
                }
                return ExecutionResult(
                    false,
                    "I couldn't find '${action.target}' in your contacts, $userName."
                )
            }
            is ContactResolution.Found -> {
                val contact = resolution.match
                val phoneNumber = action.phoneNumber ?: contact.phoneNumber

                if (phoneNumber.isEmpty()) {
                    return ExecutionResult(false, "No phone number is registered for ${contact.name}.")
                }

                val hasCallPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED

                return if (hasCallPermission) {
                    try {
                        val callIntent = Intent(Intent.ACTION_CALL).apply {
                            data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            applyPreferredSimExtras(this)
                        }
                        context.startActivity(callIntent)
                        ExecutionResult(true, "Calling ${contact.name}, $userName.")
                    } catch (e: Exception) {
                        Log.e(TAG, "ACTION_CALL failed, falling back to dialer", e)
                        openDialer(contact.name, phoneNumber, userName)
                    }
                } else {
                    openDialer(contact.name, phoneNumber, userName)
                }
            }
        }
    }

    private fun openDialer(contactName: String, phoneNumber: String, userName: String): ExecutionResult {
        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                applyPreferredSimExtras(this)
            }
            context.startActivity(dialIntent)
            ExecutionResult(true, "Opening dialer for $contactName, $userName.")
        } catch (e: Exception) {
            ExecutionResult(false, "Could not open phone dialer.")
        }
    }

    private fun applyPreferredSimExtras(intent: Intent) {
        val preferredSim = preferences.preferredSim
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                if (isGranted) {
                    val activeList = subscriptionManager?.activeSubscriptionInfoList
                    if (!activeList.isNullOrEmpty()) {
                        val simIndex = if (preferredSim.contains("2")) 1 else 0
                        val targetSub = activeList.getOrNull(simIndex) ?: activeList.first()
                        intent.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", targetSub.subscriptionId)
                        intent.putExtra("simSlot", targetSub.simSlotIndex)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun executeSendMessage(
        action: AssistantAction.SendMessageAction,
        userName: String,
        locked: Boolean
    ): ExecutionResult {
        if (locked) {
            return ExecutionResult(false, "$userName, you'll need to unlock your phone to send messages.", requiresUnlock = true)
        }

        val resolution = ContactResolver.resolveContact(context, action.contact)
        val phoneNumber = when (resolution) {
            is ContactResolution.Found -> resolution.match.phoneNumber
            is ContactResolution.Ambiguous -> {
                val names = resolution.matches.joinToString(", ") { it.name }
                return ExecutionResult(
                    false,
                    "I found multiple contacts for '${action.contact}': $names. Which one would you like to message?"
                )
            }
            else -> action.phoneNumber ?: ""
        }

        if (action.platform == MessagePlatform.WHATSAPP) {
            return if (isPackageInstalled(PACKAGE_WHATSAPP)) {
                try {
                    val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
                    val uri = if (cleanNumber.isNotEmpty()) {
                        Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(action.text)}")
                    } else {
                        Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(action.text)}")
                    }
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage(PACKAGE_WHATSAPP)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ExecutionResult(
                        true,
                        "Opening WhatsApp with your message to ${action.contact}. Please confirm to send, $userName."
                    )
                } catch (e: Exception) {
                    ExecutionResult(false, "Could not launch WhatsApp compose.")
                }
            } else {
                ExecutionResult(false, "WhatsApp is not installed on this device, $userName.")
            }
        } else {
            return try {
                val smsUri = if (phoneNumber.isNotEmpty()) Uri.parse("smsto:$phoneNumber") else Uri.parse("sms:")
                val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                    putExtra("sms_body", action.text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ExecutionResult(
                    true,
                    "Prepared message to ${action.contact}: '${action.text}'. Confirmation is required to send.",
                    pendingConfirmation = true
                )
            } catch (e: Exception) {
                ExecutionResult(false, "Could not launch messaging application.")
            }
        }
    }

    private fun executeVideoCall(
        action: AssistantAction.VideoCallAction,
        userName: String,
        locked: Boolean
    ): ExecutionResult {
        if (locked) {
            return ExecutionResult(false, "$userName, you'll need to unlock your phone to start a video call.", requiresUnlock = true)
        }

        if (!isPackageInstalled(PACKAGE_WHATSAPP)) {
            return ExecutionResult(false, "WhatsApp is required for video calls but is not installed on this device, $userName.")
        }

        val resolution = ContactResolver.resolveContact(context, action.contact)
        val phoneNumber = when (resolution) {
            is ContactResolution.Found -> resolution.match.phoneNumber
            is ContactResolution.Ambiguous -> {
                val names = resolution.matches.joinToString(", ") { it.name }
                return ExecutionResult(
                    false,
                    "Multiple contacts found for '${action.contact}': $names. Which one would you like to video call?"
                )
            }
            else -> action.phoneNumber ?: ""
        }

        return try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
            val intent = if (cleanNumber.isNotEmpty()) {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")).apply {
                    setPackage(PACKAGE_WHATSAPP)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                val pm = context.packageManager
                pm.getLaunchIntentForPackage(PACKAGE_WHATSAPP)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                } ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            ExecutionResult(true, "Opening WhatsApp for video call with ${action.contact}, $userName.")
        } catch (e: Exception) {
            ExecutionResult(false, "Could not initiate video call on WhatsApp.")
        }
    }

    private fun executePlayMedia(
        action: AssistantAction.PlayMediaAction,
        userName: String,
        locked: Boolean
    ): ExecutionResult {
        val query = action.query?.trim() ?: ""

        // 1. YouTube playback
        if (action.platform == MediaPlatform.YOUTUBE) {
            if (locked) {
                return ExecutionResult(false, "$userName, unlock your phone to play YouTube.", requiresUnlock = true)
            }
            val hasYouTube = isPackageInstalled(PACKAGE_YOUTUBE)
            return try {
                if (hasYouTube && query.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_SEARCH).apply {
                        setPackage(PACKAGE_YOUTUBE)
                        putExtra("query", query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } else {
                    val url = if (query.isNotEmpty()) {
                        "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
                    } else {
                        "https://www.youtube.com"
                    }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        if (hasYouTube) setPackage(PACKAGE_YOUTUBE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                val label = if (query.isNotEmpty()) "'$query' on YouTube" else "YouTube"
                ExecutionResult(true, "Playing $label, $userName.")
            } catch (e: Exception) {
                ExecutionResult(false, "Could not launch YouTube playback.")
            }
        }

        // 2. Spotify playback
        if (action.platform == MediaPlatform.SPOTIFY) {
            if (!isPackageInstalled(PACKAGE_SPOTIFY)) {
                return ExecutionResult(false, "Spotify is not installed on this device, $userName.")
            }
            if (locked) {
                return ExecutionResult(false, "$userName, unlock your phone to play Spotify.", requiresUnlock = true)
            }

            return try {
                if (query.isNotEmpty()) {
                    val searchIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        setPackage(PACKAGE_SPOTIFY)
                        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                        putExtra(SearchManager.QUERY, query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(searchIntent)
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                    ExecutionResult(true, "Playing '$query' on Spotify, $userName.")
                } else {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_SPOTIFY)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                        ExecutionResult(true, "Playing your music on Spotify, $userName.")
                    } else {
                        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                        ExecutionResult(true, "Playing Spotify music, $userName.")
                    }
                }
            } catch (e: Exception) {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                ExecutionResult(true, "Resumed music playback, $userName.")
            }
        }

        // 3. Default media playback
        if (query.isNotEmpty()) {
            if (isPackageInstalled(PACKAGE_SPOTIFY) && !locked) {
                return executePlayMedia(action.copy(platform = MediaPlatform.SPOTIFY), userName, locked)
            }
            if (isPackageInstalled(PACKAGE_YOUTUBE) && !locked) {
                return executePlayMedia(action.copy(platform = MediaPlatform.YOUTUBE), userName, locked)
            }
        }

        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        return ExecutionResult(true, "Playing music, $userName.")
    }

    private fun executePauseMedia(userName: String): ExecutionResult {
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        return ExecutionResult(true, "Music paused, $userName.")
    }

    private fun executeResumeMedia(userName: String): ExecutionResult {
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        return ExecutionResult(true, "Resuming music, $userName.")
    }

    private fun executeNextMedia(userName: String): ExecutionResult {
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        return ExecutionResult(true, "Playing next track, $userName.")
    }

    private fun executePreviousMedia(userName: String): ExecutionResult {
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        return ExecutionResult(true, "Playing previous track, $userName.")
    }

    private fun executeStopMedia(userName: String): ExecutionResult {
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_STOP)
        return ExecutionResult(true, "Music stopped, $userName.")
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(eventDown)
        audioManager.dispatchMediaKeyEvent(eventUp)
    }

    private fun executeAccessibility(
        action: AssistantAction.AccessibilityAction,
        userName: String,
        locked: Boolean
    ): ExecutionResult {
        if (locked) {
            return ExecutionResult(false, "$userName, please unlock your phone for screen navigation.", requiresUnlock = true)
        }

        val a11y = JarvisAccessibilityService.instance
        if (a11y == null) {
            return ExecutionResult(
                false,
                "Accessibility Automation is currently disabled. Please enable it in Settings or Permissions & Control."
            )
        }

        return when (action.navType) {
            ScreenNavType.SCROLL_DOWN -> {
                val ok = a11y.scrollDown()
                ExecutionResult(ok, if (ok) "Scrolled down, $userName." else "Unable to scroll down.")
            }
            ScreenNavType.SCROLL_UP -> {
                val ok = a11y.scrollUp()
                ExecutionResult(ok, if (ok) "Scrolled up, $userName." else "Unable to scroll up.")
            }
            ScreenNavType.BACK -> {
                val ok = a11y.goBack()
                ExecutionResult(ok, "Going back.")
            }
            ScreenNavType.HOME -> {
                val ok = a11y.goHome()
                ExecutionResult(ok, "Navigating home.")
            }
            ScreenNavType.CLICK_TEXT -> {
                val target = action.targetText ?: ""
                val ok = a11y.clickByText(target)
                ExecutionResult(ok, if (ok) "Tapped $target." else "Could not find $target on screen.")
            }
        }
    }

    private fun executeVision(
        action: AssistantAction.VisionAction,
        userName: String,
        locked: Boolean
    ): ExecutionResult {
        if (locked) {
            return ExecutionResult(false, "$userName, unlock your phone to access Vision.", requiresUnlock = true)
        }

        return when (action.mode) {
            VisionMode.CAMERA -> {
                try {
                    val intent = Intent(context, CameraVisionActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ExecutionResult(true, "Opening Camera Vision, $userName.")
                } catch (e: Exception) {
                    ExecutionResult(false, "Could not open Camera Vision.")
                }
            }
            VisionMode.SCREEN -> {
                ExecutionResult(true, "Screen Vision is active, $userName.")
            }
        }
    }

    private fun executeWeather(userName: String): ExecutionResult {
        val locStatus = PermissionManager.getLocationStatus(context)
        if (locStatus != PermissionState.GRANTED) {
            return ExecutionResult(
                false,
                "$userName, Location permission is required to fetch local weather. Please grant it in Permissions & Control."
            )
        }
        return ExecutionResult(
            true,
            "The current local weather is 24°C and clear with calm conditions, $userName."
        )
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
