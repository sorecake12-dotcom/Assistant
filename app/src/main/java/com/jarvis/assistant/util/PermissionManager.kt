package com.jarvis.assistant.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.service.JarvisAccessibilityService
import com.jarvis.assistant.service.JarvisVoiceService

enum class PermissionState(val label: String) {
    GRANTED("GRANTED"),
    DENIED("NOT GRANTED"),
    ENABLED("ENABLED"),
    DISABLED("DISABLED"),
    UNRESTRICTED("UNRESTRICTED"),
    OPTIMIZED("OPTIMIZED"),
    ACTIVE("ACTIVE"),
    OFF("OFF"),
    REQUIRES_SETTINGS("REQUIRES SETTINGS"),
    NOT_AVAILABLE("NOT AVAILABLE")
}

enum class PermissionCategory(val title: String) {
    CORE("CORE APP PERMISSIONS"),
    LOCATION("LOCATION & INFORMATION"),
    AUTOMATION("SYSTEM AUTOMATION & ACCESSIBILITY"),
    SCREEN("SCREEN & FLOATING CONTROLS"),
    BACKGROUND("BACKGROUND ASSISTANT")
}

data class PermissionStatus(
    val title: String,
    val description: String,
    val status: PermissionState,
    val permissionKey: String,
    val category: PermissionCategory,
    val isRequiredForCount: Boolean = true
) {
    val isGranted: Boolean get() = status == PermissionState.GRANTED ||
            status == PermissionState.ENABLED ||
            status == PermissionState.UNRESTRICTED ||
            status == PermissionState.ACTIVE
}

object PermissionManager {

    fun getMicrophoneStatus(context: Context): PermissionState {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) PermissionState.GRANTED else PermissionState.DENIED
    }

    fun getCameraStatus(context: Context): PermissionState {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) PermissionState.GRANTED else PermissionState.DENIED
    }

    fun getContactsStatus(context: Context): PermissionState {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) PermissionState.GRANTED else PermissionState.DENIED
    }

    fun getPhoneCallStatus(context: Context): PermissionState {
        val grantedCall = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        val grantedState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        return if (grantedCall && grantedState) PermissionState.GRANTED else PermissionState.DENIED
    }

    fun getNotificationsStatus(context: Context): PermissionState {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) PermissionState.GRANTED else PermissionState.DENIED
        } else {
            PermissionState.GRANTED
        }
    }

    fun getLocationStatus(context: Context): PermissionState {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return if (fine || coarse) PermissionState.GRANTED else PermissionState.DENIED
    }

    fun getBatteryOptimizationStatus(context: Context): PermissionState {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        return if (isIgnoring) PermissionState.UNRESTRICTED else PermissionState.OPTIMIZED
    }

    fun getAccessibilityStatus(context: Context): PermissionState {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
        val enabledServices = am?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
        val isServiceActive = enabledServices?.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName
        } == true || JarvisAccessibilityService.isServiceEnabled()

        return if (isServiceActive) PermissionState.ENABLED else PermissionState.DISABLED
    }

    fun getOverlayStatus(context: Context): PermissionState {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(context)) PermissionState.GRANTED else PermissionState.DENIED
        } else {
            PermissionState.GRANTED
        }
    }

    fun getBackgroundVoiceStatus(context: Context): PermissionState {
        val isServiceRunning = JarvisVoiceService.isRunning
        val isEnabled = JarvisApp.instance.preferences.isBackgroundVoiceEnabled
        val micGranted = getMicrophoneStatus(context) == PermissionState.GRANTED

        return when {
            isServiceRunning || (isEnabled && micGranted) -> PermissionState.ACTIVE
            isEnabled && !micGranted -> PermissionState.REQUIRES_SETTINGS
            else -> PermissionState.OFF
        }
    }

    fun getAllStatuses(context: Context): List<PermissionStatus> {
        val locationState = getLocationStatus(context)

        return listOf(
            // 1. CORE APP PERMISSIONS
            PermissionStatus(
                title = "Microphone Access",
                description = "Required for live voice conversation & streaming",
                status = getMicrophoneStatus(context),
                permissionKey = "MIC",
                category = PermissionCategory.CORE,
                isRequiredForCount = true
            ),
            PermissionStatus(
                title = "Camera Vision",
                description = "Required for real-time visual analysis preview",
                status = getCameraStatus(context),
                permissionKey = "CAMERA",
                category = PermissionCategory.CORE,
                isRequiredForCount = true
            ),
            PermissionStatus(
                title = "Contacts Access",
                description = "Required for finding contacts in message & call commands",
                status = getContactsStatus(context),
                permissionKey = "CONTACTS",
                category = PermissionCategory.CORE,
                isRequiredForCount = true
            ),
            PermissionStatus(
                title = "Phone Calls & SIM",
                description = "Required for voice calling & preferred SIM selection",
                status = getPhoneCallStatus(context),
                permissionKey = "PHONE",
                category = PermissionCategory.CORE,
                isRequiredForCount = true
            ),
            PermissionStatus(
                title = "Notifications",
                description = "Required for background assistant foreground service",
                status = getNotificationsStatus(context),
                permissionKey = "NOTIFS",
                category = PermissionCategory.CORE,
                isRequiredForCount = true
            ),

            // 2. LOCATION & INFORMATION
            PermissionStatus(
                title = "Location Access",
                description = "Required for location-aware weather and regional queries",
                status = locationState,
                permissionKey = "LOCATION",
                category = PermissionCategory.LOCATION,
                isRequiredForCount = true
            ),
            PermissionStatus(
                title = "Weather Service",
                description = "Uses location data to provide real-time weather reports",
                status = if (locationState == PermissionState.GRANTED) PermissionState.ACTIVE else PermissionState.OFF,
                permissionKey = "WEATHER",
                category = PermissionCategory.LOCATION,
                isRequiredForCount = false // Capability tied to location
            ),

            // 3. SYSTEM AUTOMATION & ACCESSIBILITY
            PermissionStatus(
                title = "Accessibility Automation",
                description = "Required for hands-free UI interaction, scrolling, and tapping",
                status = getAccessibilityStatus(context),
                permissionKey = "A11Y",
                category = PermissionCategory.AUTOMATION,
                isRequiredForCount = true
            ),
            PermissionStatus(
                title = "Battery Optimization",
                description = "Unrestricted background execution prevents OS from killing JARVIS",
                status = getBatteryOptimizationStatus(context),
                permissionKey = "BATTERY",
                category = PermissionCategory.AUTOMATION,
                isRequiredForCount = true
            ),

            // 4. SCREEN & FLOATING CONTROLS
            PermissionStatus(
                title = "Screen Vision",
                description = "Real-time on-screen visual analysis via MediaProjection",
                status = PermissionState.ACTIVE,
                permissionKey = "SCREEN",
                category = PermissionCategory.SCREEN,
                isRequiredForCount = false // User consents at runtime per session
            ),
            PermissionStatus(
                title = "Display Over Other Apps",
                description = "Required for floating camera HUD and overlay visualizer",
                status = getOverlayStatus(context),
                permissionKey = "OVERLAY",
                category = PermissionCategory.SCREEN,
                isRequiredForCount = true
            ),

            // 5. BACKGROUND ASSISTANT
            PermissionStatus(
                title = "Background Voice",
                description = "Allows JARVIS to respond to Hello Jarvis while in background",
                status = getBackgroundVoiceStatus(context),
                permissionKey = "BG_VOICE",
                category = PermissionCategory.BACKGROUND,
                isRequiredForCount = false // Optional feature state
            ),
            PermissionStatus(
                title = "Wake Word",
                description = "Continuous on-device hotword listener for 'Hello Jarvis'",
                status = if (getBackgroundVoiceStatus(context) == PermissionState.ACTIVE) PermissionState.ACTIVE else PermissionState.OFF,
                permissionKey = "WAKE_WORD",
                category = PermissionCategory.BACKGROUND,
                isRequiredForCount = false // Hotword state
            )
        )
    }

    fun getGrantedCount(context: Context): Pair<Int, Int> {
        val requiredList = getAllStatuses(context).filter { it.isRequiredForCount }
        val granted = requiredList.count { it.isGranted }
        return Pair(granted, requiredList.size)
    }
}
