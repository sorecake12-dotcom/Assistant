package com.jarvis.assistant.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.databinding.ActivityOnboardingBinding
import com.jarvis.assistant.ui.home.MainActivity
import com.jarvis.assistant.util.PermissionManager
import com.jarvis.assistant.util.PermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val preferences by lazy { JarvisApp.instance.preferences }

    private var currentStep = 0
    private var isTestingApi = false

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val requestMultiplePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        refreshPermissionBadges()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pre-fill existing API key if available
        if (preferences.apiKey.isNotEmpty()) {
            binding.etOnboardingApiKey.setText(preferences.apiKey)
        }

        setupStepNavigation()
        setupPermissionActions()
        setupConnectAiActions()
        updateStepUi(0)
    }

    override fun onResume() {
        super.onResume()
        if (currentStep == 1) {
            refreshPermissionBadges()
        }
    }

    private fun setupStepNavigation() {
        // Step 1: Welcome Continue
        binding.btnWelcomeContinue.setOnClickListener {
            goToStep(1)
        }

        // Step 2: Permissions Continue
        binding.btnPermissionsContinue.setOnClickListener {
            goToStep(2)
        }
    }

    private fun goToStep(stepIndex: Int) {
        currentStep = stepIndex
        binding.flipperOnboarding.displayedChild = stepIndex
        updateStepUi(stepIndex)

        if (stepIndex == 1) {
            refreshPermissionBadges()
        }
    }

    private fun updateStepUi(stepIndex: Int) {
        binding.tvStepIndicator.text = "STEP ${stepIndex + 1} OF 3"

        val activeBg = ContextCompat.getDrawable(this, R.drawable.bg_chip_active)
        val inactiveBg = ContextCompat.getDrawable(this, R.drawable.bg_chip_inactive)

        binding.dotStep1.background = if (stepIndex == 0) activeBg else inactiveBg
        binding.dotStep2.background = if (stepIndex == 1) activeBg else inactiveBg
        binding.dotStep3.background = if (stepIndex == 2) activeBg else inactiveBg

        val density = resources.displayMetrics.density
        binding.dotStep1.layoutParams.width = if (stepIndex == 0) (20 * density).toInt() else (8 * density).toInt()
        binding.dotStep2.layoutParams.width = if (stepIndex == 1) (20 * density).toInt() else (8 * density).toInt()
        binding.dotStep3.layoutParams.width = if (stepIndex == 2) (20 * density).toInt() else (8 * density).toInt()
        binding.dotStep1.requestLayout()
        binding.dotStep2.requestLayout()
        binding.dotStep3.requestLayout()
    }

    private fun setupPermissionActions() {
        binding.rowPermMic.setOnClickListener {
            requestMultiplePermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }

        binding.rowPermCamera.setOnClickListener {
            requestMultiplePermissions.launch(arrayOf(Manifest.permission.CAMERA))
        }

        binding.rowPermPhone.setOnClickListener {
            requestMultiplePermissions.launch(arrayOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE
            ))
        }

        binding.rowPermOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }

        binding.btnGrantAllPermissions.setOnClickListener {
            val list = mutableListOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                list.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            requestMultiplePermissions.launch(list.toTypedArray())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    private fun refreshPermissionBadges() {
        // Mic
        val micGranted = PermissionManager.getMicrophoneStatus(this) == PermissionState.GRANTED
        updateBadge(binding.tvStatusMic, micGranted)

        // Camera
        val camGranted = PermissionManager.getCameraStatus(this) == PermissionState.GRANTED
        updateBadge(binding.tvStatusCamera, camGranted)

        // Phone & Contacts
        val phoneGranted = PermissionManager.getPhoneCallStatus(this) == PermissionState.GRANTED &&
                PermissionManager.getContactsStatus(this) == PermissionState.GRANTED
        updateBadge(binding.tvStatusPhone, phoneGranted)

        // Overlay
        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        updateBadge(binding.tvStatusOverlay, overlayGranted)
    }

    private fun updateBadge(badge: android.widget.TextView, isGranted: Boolean) {
        if (isGranted) {
            badge.text = "● GRANTED"
            badge.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            badge.text = "● REQUIRED"
            badge.setTextColor(ContextCompat.getColor(this, R.color.gold_amber))
        }
    }

    private fun setupConnectAiActions() {
        binding.btnTestConnection.setOnClickListener {
            val key = binding.etOnboardingApiKey.text?.toString()?.trim() ?: ""
            if (key.isEmpty()) {
                binding.tvTestStatus.text = "Please enter an API key first"
                binding.tvTestStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
                return@setOnClickListener
            }
            testGeminiApiKey(key)
        }

        binding.btnSaveAndLaunch.setOnClickListener {
            val key = binding.etOnboardingApiKey.text?.toString()?.trim() ?: ""
            if (key.isEmpty()) {
                Toast.makeText(this, "Please enter your Gemini API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            preferences.apiKey = key
            preferences.isFirstLaunchComplete = true

            Toast.makeText(this, "Welcome to Assistant!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun testGeminiApiKey(key: String) {
        if (isTestingApi) return
        isTestingApi = true

        binding.tvTestStatus.text = "Testing connection..."
        binding.tvTestStatus.setTextColor(ContextCompat.getColor(this, R.color.primary_cyan))
        binding.btnTestConnection.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            var isSuccess = false
            var errorMessage = ""

            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$key"
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    isSuccess = true
                } else {
                    val code = response.code
                    errorMessage = if (code == 400 || code == 403) {
                        "Invalid API key or unauthorized ($code)"
                    } else {
                        "API Error ($code)"
                    }
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Network error"
            }

            withContext(Dispatchers.Main) {
                isTestingApi = false
                binding.btnTestConnection.isEnabled = true

                if (isSuccess) {
                    binding.tvTestStatus.text = "● Connection Successful!"
                    binding.tvTestStatus.setTextColor(ContextCompat.getColor(this@OnboardingActivity, R.color.status_green))
                } else {
                    binding.tvTestStatus.text = "● Failed: $errorMessage"
                    binding.tvTestStatus.setTextColor(ContextCompat.getColor(this@OnboardingActivity, R.color.status_red))
                }
            }
        }
    }

    override fun onBackPressed() {
        if (currentStep > 0) {
            goToStep(currentStep - 1)
        } else {
            super.onBackPressed()
        }
    }
}
