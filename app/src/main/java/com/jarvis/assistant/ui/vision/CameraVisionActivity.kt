package com.jarvis.assistant.ui.vision

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.jarvis.assistant.databinding.ActivityCameraVisionBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraVisionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraVisionBinding
    private var cameraExecutor: ExecutorService? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var isScreenMode = false

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required for Camera Vision", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            binding.tvModeTitle.text = "SCREEN VISION • STREAMING"
            binding.tvVisionStatus.text = "Screen Capture Active for Analysis"
            Toast.makeText(this, "Screen capture active. Jarvis is analyzing your screen.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Screen capture permission was declined.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraVisionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isScreenMode = intent.getBooleanExtra("EXTRA_SCREEN_MODE", false)
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnCloseCamera.setOnClickListener {
            finish()
        }

        binding.btnFlipCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            startCamera()
        }

        if (isScreenMode) {
            binding.tvModeTitle.text = "SCREEN VISION • INITIALIZING"
            binding.btnFlipCamera.visibility = android.view.View.GONE
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

                val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (e: Exception) {
                Log.e("CameraVision", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
    }
}
