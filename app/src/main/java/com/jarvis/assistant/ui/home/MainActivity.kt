package com.jarvis.assistant.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.databinding.ActivityMainBinding
import com.jarvis.assistant.service.JarvisVoiceService
import com.jarvis.assistant.ui.chat.ChatAdapter
import com.jarvis.assistant.ui.onboarding.OnboardingActivity
import com.jarvis.assistant.ui.orb.OrbHelper
import com.jarvis.assistant.ui.settings.SettingsActivity
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var orbHelper: OrbHelper
    private val chatAdapter = ChatAdapter()

    // Camera State
    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCamera: Camera? = null
    private var cameraExecutor: ExecutorService? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var isTorchOn = false
    private var lastAnalysisTimestamp = 0L

    // Draggable bounds
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var viewStartX = 0f
    private var viewStartY = 0f

    // Audio Permission Launcher
    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.toggleSession()
        } else {
            Toast.makeText(
                this,
                "Microphone permission is required for voice conversation.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Camera Permission Launcher
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openFloatingCamera()
        } else {
            Toast.makeText(
                this,
                "Camera permission is required for Camera Vision.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Screen Capture (MediaProjection) Launcher
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startScreenVisionMode()
        } else {
            Toast.makeText(
                this,
                "Screen capture permission was declined.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!JarvisApp.instance.preferences.isFirstLaunchComplete) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupOrb()
        setupDrawerRecyclerView()
        setupListeners()
        setupFloatingCameraDragging()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSettings()
        applyTheme()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
        closeFloatingCamera()
    }

    private fun setupOrb() {
        orbHelper = OrbHelper(binding.webViewOrb)
        orbHelper.setup()
    }

    private fun applyTheme() {
        val theme = JarvisApp.instance.preferences.theme
        val isGold = theme == "AMBER_GOLD"
        val isRed = theme == "RED"
        orbHelper.setTheme(theme)

        val activeColor = ContextCompat.getColor(this, when {
            isRed -> R.color.red_primary
            isGold -> R.color.gold_amber
            else -> R.color.primary_cyan
        })
        val softColor = ContextCompat.getColor(this, when {
            isRed -> R.color.red_neon
            isGold -> R.color.gold_amber
            else -> R.color.soft_blue
        })

        binding.ivPowerIcon.imageTintList = ColorStateList.valueOf(softColor)
        binding.btnExpandChevron.imageTintList = ColorStateList.valueOf(softColor)
        binding.floatingCameraFrame.setBackgroundResource(
            when {
                isRed -> R.drawable.bg_floating_camera_red
                isGold -> R.drawable.bg_floating_camera_gold
                else -> R.drawable.bg_floating_camera
            }
        )
        binding.lblCameraActive.setTextColor(activeColor)
        binding.tvScreenVisionStatus.setTextColor(activeColor)
        binding.ivActionCameraIcon.imageTintList = ColorStateList.valueOf(activeColor)
        binding.ivActionVisionIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, when {
                isRed -> R.color.red_neon
                isGold -> R.color.gold_primary
                else -> R.color.neon_blue
            })
        )
    }

    private fun setupDrawerRecyclerView() {
        binding.rvDrawerChatHistory.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvDrawerChatHistory.adapter = chatAdapter
    }

    private fun setupListeners() {
        // Settings button
        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // Top title pill -> opens history drawer
        binding.boxJarvisHeaderTitle.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // Expand chevron -> toggles Vision & Camera Action Dock Popup
        binding.btnExpandChevron.setOnClickListener {
            val isVisible = binding.boxVisionDockPopup.visibility == View.VISIBLE
            binding.boxVisionDockPopup.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        // Camera Action Button -> Open Draggable Floating Camera Preview
        binding.btnActionCamera.setOnClickListener {
            binding.boxVisionDockPopup.visibility = View.GONE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openFloatingCamera()
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Screen Vision Action Button -> Trigger MediaProjection
        binding.btnActionVision.setOnClickListener {
            binding.boxVisionDockPopup.visibility = View.GONE
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }

        // Screen Vision Stop Button
        binding.btnStopScreenVision.setOnClickListener {
            stopScreenVisionMode()
        }

        // Power session button
        binding.btnPowerSession.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.toggleSession()
            } else {
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        // Mic mute toggle
        binding.btnMicMute.setOnClickListener {
            viewModel.toggleMicMute()
        }

        // Camera Preview Controls
        binding.btnCameraClose.setOnClickListener {
            closeFloatingCamera()
        }

        binding.btnCameraFlip.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            bindCameraUseCases()
        }

        binding.btnCameraTorch.setOnClickListener {
            toggleTorch()
        }

        // Drawer Close button [X]
        binding.btnDrawerClose.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Drawer Clear/Delete button
        binding.btnDrawerDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to clear the conversation history?")
                .setPositiveButton("Clear") { _, _ ->
                    viewModel.chatRepository.clearHistory()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Drawer Send message
        binding.btnDrawerSend.setOnClickListener {
            sendMessageFromInput()
        }

        binding.etDrawerMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessageFromInput()
                true
            } else {
                false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingCameraDragging() {
        binding.cardFloatingCamera.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    viewStartX = view.x
                    viewStartY = view.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - dragStartX
                    val deltaY = event.rawY - dragStartY

                    val parent = binding.mainContentLayout
                    val maxRight = (parent.width - view.width).coerceAtLeast(0)
                    val maxBottom = (parent.height - view.height).coerceAtLeast(0)

                    val targetX = (viewStartX + deltaX).coerceIn(0f, maxRight.toFloat())
                    val targetY = (viewStartY + deltaY).coerceIn(0f, maxBottom.toFloat())

                    view.x = targetX
                    view.y = targetY
                    true
                }
                else -> false
            }
        }
    }

    private fun openFloatingCamera() {
        binding.cardFloatingCamera.visibility = View.VISIBLE
        binding.cardFloatingCamera.alpha = 1f
        viewModel.setVisionMode(ConversationState.VISION_CAMERA)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e("MainActivity", "Camera initialization failed", e)
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreviewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor ?: Executors.newSingleThreadExecutor()) { imageProxy ->
                processCameraFrame(imageProxy)
            }

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            activeCamera = provider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            isTorchOn = false
            updateTorchButtonUi()
        } catch (e: Exception) {
            Log.e("MainActivity", "Use case binding failed", e)
        }
    }

    private fun processCameraFrame(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        // Throttle AI frame analysis: extract 1 frame every 2.5 seconds
        if (now - lastAnalysisTimestamp > 2500) {
            lastAnalysisTimestamp = now
            val jpegBytes = imageProxyToJpeg(imageProxy)
            if (jpegBytes != null && jpegBytes.isNotEmpty()) {
                viewModel.sendImageFrame(jpegBytes)
            }
        }
        imageProxy.close()
    }

    private fun imageProxyToJpeg(image: ImageProxy): ByteArray? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        return if (image.format == ImageFormat.JPEG) {
            bytes
        } else if (image.format == ImageFormat.YUV_420_888) {
            try {
                val yuvImage = YuvImage(
                    yuv420888ToNv21(image),
                    ImageFormat.NV21,
                    image.width,
                    image.height,
                    null
                )
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 75, out)
                out.toByteArray()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val numPixels = image.width * image.height
        val nv21 = ByteArray(numPixels + 2 * (image.width / 2) * (image.height / 2))

        yBuffer.get(nv21, 0, numPixels)

        val uBytes = ByteArray(uBuffer.remaining())
        val vBytes = ByteArray(vBuffer.remaining())
        uBuffer.get(uBytes)
        vBuffer.get(vBytes)

        val uvLength = image.width * image.height / 2
        var index = numPixels
        for (i in 0 until uvLength / 2) {
            if (i < vBytes.size && i < uBytes.size) {
                nv21[index++] = vBytes[i]
                nv21[index++] = uBytes[i]
            }
        }
        return nv21
    }

    private fun toggleTorch() {
        val camera = activeCamera ?: return
        if (camera.cameraInfo.hasFlashUnit()) {
            isTorchOn = !isTorchOn
            camera.cameraControl.enableTorch(isTorchOn)
            updateTorchButtonUi()
        } else {
            Toast.makeText(this, "Flash not supported on this lens", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTorchButtonUi() {
        binding.btnCameraTorch.setImageResource(
            if (isTorchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        )
        val tint = if (isTorchOn) ContextCompat.getColor(this, R.color.gold_amber) else ContextCompat.getColor(this, R.color.text_primary)
        binding.btnCameraTorch.imageTintList = ColorStateList.valueOf(tint)
    }

    private fun closeFloatingCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {}
        activeCamera = null
        isTorchOn = false
        binding.cardFloatingCamera.visibility = View.GONE
        viewModel.clearVisionMode()
    }

    private fun startScreenVisionMode() {
        binding.boxScreenVisionBanner.visibility = View.VISIBLE
        viewModel.setVisionMode(ConversationState.VISION_SCREEN)
        Toast.makeText(this, "Screen Vision Active • Jarvis is observing", Toast.LENGTH_SHORT).show()
    }

    private fun stopScreenVisionMode() {
        binding.boxScreenVisionBanner.visibility = View.GONE
        viewModel.clearVisionMode()
        Toast.makeText(this, "Screen Vision Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun sendMessageFromInput() {
        val text = binding.etDrawerMessage.text?.toString()?.trim() ?: ""
        if (text.isNotBlank()) {
            val assistantName = JarvisApp.instance.preferences.assistantName.lowercase()
            val lower = text.lowercase()
            if (lower == "bye" || lower == "goodbye" || lower == "bye $assistantName" || lower == "goodbye $assistantName" || lower == "bye boss") {
                JarvisVoiceService.endSession(this)
            }
            viewModel.sendTextMessage(text)
            binding.etDrawerMessage.setText("")
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Live Clock
                launch {
                    viewModel.liveTime.collect { time ->
                        binding.tvLiveTime.text = time
                    }
                }

                // Battery %
                launch {
                    viewModel.batteryLevel.collect { battery ->
                        binding.tvBatteryPct.text = battery
                    }
                }

                // RAM %
                launch {
                    viewModel.ramUsage.collect { ram ->
                        binding.tvRamPct.text = ram
                    }
                }

                // Power state
                launch {
                    viewModel.isSessionOn.collect { isOn ->
                        updatePowerButtonUi(isOn)
                    }
                }

                // Mic Mute state
                launch {
                    viewModel.isMicMuted.collect { isMuted ->
                        updateMicMuteUi(isMuted)
                    }
                }

                // Conversation state
                launch {
                    viewModel.conversationState.collect { state ->
                        updateConversationStateUi(state)
                    }
                }

                // Audio level amplitude
                launch {
                    viewModel.audioLevel.collect { level ->
                        orbHelper.updateAudioLevel(level)
                    }
                }

                // Chat turns in Drawer
                launch {
                    viewModel.chatRepository.turns.collect { turns ->
                        chatAdapter.submitList(turns) {
                            if (turns.isNotEmpty()) {
                                binding.rvDrawerChatHistory.scrollToPosition(turns.size - 1)
                            }
                        }
                    }
                }

                // Events / Alerts
                launch {
                    viewModel.eventFlow.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun updatePowerButtonUi(isOn: Boolean) {
        val theme = JarvisApp.instance.preferences.theme
        val isGold = theme == "AMBER_GOLD"
        val isRed = theme == "RED"
        val activeColor = when {
            isRed -> R.color.red_primary
            isGold -> R.color.gold_primary
            else -> R.color.primary_cyan
        }
        val inactiveColor = when {
            isRed -> R.color.red_crimson
            isGold -> R.color.gold_amber
            else -> R.color.soft_blue
        }

        if (isOn) {
            binding.ivPowerIcon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, activeColor)
            )
            binding.btnPowerSession.setBackgroundResource(R.drawable.bg_power_button)
        } else {
            binding.ivPowerIcon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, inactiveColor)
            )
            binding.btnPowerSession.setBackgroundResource(R.drawable.bg_control_button)
        }
    }

    private fun updateMicMuteUi(isMuted: Boolean) {
        if (isMuted) {
            binding.btnMicMute.setImageResource(R.drawable.ic_mic_off)
            binding.btnMicMute.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.status_red)
            )
        } else {
            binding.btnMicMute.setImageResource(R.drawable.ic_mic)
            binding.btnMicMute.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
        }
    }

    private fun updateConversationStateUi(state: ConversationState) {
        val theme = JarvisApp.instance.preferences.theme
        val isGold = theme == "AMBER_GOLD"
        val isRed = theme == "RED"

        val activeThemeColor = ContextCompat.getColor(this, when {
            isRed -> R.color.red_primary
            isGold -> R.color.gold_primary
            else -> R.color.primary_cyan
        })
        val speakingColor = ContextCompat.getColor(this, when {
            isRed -> R.color.red_neon
            isGold -> R.color.gold_amber
            else -> R.color.neon_blue
        })

        val (displayLabel, dotColor, orbKey) = when (state) {
            ConversationState.OFF -> Triple("Offline", ContextCompat.getColor(this, R.color.text_muted), "offline")
            ConversationState.ENDED -> Triple("Session Ended", ContextCompat.getColor(this, R.color.text_muted), "offline")
            ConversationState.IDLE -> Triple("Ready", ContextCompat.getColor(this, R.color.status_green), "idle")
            ConversationState.ACTIVE -> Triple("Active", ContextCompat.getColor(this, R.color.status_green), "idle")
            ConversationState.BACKGROUND_LISTENING -> Triple("Wake Word Active", activeThemeColor, "background_listening")
            ConversationState.WAKE_DETECTED -> Triple("Wake Detected", activeThemeColor, "wake_detected")
            ConversationState.LISTENING -> Triple("Listening", activeThemeColor, "listening")
            ConversationState.SILENT -> Triple("Silent Mode", ContextCompat.getColor(this, R.color.gold_amber), "silent")
            ConversationState.THINKING -> Triple("Thinking", ContextCompat.getColor(this, R.color.purple_glow), "thinking")
            ConversationState.SPEAKING -> Triple("Speaking", speakingColor, "speaking")
            ConversationState.EXECUTING -> Triple("Executing", ContextCompat.getColor(this, R.color.gold_amber), "executing")
            ConversationState.VISION_CAMERA -> Triple("Camera Active", activeThemeColor, "vision_camera")
            ConversationState.VISION_SCREEN -> Triple("Vision Active", ContextCompat.getColor(this, R.color.status_red), "vision_screen")
            ConversationState.MUTED -> Triple("Muted", ContextCompat.getColor(this, R.color.status_red), "muted")
            ConversationState.ERROR -> Triple("Error", ContextCompat.getColor(this, R.color.status_red), "error")
        }

        binding.tvConversationState.text = displayLabel
        binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(dotColor)
        orbHelper.updateState(state)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (binding.cardFloatingCamera.visibility == View.VISIBLE) {
            closeFloatingCamera()
        } else if (binding.boxVisionDockPopup.visibility == View.VISIBLE) {
            binding.boxVisionDockPopup.visibility = View.GONE
        } else if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
