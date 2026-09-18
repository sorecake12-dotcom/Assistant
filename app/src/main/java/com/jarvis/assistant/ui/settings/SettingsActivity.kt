package com.jarvis.assistant.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.databinding.ActivitySettingsBinding
import com.jarvis.assistant.personality.PersonalityManager
import com.jarvis.assistant.personality.PersonalityMode
import com.jarvis.assistant.service.JarvisVoiceService
import com.jarvis.assistant.ui.permissions.PermissionsActivity
import com.jarvis.assistant.util.PermissionManager
import com.jarvis.assistant.util.PermissionState
import android.content.res.ColorStateList
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.update.AppUpdateManager
import com.jarvis.assistant.update.UpdateInfo
import com.jarvis.assistant.update.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class JarvisDraftConfig(
    var apiKey: String = "",
    var userName: String = "Boss",
    var assistantName: String = "Jarvis",
    var voice: String = "Aoede",
    var personality: String = GeminiConstants.PERSONALITY_AI_ASSISTANT,
    var preferredSim: String = "SIM 1",
    var theme: String = "ARC_BLUE",
    var isBackgroundVoiceEnabled: Boolean = false,
    var wakeWord: String = "Hello Jarvis"
)

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val preferences by lazy { JarvisApp.instance.preferences }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private lateinit var savedConfig: JarvisDraftConfig
    private lateinit var draftConfig: JarvisDraftConfig

    private var currentUpdateInfo: UpdateInfo? = null
    private var downloadedApkFile: File? = null
    private var isDownloading: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadInitialConfiguration()
        setupToolbar()
        setupApiKeyActions()
        setupAssistantNameInput()
        setupVoiceChips()
        setupPersonalitySegment()
        setupThemeOptions()
        setupSimSelector()
        setupAppUpdateSection()
        applyConfigToUi(draftConfig)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        setupSimSelector()
        checkPendingInstallPermission()
    }

    private fun loadInitialConfiguration() {
        savedConfig = JarvisDraftConfig(
            apiKey = preferences.apiKey,
            userName = preferences.userName,
            assistantName = preferences.assistantName,
            voice = preferences.voice,
            personality = preferences.personality,
            preferredSim = preferences.preferredSim,
            theme = preferences.theme,
            isBackgroundVoiceEnabled = preferences.isBackgroundVoiceEnabled,
            wakeWord = preferences.wakeWord
        )
        draftConfig = savedConfig.copy()
    }

    private fun setupApiKeyActions() {
        binding.etApiKey.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (binding.tvApiKeyError.visibility == View.VISIBLE) {
                    binding.tvApiKeyError.visibility = View.GONE
                }
            }
        })
    }

    private fun setupAssistantNameInput() {
        binding.etAssistantName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val raw = s?.toString()?.trim() ?: ""
                val name = if (raw.isNotEmpty()) raw else "Jarvis"
                draftConfig.assistantName = name
                draftConfig.wakeWord = "Hello $name"
                binding.tvWakePhrasePreview.text = "Wake phrase: \"Hello $name\""
                binding.tvSettingsTitle.text = "${name.uppercase()} CONFIG"
            }
        })
    }

    private fun hasUnsavedChanges(): Boolean {
        val currentApiKey = binding.etApiKey.text?.toString()?.trim() ?: ""
        val currentUserName = binding.etUserName.text?.toString()?.trim() ?: "Boss"
        val currentAssistantName = binding.etAssistantName.text?.toString()?.trim()?.ifEmpty { "Jarvis" } ?: "Jarvis"

        return currentApiKey != savedConfig.apiKey ||
                currentUserName != savedConfig.userName ||
                currentAssistantName != savedConfig.assistantName ||
                draftConfig.voice != savedConfig.voice ||
                draftConfig.personality != savedConfig.personality ||
                draftConfig.preferredSim != savedConfig.preferredSim ||
                draftConfig.theme != savedConfig.theme
    }

    private fun handleBackPress() {
        if (hasUnsavedChanges()) {
            showUnsavedChangesDialog()
        } else {
            finish()
        }
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved configuration changes. Do you want to discard them?")
            .setPositiveButton("DISCARD") { _, _ ->
                // Revert previews to saved config and finish
                applyConfigToUi(savedConfig)
                finish()
            }
            .setNegativeButton("KEEP EDITING", null)
            .show()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            handleBackPress()
        }

        binding.btnReset.setOnClickListener {
            draftConfig = savedConfig.copy()
            applyConfigToUi(draftConfig)
            Toast.makeText(this, "Configuration reset to saved values", Toast.LENGTH_SHORT).show()
        }

        binding.btnSystemAccess.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }

        binding.btnCheckUpdates.setOnClickListener {
            Toast.makeText(this, "JARVIS is up to date", Toast.LENGTH_SHORT).show()
        }

        // SAVE & APPLY with press scale animation
        binding.btnSaveApply.setOnClickListener {
            it.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(80)
                .withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    saveAndApplyConfig()
                }
                .start()
        }
    }

    private fun applyConfigToUi(config: JarvisDraftConfig) {
        binding.tvSettingsTitle.text = "${config.assistantName.uppercase()} CONFIG"
        binding.etApiKey.setText(config.apiKey)
        binding.etUserName.setText(config.userName)
        binding.etAssistantName.setText(config.assistantName)
        binding.tvWakePhrasePreview.text = "Wake phrase: \"Hello ${config.assistantName}\""
        setupSimSelector()
        updateThemeUi(animate = false)
        updatePersonalityUi(animate = false)
        setupVoiceChips()
    }

    private fun detectActiveSimCards(): List<String> {
        val simList = mutableListOf<String>()
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val subs = sm?.activeSubscriptionInfoList
                if (!subs.isNullOrEmpty()) {
                    subs.forEachIndexed { index, sub ->
                        val carrier = sub.displayName?.toString()?.trim()
                        val label = if (!carrier.isNullOrEmpty() && carrier != "Android" && !carrier.contains("Sub", true)) {
                            "SIM ${index + 1} ($carrier)"
                        } else {
                            "SIM ${index + 1}"
                        }
                        simList.add(label)
                    }
                }
            }
        } catch (_: Exception) {}

        if (simList.isEmpty()) {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val phoneCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tm?.activeModemCount ?: 1
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                tm?.phoneCount ?: 1
            } else 1

            if (phoneCount <= 1) {
                simList.add("SIM 1")
            } else {
                simList.add("SIM 1")
                simList.add("SIM 2")
            }
        }
        return simList
    }

    private fun setupSimSelector() {
        val activeSims = detectActiveSimCards()

        if (activeSims.size == 1) {
            // If phone has 1 SIM, only show 1 SIM!
            binding.btnSim1.visibility = View.VISIBLE
            binding.btnSim1.text = activeSims[0]
            binding.btnSim2.visibility = View.GONE
            draftConfig.preferredSim = "SIM 1"
        } else {
            // If phone has 2 SIMs, show both!
            binding.btnSim1.visibility = View.VISIBLE
            binding.btnSim1.text = activeSims.getOrNull(0) ?: "SIM 1"
            binding.btnSim2.visibility = View.VISIBLE
            binding.btnSim2.text = activeSims.getOrNull(1) ?: "SIM 2"
        }

        binding.btnSim1.setOnClickListener {
            draftConfig.preferredSim = "SIM 1"
            updateSimUi()
        }
        binding.btnSim2.setOnClickListener {
            draftConfig.preferredSim = "SIM 2"
            updateSimUi()
        }
        updateSimUi()
    }

    private fun updateSimUi() {
        val isSim1 = draftConfig.preferredSim == "SIM 1"
        val activeBg = when (draftConfig.theme) {
            "RED" -> R.drawable.bg_chip_red_active
            "AMBER_GOLD" -> R.drawable.bg_chip_gold_active
            else -> R.drawable.bg_chip_active
        }
        val inactiveBg = R.drawable.bg_chip_inactive

        binding.btnSim1.setBackgroundResource(if (isSim1) activeBg else inactiveBg)
        binding.btnSim1.setTextColor(
            ContextCompat.getColor(
                this,
                if (isSim1) R.color.bg_space else R.color.text_secondary
            )
        )

        binding.btnSim2.setBackgroundResource(if (!isSim1) activeBg else inactiveBg)
        binding.btnSim2.setTextColor(
            ContextCompat.getColor(
                this,
                if (!isSim1) R.color.bg_space else R.color.text_secondary
            )
        )
    }

    private fun setupVoiceChips() {
        binding.boxVoiceChips.removeAllViews()
        val allVoices = GeminiConstants.SUPPORTED_VOICES

        allVoices.forEach { voiceName ->
            val isSelected = voiceName.equals(draftConfig.voice, ignoreCase = true)
            val chip = createVoiceChip(voiceName, isSelected) {
                draftConfig.voice = voiceName
                setupVoiceChips()
            }
            binding.boxVoiceChips.addView(chip)
        }
    }

    private fun createVoiceChip(text: String, isSelected: Boolean, onClick: () -> Unit): View {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 12f
            minHeight = (42 * resources.displayMetrics.density).toInt()
            setPadding(36, 0, 36, 0)
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(
                if (isSelected) {
                    when (draftConfig.theme) {
                        "RED" -> R.drawable.bg_chip_red_active
                        "AMBER_GOLD" -> R.drawable.bg_chip_gold_active
                        else -> R.drawable.bg_chip_active
                    }
                } else {
                    R.drawable.bg_chip_inactive
                }
            )
            setTextColor(
                if (isSelected) ContextCompat.getColor(context, R.color.bg_space)
                else ContextCompat.getColor(context, R.color.text_secondary)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 14, 0)
        }
        tv.layoutParams = lp
        return tv
    }

    private fun setupPersonalitySegment() {
        binding.containerPersonalitySegment.post {
            val segmentWidth = binding.containerPersonalitySegment.width / 3
            val lp = binding.viewPersonalityIndicator.layoutParams
            lp.width = segmentWidth - 6 // margin offset
            binding.viewPersonalityIndicator.layoutParams = lp
            updatePersonalityIndicatorPosition(animate = false)
        }

        binding.btnPersonalityAssistant.setOnClickListener {
            if (draftConfig.personality != GeminiConstants.PERSONALITY_AI_ASSISTANT) {
                draftConfig.personality = GeminiConstants.PERSONALITY_AI_ASSISTANT
                updatePersonalityUi(animate = true)
            }
        }

        binding.btnPersonalityGf.setOnClickListener {
            if (draftConfig.personality != GeminiConstants.PERSONALITY_GF) {
                draftConfig.personality = GeminiConstants.PERSONALITY_GF
                updatePersonalityUi(animate = true)
            }
        }

        binding.btnPersonalityProfessional.setOnClickListener {
            if (draftConfig.personality != GeminiConstants.PERSONALITY_PROFESSIONAL) {
                draftConfig.personality = GeminiConstants.PERSONALITY_PROFESSIONAL
                updatePersonalityUi(animate = true)
            }
        }
    }

    private fun updatePersonalityIndicatorPosition(animate: Boolean) {
        val segmentWidth = (binding.containerPersonalitySegment.width / 3).toFloat()
        val targetX = when (PersonalityMode.fromId(draftConfig.personality)) {
            PersonalityMode.AI_ASSISTANT -> 0f
            PersonalityMode.GF -> segmentWidth
            PersonalityMode.PROFESSIONAL -> segmentWidth * 2f
        }

        if (animate) {
            binding.viewPersonalityIndicator.animate()
                .translationX(targetX)
                .setDuration(260)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            binding.viewPersonalityIndicator.translationX = targetX
        }
    }

    private fun updatePersonalityUi(animate: Boolean = false) {
        updatePersonalityIndicatorPosition(animate)
        val isGold = draftConfig.theme == "AMBER_GOLD"
        val isRed = draftConfig.theme == "RED"

        // Indicator styling
        binding.viewPersonalityIndicator.setBackgroundResource(
            when {
                isRed -> R.drawable.bg_segment_indicator_red
                isGold -> R.drawable.bg_segment_indicator_gold
                else -> R.drawable.bg_segment_indicator
            }
        )

        val activeColor = ContextCompat.getColor(this, R.color.bg_space)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_secondary)

        val mode = PersonalityMode.fromId(draftConfig.personality)

        // Tab 1: AI Assistant
        val isAssistant = mode == PersonalityMode.AI_ASSISTANT
        binding.tvPersonalityAssistantText.setTextColor(if (isAssistant) activeColor else inactiveColor)
        binding.ivPersonalityAssistantIcon.setColorFilter(if (isAssistant) activeColor else inactiveColor)

        // Tab 2: GF
        val isGf = mode == PersonalityMode.GF
        binding.tvPersonalityGfText.setTextColor(if (isGf) activeColor else inactiveColor)
        binding.ivPersonalityGfIcon.setColorFilter(if (isGf) activeColor else inactiveColor)

        // Tab 3: Professional
        val isProf = mode == PersonalityMode.PROFESSIONAL
        binding.tvPersonalityProfessionalText.setTextColor(if (isProf) activeColor else inactiveColor)
        binding.ivPersonalityProfessionalIcon.setColorFilter(if (isProf) activeColor else inactiveColor)

        // Description
        val targetDesc = PersonalityManager.getDescription(mode)
        if (animate) {
            binding.tvPersonalityDescription.animate()
                .alpha(0f)
                .setDuration(100)
                .withEndAction {
                    binding.tvPersonalityDescription.text = targetDesc
                    binding.tvPersonalityDescription.animate().alpha(1f).setDuration(160).start()
                }
                .start()
        } else {
            binding.tvPersonalityDescription.text = targetDesc
        }
    }

    private fun setupThemeOptions() {
        binding.containerThemeSegment.post {
            val thirdWidth = binding.containerThemeSegment.width / 3
            val lp = binding.viewThemeIndicator.layoutParams
            lp.width = thirdWidth - 6 // margin offset
            binding.viewThemeIndicator.layoutParams = lp
            updateThemeIndicatorPosition(animate = false)
        }

        binding.btnThemeArcBlue.setOnClickListener {
            if (draftConfig.theme != "ARC_BLUE") {
                draftConfig.theme = "ARC_BLUE"
                updateThemeUi(animate = true)
            }
        }

        binding.btnThemeAmberGold.setOnClickListener {
            if (draftConfig.theme != "AMBER_GOLD") {
                draftConfig.theme = "AMBER_GOLD"
                updateThemeUi(animate = true)
            }
        }

        binding.btnThemeRed.setOnClickListener {
            if (draftConfig.theme != "RED") {
                draftConfig.theme = "RED"
                updateThemeUi(animate = true)
            }
        }
    }

    private fun updateThemeIndicatorPosition(animate: Boolean) {
        val thirdWidth = (binding.containerThemeSegment.width / 3).toFloat()
        val targetX = when (draftConfig.theme) {
            "AMBER_GOLD" -> thirdWidth
            "RED" -> thirdWidth * 2f
            else -> 0f
        }

        if (animate) {
            binding.viewThemeIndicator.animate()
                .translationX(targetX)
                .setDuration(260)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            binding.viewThemeIndicator.translationX = targetX
        }
    }

    private fun updateThemeUi(animate: Boolean = false) {
        val isGold = draftConfig.theme == "AMBER_GOLD"
        val isRed = draftConfig.theme == "RED"
        updateThemeIndicatorPosition(animate)

        val activeColor = ContextCompat.getColor(this, R.color.bg_space)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_secondary)

        when {
            isRed -> {
                binding.viewThemeIndicator.setBackgroundResource(R.drawable.bg_segment_indicator_red)
                binding.btnSaveApply.setBackgroundResource(R.drawable.bg_btn_gradient_red)
                binding.btnDownloadUpdate.setBackgroundResource(R.drawable.bg_btn_gradient_red)
                binding.btnInstallUpdate.setBackgroundResource(R.drawable.bg_btn_gradient_red)

                binding.tvRedText.setTextColor(activeColor)
                binding.ivRedIcon.setColorFilter(activeColor)

                binding.tvArcBlueText.setTextColor(inactiveColor)
                binding.ivArcBlueIcon.setColorFilter(inactiveColor)

                binding.tvAmberGoldText.setTextColor(inactiveColor)
                binding.ivAmberGoldIcon.setColorFilter(inactiveColor)

                binding.tvThemeDescription.text = "Red Theme (Electric Red & Deep Space)"
            }
            isGold -> {
                binding.viewThemeIndicator.setBackgroundResource(R.drawable.bg_segment_indicator_gold)
                binding.btnSaveApply.setBackgroundResource(R.drawable.bg_btn_gradient_gold)
                binding.btnDownloadUpdate.setBackgroundResource(R.drawable.bg_btn_gradient_gold)
                binding.btnInstallUpdate.setBackgroundResource(R.drawable.bg_btn_gradient_gold)

                binding.tvAmberGoldText.setTextColor(activeColor)
                binding.ivAmberGoldIcon.setColorFilter(activeColor)

                binding.tvArcBlueText.setTextColor(inactiveColor)
                binding.ivArcBlueIcon.setColorFilter(inactiveColor)

                binding.tvRedText.setTextColor(inactiveColor)
                binding.ivRedIcon.setColorFilter(inactiveColor)

                binding.tvThemeDescription.text = "Amber Gold Theme (Warm Golden Holographic Obsidian)"
            }
            else -> {
                binding.viewThemeIndicator.setBackgroundResource(R.drawable.bg_segment_indicator)
                binding.btnSaveApply.setBackgroundResource(R.drawable.bg_btn_gradient)
                binding.btnDownloadUpdate.setBackgroundResource(R.drawable.bg_btn_gradient)
                binding.btnInstallUpdate.setBackgroundResource(R.drawable.bg_btn_gradient)

                binding.tvArcBlueText.setTextColor(activeColor)
                binding.ivArcBlueIcon.setColorFilter(activeColor)

                binding.tvAmberGoldText.setTextColor(inactiveColor)
                binding.ivAmberGoldIcon.setColorFilter(inactiveColor)

                binding.tvRedText.setTextColor(inactiveColor)
                binding.ivRedIcon.setColorFilter(inactiveColor)

                binding.tvThemeDescription.text = "Arc Blue Theme (Electric Cyan & Deep Space)"
            }
        }

        setupVoiceChips()
        updatePersonalityUi(animate = false)
        updateSimUi()
    }

    private fun saveAndApplyConfig() {
        val apiKey = binding.etApiKey.text?.toString()?.trim() ?: ""
        val userName = binding.etUserName.text?.toString()?.trim() ?: "Boss"
        val assistantName = binding.etAssistantName.text?.toString()?.trim()?.ifEmpty { "Jarvis" } ?: "Jarvis"

        if (apiKey.isNotEmpty()) {
            binding.btnSaveApply.isEnabled = false
            binding.btnSaveApply.text = "VERIFYING KEY..."
            binding.tvApiKeyError.visibility = View.GONE

            lifecycleScope.launch(Dispatchers.IO) {
                var isValid = false
                var errorMsg = ""
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
                    val request = Request.Builder().url(url).get().build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        isValid = true
                    } else {
                        val code = response.code
                        errorMsg = if (code == 400 || code == 403) {
                            "API Key is invalid or unauthorized ($code). Please enter a valid Gemini key."
                        } else {
                            "Gemini API error ($code). Please verify your key."
                        }
                    }
                } catch (e: Exception) {
                    errorMsg = "Connection failed: ${e.localizedMessage ?: "Network error"}"
                }

                withContext(Dispatchers.Main) {
                    binding.btnSaveApply.isEnabled = true
                    binding.btnSaveApply.text = "SAVE & APPLY"

                    if (isValid) {
                        commitAndFinish(apiKey, userName, assistantName)
                    } else {
                        binding.tvApiKeyError.text = errorMsg
                        binding.tvApiKeyError.visibility = View.VISIBLE
                        binding.etApiKey.requestFocus()
                        Toast.makeText(
                            this@SettingsActivity,
                            "API Key is wrong or invalid. Settings could not be applied.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } else {
            commitAndFinish(apiKey, userName, assistantName)
        }
    }

    private fun commitAndFinish(apiKey: String, userName: String, assistantName: String) {
        draftConfig.apiKey = apiKey
        draftConfig.userName = userName
        draftConfig.assistantName = assistantName
        draftConfig.wakeWord = "Hello $assistantName"

        // Commit permanent changes to AppPreferences
        preferences.apiKey = draftConfig.apiKey
        preferences.userName = draftConfig.userName
        preferences.assistantName = draftConfig.assistantName
        preferences.wakeWord = draftConfig.wakeWord
        preferences.voice = draftConfig.voice
        preferences.personality = draftConfig.personality
        preferences.personalityMode = PersonalityMode.fromId(draftConfig.personality)
        preferences.preferredSim = draftConfig.preferredSim
        preferences.theme = draftConfig.theme

        savedConfig = draftConfig.copy()

        setResult(RESULT_OK)
        val msg = if (apiKey.isNotEmpty()) "Configuration Saved & Applied. Gemini AI is active!" else "Configuration Saved & Applied"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        finish()
    }

    // ==========================================
    // IN-APP APK UPDATE SYSTEM
    // ==========================================

    private fun setupAppUpdateSection() {
        val (versionName, versionCode) = AppUpdateManager.getInstalledVersion(this)
        binding.tvCurrentVersionBadge.text = "v$versionName ($versionCode)"

        binding.btnCheckUpdates.setOnClickListener {
            if (!isDownloading) {
                performUpdateCheck()
            } else {
                Toast.makeText(this, "Download in progress...", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDismissUpdate.setOnClickListener {
            binding.cardUpdateDetails.visibility = View.GONE
        }

        binding.btnCancelDownload.setOnClickListener {
            cancelApkDownload()
        }

        binding.btnDownloadUpdate.setOnClickListener {
            performApkDownload()
        }

        binding.btnInstallUpdate.setOnClickListener {
            triggerApkInstall()
        }

        binding.btnAllowInstallPermission.setOnClickListener {
            AppUpdateManager.openInstallPermissionSettings(this)
        }
    }

    private fun performUpdateCheck() {
        binding.btnCheckUpdates.isEnabled = false
        binding.tvUpdateStatusSummary.text = "Checking..."
        binding.ivUpdateSyncIcon.animate().rotationBy(360f).setDuration(800).start()

        lifecycleScope.launch {
            val status = AppUpdateManager.checkForUpdates(this@SettingsActivity)
            binding.btnCheckUpdates.isEnabled = true

            when (status) {
                is UpdateStatus.UpdateAvailable -> {
                    currentUpdateInfo = status.updateInfo
                    binding.tvUpdateStatusSummary.text = "Update Available"
                    binding.tvUpdateStatusSummary.setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.status_green))

                    binding.tvUpdateCardTitle.text = "New Update Available"
                    binding.tvUpdateTagBadge.text = "v${status.updateInfo.versionName}"
                    binding.tvUpdateVersionComparison.text = "Current: ${status.installedVersionName}  •  Latest: ${status.updateInfo.versionName}"
                    binding.tvReleaseNotes.text = status.updateInfo.releaseNotes

                    binding.boxDownloadProgress.visibility = View.GONE
                    binding.btnDownloadUpdate.visibility = View.VISIBLE
                    binding.btnInstallUpdate.visibility = View.GONE

                    checkPendingInstallPermission()

                    binding.cardUpdateDetails.visibility = View.VISIBLE
                    Toast.makeText(
                        this@SettingsActivity,
                        "Assistant v${status.updateInfo.versionName} is available!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is UpdateStatus.UpToDate -> {
                    binding.tvUpdateStatusSummary.text = "Up to date ✓"
                    binding.tvUpdateStatusSummary.setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.soft_blue))
                    binding.cardUpdateDetails.visibility = View.GONE
                    Toast.makeText(
                        this@SettingsActivity,
                        "Assistant is up to date (v${status.installedVersionName})",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is UpdateStatus.Error -> {
                    binding.tvUpdateStatusSummary.text = "Check failed"
                    binding.tvUpdateStatusSummary.setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.status_red))
                    Toast.makeText(
                        this@SettingsActivity,
                        status.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {}
            }
        }
    }

    private fun performApkDownload() {
        val info = currentUpdateInfo ?: return
        isDownloading = true

        binding.boxDownloadProgress.visibility = View.VISIBLE
        binding.btnDownloadUpdate.visibility = View.GONE
        binding.btnInstallUpdate.visibility = View.GONE
        binding.pbDownloadProgress.progress = 0
        binding.tvDownloadProgressText.text = "Connecting to download server..."

        lifecycleScope.launch {
            val result = AppUpdateManager.downloadApk(this@SettingsActivity, info) { percent, downloadedBytes, totalBytes ->
                binding.pbDownloadProgress.progress = percent
                val formattedDownloaded = formatFileSize(downloadedBytes)
                val formattedTotal = if (totalBytes > 0) formatFileSize(totalBytes) else "..."
                binding.tvDownloadProgressText.text = "Downloading update... $percent% ($formattedDownloaded / $formattedTotal)"
            }

            isDownloading = false
            binding.boxDownloadProgress.visibility = View.GONE

            if (result.isSuccess) {
                downloadedApkFile = result.getOrNull()
                binding.btnInstallUpdate.visibility = View.VISIBLE
                binding.btnDownloadUpdate.visibility = View.GONE
                checkPendingInstallPermission()
                Toast.makeText(
                    this@SettingsActivity,
                    "Download complete. Package verified!",
                    Toast.LENGTH_SHORT
                ).show()

                if (AppUpdateManager.canRequestPackageInstalls(this@SettingsActivity)) {
                    triggerApkInstall()
                }
            } else {
                binding.btnDownloadUpdate.visibility = View.VISIBLE
                binding.btnInstallUpdate.visibility = View.GONE
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Unknown download error"
                Toast.makeText(
                    this@SettingsActivity,
                    "Download failed: $errorMsg",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun cancelApkDownload() {
        if (isDownloading) {
            AppUpdateManager.cancelDownload()
            isDownloading = false
            binding.boxDownloadProgress.visibility = View.GONE
            binding.btnDownloadUpdate.visibility = View.VISIBLE
            Toast.makeText(this, "Download cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerApkInstall() {
        val apk = downloadedApkFile
        if (apk == null || !apk.exists()) {
            Toast.makeText(this, "Update package not found. Please download again.", Toast.LENGTH_SHORT).show()
            binding.btnInstallUpdate.visibility = View.GONE
            binding.btnDownloadUpdate.visibility = View.VISIBLE
            return
        }

        if (!AppUpdateManager.canRequestPackageInstalls(this)) {
            binding.tvInstallPermissionNotice.visibility = View.VISIBLE
            binding.btnAllowInstallPermission.visibility = View.VISIBLE
            AlertDialog.Builder(this)
                .setTitle("Install Permission Required")
                .setMessage("Android requires permission to install APK updates downloaded from outside the Google Play Store.\n\nPlease tap 'ALLOW' to grant permission in Settings, then return to install.")
                .setPositiveButton("SETTINGS") { _, _ ->
                    AppUpdateManager.openInstallPermissionSettings(this)
                }
                .setNegativeButton("CANCEL", null)
                .show()
            return
        }

        val launched = AppUpdateManager.launchPackageInstaller(this, apk)
        if (!launched) {
            Toast.makeText(this, "Failed to launch package installer. Check file permissions.", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPendingInstallPermission() {
        val hasPermission = AppUpdateManager.canRequestPackageInstalls(this)
        if (hasPermission) {
            binding.tvInstallPermissionNotice.visibility = View.GONE
            binding.btnAllowInstallPermission.visibility = View.GONE
        } else if (currentUpdateInfo != null || downloadedApkFile != null) {
            binding.tvInstallPermissionNotice.visibility = View.VISIBLE
            binding.btnAllowInstallPermission.visibility = View.VISIBLE
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format("%.1f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }
}
