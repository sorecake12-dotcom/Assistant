package com.jarvis.assistant.ui.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.databinding.ActivityPermissionsBinding
import com.jarvis.assistant.databinding.ItemPermissionRowBinding
import com.jarvis.assistant.service.JarvisBackgroundService
import com.jarvis.assistant.util.PermissionCategory
import com.jarvis.assistant.util.PermissionManager
import com.jarvis.assistant.util.PermissionState
import com.jarvis.assistant.util.PermissionStatus

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding

    private val requestMultiplePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        refreshPermissionsUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        refreshPermissionsUi()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionsUi()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSync.setOnClickListener {
            refreshPermissionsUi()
            Toast.makeText(this, "Permissions synchronized", Toast.LENGTH_SHORT).show()
        }

        binding.btnGiveAllPermissions.setOnClickListener {
            requestAllPermissionsFlow()
        }
    }

    private fun refreshPermissionsUi() {
        val (granted, total) = PermissionManager.getGrantedCount(this)
        binding.tvGrantedRatio.text = "$granted of $total Granted"

        val permissions = PermissionManager.getAllStatuses(this)
        populateCategorizedPermissions(binding.containerPermissions, permissions)
    }

    private fun populateCategorizedPermissions(container: LinearLayout, list: List<PermissionStatus>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        val categorized = list.groupBy { it.category }

        for (category in PermissionCategory.values()) {
            val perms = categorized[category] ?: continue
            if (perms.isEmpty()) continue

            // Category Header
            val headerView = TextView(this).apply {
                text = category.displayName
                setTextColor(ContextCompat.getColor(this@PermissionsActivity, R.color.neon_blue))
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                val topMargin = if (container.childCount == 0) 4 else 24
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dpToPx(topMargin), 0, dpToPx(8))
                }
            }
            container.addView(headerView)

            // Category Card Container
            val cardContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@PermissionsActivity, R.drawable.bg_card_rounded)
                setPadding(dpToPx(16), dpToPx(6), dpToPx(16), dpToPx(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            perms.forEachIndexed { index, perm ->
                val rowBinding = ItemPermissionRowBinding.inflate(inflater, cardContainer, false)
                rowBinding.tvPermTitle.text = perm.title
                rowBinding.tvPermDesc.text = perm.description

                val iconRes = when (perm.permissionKey) {
                    "MIC" -> R.drawable.ic_mic
                    "CAMERA" -> R.drawable.ic_camera
                    "CONTACTS" -> R.drawable.ic_group
                    "PHONE" -> R.drawable.ic_phone
                    "NOTIFS" -> R.drawable.ic_check
                    "LOCATION" -> R.drawable.ic_location
                    "WEATHER" -> R.drawable.ic_sparkle
                    "BATTERY" -> R.drawable.ic_power
                    "A11Y" -> R.drawable.ic_bolt
                    "SCREEN" -> R.drawable.ic_vision
                    "OVERLAY" -> R.drawable.ic_shield
                    "BG_VOICE" -> R.drawable.ic_mic
                    "WAKE_WORD" -> R.drawable.ic_robot
                    else -> R.drawable.ic_shield
                }
                rowBinding.ivPermIcon.setImageResource(iconRes)

                val statusText = when (perm.permissionKey) {
                    "A11Y", "BG_VOICE", "WAKE_WORD" -> if (perm.isGranted) "ENABLED" else "DISABLED"
                    else -> perm.status.label
                }
                rowBinding.tvPermStatus.text = statusText
                val colorRes = when (perm.status) {
                    PermissionState.GRANTED -> R.color.status_green
                    PermissionState.DENIED -> R.color.status_red
                    PermissionState.REQUIRES_SETTINGS -> R.color.gold_amber
                    PermissionState.NOT_AVAILABLE -> R.color.text_muted
                }
                rowBinding.tvPermStatus.setTextColor(ContextCompat.getColor(this, colorRes))

                rowBinding.root.setOnClickListener {
                    handlePermissionRowClick(perm)
                }

                cardContainer.addView(rowBinding.root)

                if (index < perms.size - 1) {
                    val divider = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1
                        )
                        setBackgroundColor(ContextCompat.getColor(this@PermissionsActivity, R.color.border_subtle))
                    }
                    cardContainer.addView(divider)
                }
            }

            container.addView(cardContainer)
        }
    }

    private fun handlePermissionRowClick(perm: PermissionStatus) {
        when (perm.permissionKey) {
            "MIC" -> requestMultiplePermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            "CAMERA" -> requestMultiplePermissions.launch(arrayOf(Manifest.permission.CAMERA))
            "CONTACTS" -> requestMultiplePermissions.launch(arrayOf(Manifest.permission.READ_CONTACTS))
            "PHONE" -> requestMultiplePermissions.launch(arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE
            ))
            "NOTIFS" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestMultiplePermissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                } else {
                    Toast.makeText(this, "Notifications enabled on this Android version", Toast.LENGTH_SHORT).show()
                }
            }
            "LOCATION" -> requestMultiplePermissions.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            "WEATHER" -> {
                if (PermissionManager.getLocationStatus(this) == PermissionState.GRANTED) {
                    Toast.makeText(this, "Weather service active: location granted", Toast.LENGTH_SHORT).show()
                } else {
                    requestMultiplePermissions.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
            }
            "BATTERY" -> {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            "A11Y" -> {
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            "SCREEN" -> {
                Toast.makeText(this, "Screen vision active on demand during screen analysis", Toast.LENGTH_SHORT).show()
            }
            "OVERLAY" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    } catch (e: Exception) {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            }
            "BG_VOICE" -> {
                JarvisBackgroundService.toggle(this)
                refreshPermissionsUi()
            }
            "WAKE_WORD" -> {
                Toast.makeText(this, "Wake word active when Background Voice service is running", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestAllPermissionsFlow() {
        val permsToRequest = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestMultiplePermissions.launch(permsToRequest.toTypedArray())

        // If battery optimization not granted, prompt
        if (PermissionManager.getBatteryOptimizationStatus(this) != PermissionState.GRANTED) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }

        // If accessibility not enabled, open settings
        if (PermissionManager.getAccessibilityStatus(this) != PermissionState.GRANTED) {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {}
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
