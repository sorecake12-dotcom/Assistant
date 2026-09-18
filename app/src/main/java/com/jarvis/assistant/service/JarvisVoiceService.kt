package com.jarvis.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.audio.WakeWordDetector
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.ui.home.MainActivity
import com.jarvis.assistant.util.ActionRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class JarvisVoiceService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "JarvisVoiceService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "assistant_voice_channel"
        private const val CHANNEL_NAME = "Assistant AI Voice Service"

        const val ACTION_START_SESSION = "action_start_session"
        const val ACTION_END_SESSION = "action_end_session"
        const val ACTION_MUTE = "action_mute"
        const val ACTION_UNMUTE = "action_unmute"
        const val ACTION_USER_INTERACTION = "action_user_interaction"

        private val _serviceState = MutableStateFlow(ConversationState.IDLE)
        val serviceState: StateFlow<ConversationState> = _serviceState.asStateFlow()

        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_START_SESSION
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java)
            context.stopService(intent)
        }

        fun endSession(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_END_SESSION
            }
            context.startService(intent)
        }

        fun notifyInteraction(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_USER_INTERACTION
            }
            context.startService(intent)
        }

        fun setMuted(context: Context, muted: Boolean) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = if (muted) ACTION_MUTE else ACTION_UNMUTE
            }
            context.startService(intent)
        }

        fun toggle(context: Context) {
            if (isRunning) {
                stop(context)
                JarvisApp.instance.preferences.isBackgroundVoiceEnabled = false
            } else {
                start(context)
                JarvisApp.instance.preferences.isBackgroundVoiceEnabled = true
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeWordDetector: WakeWordDetector? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var actionRouter: ActionRouter

    private var silenceJob: Job? = null
    private var silentModeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "JarvisVoiceService onCreate")
        isRunning = true
        actionRouter = ActionRouter(this)

        acquireWakeLock()
        initNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("JARVIS Session Starting..."))
        initTts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_END_SESSION -> endSessionInternal()
            ACTION_MUTE -> muteInternal()
            ACTION_UNMUTE -> unmuteInternal()
            ACTION_USER_INTERACTION -> resetSilenceTimer()
            ACTION_START_SESSION, null -> startSessionInternal()
        }
        return START_STICKY
    }

    private fun startSessionInternal() {
        cancelSilenceTimers()
        wakeWordDetector?.stop()
        wakeWordDetector = null

        _serviceState.value = ConversationState.ACTIVE
        val prefs = (application as JarvisApp).preferences
        val assistantName = prefs.assistantName
        updateNotification("Active Session — $assistantName")

        startCommandCapture()
        resetSilenceTimer()
    }

    private fun muteInternal() {
        cancelSilenceTimers()
        wakeWordDetector?.stop()
        wakeWordDetector = null
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null

        _serviceState.value = ConversationState.MUTED
        updateNotification("Microphone Muted")
    }

    private fun unmuteInternal() {
        if (_serviceState.value == ConversationState.MUTED) {
            startSessionInternal()
        }
    }

    private fun endSessionInternal() {
        cancelSilenceTimers()
        wakeWordDetector?.stop()
        wakeWordDetector = null
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null

        _serviceState.value = ConversationState.ENDED
        updateNotification("Session Ended — Start JARVIS to begin again")
        Log.i(TAG, "Session ended. Zero listening active.")
    }

    private fun resetSilenceTimer() {
        silenceJob?.cancel()
        silenceJob = null

        val current = _serviceState.value
        if (current == ConversationState.ENDED || current == ConversationState.MUTED || current == ConversationState.OFF) {
            return
        }

        // 30 seconds of complete silence triggers Silent Mode
        silenceJob = serviceScope.launch {
            delay(30_000L)
            enterSilentMode()
        }
    }

    private fun cancelSilenceTimers() {
        silenceJob?.cancel()
        silenceJob = null
        silentModeJob?.cancel()
        silentModeJob = null
    }

    private fun enterSilentMode() {
        cancelSilenceTimers()
        val prefs = (application as JarvisApp).preferences
        val assistantName = prefs.assistantName
        val message = "I am going into silent mode. If you want me, just say, Hello $assistantName."

        _serviceState.value = ConversationState.SILENT
        updateNotification("Silent Mode — Say 'Hello $assistantName' to resume")

        speak(message) {
            // Activate wake-word listening specifically for silent mode
            initWakeWordForSilentMode()

            // 2 minutes in Silent Mode without interaction ends the session
            silentModeJob = serviceScope.launch {
                delay(120_000L)
                timeoutAndEndSession()
            }
        }
    }

    private fun timeoutAndEndSession() {
        cancelSilenceTimers()
        speak("Bye Boss") {
            endSessionInternal()
        }
    }

    private fun initWakeWordForSilentMode() {
        val prefs = (application as JarvisApp).preferences
        val wakePhrase = "hello " + prefs.assistantName.lowercase()

        wakeWordDetector?.stop()
        wakeWordDetector = WakeWordDetector(this, wakePhrase) {
            onWakeWordTriggeredInSilentMode()
        }
        wakeWordDetector?.start()
    }

    private fun onWakeWordTriggeredInSilentMode() {
        // Wake phrase is ONLY valid when waking an EXISTING session from SILENT mode
        if (_serviceState.value != ConversationState.SILENT) {
            Log.d(TAG, "Ignoring wake word outside SILENT mode. Current: ${_serviceState.value}")
            return
        }

        cancelSilenceTimers()
        wakeWordDetector?.stop()
        wakeWordDetector = null

        _serviceState.value = ConversationState.WAKE_DETECTED
        updateNotification("Wake Detected: Responding...")

        val userName = (application as JarvisApp).preferences.userName.ifBlank { "Boss" }
        val greeting = "Yes, $userName?"

        speak(greeting) {
            _serviceState.value = ConversationState.ACTIVE
            updateNotification("Active Session")
            startCommandCapture()
            resetSilenceTimer()
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jarvis::VoiceServiceWakeLock")?.apply {
                acquire(24 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not acquire WakeLock", e)
        }
    }

    private fun initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous voice listening service for Assistant AI"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val assistantName = (application as? JarvisApp)?.preferences?.assistantName ?: "Jarvis"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Assistant AI • $assistantName")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun initTts() {
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.ENGLISH
            isTtsReady = true
        }
    }

    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isTtsReady || tts == null) {
            onDone?.invoke()
            return
        }

        val assistantName = (application as? JarvisApp)?.preferences?.assistantName ?: "Jarvis"
        _serviceState.value = ConversationState.SPEAKING
        updateNotification("$assistantName is speaking...")

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ASSISTANT_VOICE_${System.currentTimeMillis()}")
        }

        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                serviceScope.launch {
                    onDone?.invoke()
                }
            }
            override fun onError(utteranceId: String?) {
                serviceScope.launch {
                    onDone?.invoke()
                }
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "ASSISTANT_VOICE")
    }

    private fun startCommandCapture() {
        if (_serviceState.value == ConversationState.ENDED || _serviceState.value == ConversationState.MUTED) {
            return
        }

        val assistantName = (application as? JarvisApp)?.preferences?.assistantName ?: "Jarvis"
        _serviceState.value = ConversationState.LISTENING
        updateNotification("$assistantName is listening...")

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {
                    cancelSilenceTimers()
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _serviceState.value = ConversationState.THINKING
                }

                override fun onError(error: Int) {
                    Log.w(TAG, "Command capture error: $error")
                    if (_serviceState.value != ConversationState.ENDED && _serviceState.value != ConversationState.MUTED) {
                        _serviceState.value = ConversationState.ACTIVE
                        startCommandCapture()
                        resetSilenceTimer()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val command = matches?.firstOrNull() ?: ""
                    Log.i(TAG, "User spoken command: '$command'")

                    if (command.isNotBlank()) {
                        processSpokenCommand(command)
                    } else {
                        if (_serviceState.value != ConversationState.ENDED && _serviceState.value != ConversationState.MUTED) {
                            _serviceState.value = ConversationState.ACTIVE
                            startCommandCapture()
                            resetSilenceTimer()
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun processSpokenCommand(command: String) {
        _serviceState.value = ConversationState.EXECUTING
        updateNotification("Executing command...")

        val result = actionRouter.handleCommand(command)
        Log.i(TAG, "Action result: ${result.spokenFeedback}")

        speak(result.spokenFeedback) {
            if (result.shouldCloseApp) {
                val closeIntent = Intent("com.jarvis.assistant.ACTION_CLOSE_ASSISTANT").apply {
                    setPackage(packageName)
                }
                sendBroadcast(closeIntent)
            }

            if (result.shouldEndSession || result.shouldCloseApp) {
                endSessionInternal()
            } else if (result.pendingConfirmation) {
                // Keep listening for user confirmation ("Yes" / "No")
                startCommandCapture()
            } else {
                _serviceState.value = ConversationState.ACTIVE
                startCommandCapture()
                resetSilenceTimer()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "JarvisVoiceService onDestroy")
        isRunning = false
        _serviceState.value = ConversationState.OFF

        cancelSilenceTimers()

        wakeWordDetector?.release()
        wakeWordDetector = null

        speechRecognizer?.destroy()
        speechRecognizer = null

        tts?.stop()
        tts?.shutdown()
        tts = null

        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
