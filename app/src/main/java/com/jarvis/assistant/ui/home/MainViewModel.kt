package com.jarvis.assistant.ui.home

import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.action.ActionExecutor
import com.jarvis.assistant.action.ActionParser
import com.jarvis.assistant.action.AssistantAction
import com.jarvis.assistant.audio.AudioPlayer
import com.jarvis.assistant.audio.AudioRecorder
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.data.repository.ChatRepository
import com.jarvis.assistant.network.GeminiLiveWebSocket
import com.jarvis.assistant.personality.PersonalityManager
import com.jarvis.assistant.personality.PersonalityMode
import com.jarvis.assistant.util.PromptGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences: AppPreferences = (application as JarvisApp).preferences
    val chatRepository: ChatRepository = (application as JarvisApp).chatRepository

    private val _isSessionOn = MutableStateFlow(false)
    val isSessionOn: StateFlow<Boolean> = _isSessionOn.asStateFlow()

    private val _conversationState = MutableStateFlow(ConversationState.OFF)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private val _isMicMuted = MutableStateFlow(preferences.isMicMuted)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _connectionStatus = MutableStateFlow("READY")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _liveTime = MutableStateFlow("")
    val liveTime: StateFlow<String> = _liveTime.asStateFlow()

    private val _batteryLevel = MutableStateFlow("100%")
    val batteryLevel: StateFlow<String> = _batteryLevel.asStateFlow()

    private val _ramUsage = MutableStateFlow("RAM 50%")
    val ramUsage: StateFlow<String> = _ramUsage.asStateFlow()

    private val _personalityName = MutableStateFlow(preferences.personality)
    val personalityName: StateFlow<String> = _personalityName.asStateFlow()

    private val _assistantName = MutableStateFlow(preferences.assistantName)
    val assistantName: StateFlow<String> = _assistantName.asStateFlow()

    private val _eventFlow = MutableSharedFlow<String>()
    val eventFlow: SharedFlow<String> = _eventFlow.asSharedFlow()

    private var audioRecorder: AudioRecorder? = null
    private var audioPlayer: AudioPlayer? = null
    private var liveWebSocket: GeminiLiveWebSocket? = null
    private var timeClockJob: Job? = null
    private var systemMonitorJob: Job? = null

    private val currentTurnAssistantText = StringBuilder()
    private var lastUserSpeechDetectedTime = 0L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                val pct = (level * 100) / scale
                _batteryLevel.value = "$pct%"
            }
        }
    }

    init {
        startTimeClock()
        startSystemMonitor()
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            application.registerReceiver(batteryReceiver, filter)
        } catch (e: Exception) {
            _batteryLevel.value = "100%"
        }
    }

    private fun startTimeClock() {
        timeClockJob = viewModelScope.launch(Dispatchers.Default) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            while (isActive) {
                _liveTime.value = sdf.format(Date())
                delay(1000)
            }
        }
    }

    private fun startSystemMonitor() {
        systemMonitorJob = viewModelScope.launch(Dispatchers.Default) {
            val actManager = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            while (isActive) {
                actManager?.let {
                    it.getMemoryInfo(memInfo)
                    val totalMem = memInfo.totalMem.toDouble()
                    val availMem = memInfo.availMem.toDouble()
                    if (totalMem > 0) {
                        val usedPct = (((totalMem - availMem) / totalMem) * 100).toInt()
                        _ramUsage.value = "RAM $usedPct%"
                    }
                }
                delay(5000)
            }
        }
    }

    fun toggleSession() {
        if (_isSessionOn.value) {
            stopSession()
        } else {
            startSession()
        }
    }

    private fun startSession() {
        val apiKey = preferences.apiKey.trim()
        if (apiKey.isBlank()) {
            viewModelScope.launch {
                _eventFlow.emit("Please configure your Gemini API Key in Settings first.")
            }
            return
        }

        _isSessionOn.value = true
        _conversationState.value = if (_isMicMuted.value) ConversationState.IDLE else ConversationState.LISTENING
        currentTurnAssistantText.clear()

        // 1. Output Player (24kHz Mono Output)
        audioPlayer = AudioPlayer(
            onPlaybackStateChanged = { isPlaying ->
                if (isPlaying) {
                    _conversationState.value = ConversationState.SPEAKING
                } else {
                    finalizeTurn()
                    if (_isSessionOn.value) {
                        _conversationState.value = if (_isMicMuted.value) ConversationState.IDLE else ConversationState.LISTENING
                    }
                }
            },
            onPlaybackAmplitude = { amp ->
                if (_conversationState.value == ConversationState.SPEAKING) {
                    _audioLevel.value = amp
                }
            }
        ).apply { start() }

        // 2. Gemini Live WebSocket Connection
        val systemPrompt = PromptGenerator.generateSystemPrompt(
            personality = preferences.personality,
            userName = preferences.userName,
            assistantName = preferences.assistantName
        )

        liveWebSocket = GeminiLiveWebSocket(
            apiKey = apiKey,
            model = preferences.aiModel,
            voiceName = preferences.voice,
            systemPrompt = systemPrompt,
            listener = object : GeminiLiveWebSocket.Listener {
                override fun onConnectionStateChanged(status: String) {
                    _connectionStatus.value = status
                    if (status == "LIVE" && _conversationState.value == ConversationState.IDLE && !_isMicMuted.value) {
                        _conversationState.value = ConversationState.LISTENING
                    }
                }

                override fun onAudioDataReceived(pcmData: ByteArray) {
                    _conversationState.value = ConversationState.SPEAKING
                    audioPlayer?.enqueueAudio(pcmData)
                }

                override fun onAssistantTextReceived(textChunk: String) {
                    currentTurnAssistantText.append(textChunk)
                }

                override fun onInterrupted() {
                    audioPlayer?.flush()
                    currentTurnAssistantText.clear()
                    _conversationState.value = ConversationState.LISTENING
                }

                override fun onTurnCompleted() {
                    // Handled when audio finishes draining in audioPlayer
                }

                override fun onError(message: String) {
                    viewModelScope.launch {
                        _eventFlow.emit(message)
                    }
                }
            }
        ).apply {
            connect(viewModelScope)
        }

        // 3. Audio Recorder (16kHz Mono Input)
        audioRecorder = AudioRecorder { chunk, amplitude ->
            if (_isSessionOn.value) {
                liveWebSocket?.sendAudioChunk(chunk)
                if (amplitude > 0.08f) {
                    lastUserSpeechDetectedTime = System.currentTimeMillis()
                    if (_conversationState.value != ConversationState.SPEAKING && !_isMicMuted.value) {
                        _conversationState.value = ConversationState.LISTENING
                        _audioLevel.value = amplitude
                    }
                } else if (_conversationState.value == ConversationState.LISTENING) {
                    _audioLevel.value = amplitude
                    val silentDuration = System.currentTimeMillis() - lastUserSpeechDetectedTime
                    if (silentDuration > 1500 && lastUserSpeechDetectedTime > 0) {
                        _conversationState.value = ConversationState.THINKING
                    }
                }
            }
        }.apply {
            setMuted(preferences.isMicMuted)
            start(viewModelScope)
        }
    }

    private fun finalizeTurn() {
        val reply = currentTurnAssistantText.toString().trim()
        if (reply.isNotEmpty()) {
            chatRepository.addTurn(
                userText = "Spoken user query",
                jarvisText = reply
            )
            currentTurnAssistantText.clear()
        }
    }

    private fun stopSession() {
        _isSessionOn.value = false
        finalizeTurn()
        audioRecorder?.stop()
        audioRecorder = null
        audioPlayer?.flush()
        audioPlayer?.release()
        audioPlayer = null
        liveWebSocket?.disconnect()
        liveWebSocket = null
        _conversationState.value = ConversationState.OFF
        _connectionStatus.value = "READY"
        _audioLevel.value = 0f
    }

    fun setVisionMode(state: ConversationState) {
        _conversationState.value = state
    }

    fun clearVisionMode() {
        if (_isSessionOn.value) {
            _conversationState.value = if (_isMicMuted.value) ConversationState.MUTED else ConversationState.IDLE
        } else {
            _conversationState.value = ConversationState.OFF
        }
    }

    fun sendImageFrame(jpegBytes: ByteArray) {
        liveWebSocket?.sendImageFrame(jpegBytes)
    }

    fun toggleMicMute() {
        val newMuted = !_isMicMuted.value
        _isMicMuted.value = newMuted
        preferences.isMicMuted = newMuted
        audioRecorder?.setMuted(newMuted)

        if (_isSessionOn.value && _conversationState.value != ConversationState.VISION_CAMERA && _conversationState.value != ConversationState.VISION_SCREEN) {
            _conversationState.value = if (newMuted) ConversationState.MUTED else ConversationState.LISTENING
        }

        viewModelScope.launch {
            _eventFlow.emit(if (newMuted) "Microphone Muted" else "Microphone Unmuted")
        }
    }

    private var lastActivePersonality: String = preferences.personality
    private var lastActiveVoice: String = preferences.voice
    private var lastActiveAssistantName: String = preferences.assistantName

    fun refreshSettings() {
        _personalityName.value = preferences.personality
        _isMicMuted.value = preferences.isMicMuted
        _assistantName.value = preferences.assistantName
        audioRecorder?.setMuted(preferences.isMicMuted)

        val currentPersonality = preferences.personality
        val currentVoice = preferences.voice
        val currentAssistantName = preferences.assistantName

        // If session is active and user changed personality, voice, or assistant name in Settings, renew session with new instructions
        if (_isSessionOn.value && (currentPersonality != lastActivePersonality || currentVoice != lastActiveVoice || currentAssistantName != lastActiveAssistantName)) {
            lastActivePersonality = currentPersonality
            lastActiveVoice = currentVoice
            lastActiveAssistantName = currentAssistantName
            stopSession()
            startSession()
            viewModelScope.launch {
                val modeName = PersonalityMode.fromId(currentPersonality).displayName
                _eventFlow.emit("Applied $modeName Personality • $currentAssistantName")
            }
        } else {
            lastActivePersonality = currentPersonality
            lastActiveVoice = currentVoice
            lastActiveAssistantName = currentAssistantName
        }
    }

    fun sendTextMessage(userQuery: String) {
        val clean = userQuery.trim()
        if (clean.isEmpty()) return

        val assistantName = preferences.assistantName
        val action = ActionParser.parse(clean, assistantName)

        val reply: String
        if (action !is AssistantAction.UnknownAction) {
            val executor = ActionExecutor(getApplication())
            val result = executor.execute(action)
            reply = result.spokenFeedback
            if (result.shouldCloseApp) {
                val closeIntent = Intent("com.jarvis.assistant.ACTION_CLOSE_ASSISTANT").apply {
                    setPackage(getApplication<Application>().packageName)
                }
                getApplication<Application>().sendBroadcast(closeIntent)
            }
            if (result.shouldEndSession) {
                stopSession()
            }
        } else {
            // Record turn in repository with mode-specific behavioral reply
            val mode = PersonalityMode.fromId(preferences.personality)
            val name = preferences.userName.ifBlank { "Boss" }
            reply = when (mode) {
                PersonalityMode.GF -> when {
                    clean.contains("tired", ignoreCase = true) -> "Aww, then take it a little easy today na. You've been pushing yourself way too hard. ❤️"
                    clean.contains("doing", ignoreCase = true) -> "Just hanging around here waiting for you! What are you up to?"
                    clean.contains("day", ignoreCase = true) -> "Arey, my day is always better when I'm talking with you! How was yours, thak gaye kya?"
                    else -> "Haan $name, main sun rahi hoon na. Hamesha aapke saath hoon."
                }
                PersonalityMode.PROFESSIONAL -> when {
                    clean.contains("explain", ignoreCase = true) || clean.contains("error", ignoreCase = true) ->
                        "Analysis indicates nominal execution with the following parameters:\n1. Verify system configuration\n2. Maintain process integrity\n3. Execute required protocol."
                    clean.contains("day", ignoreCase = true) -> "All operations are performing within nominal parameters. Standing by for instructions."
                    clean.contains("tired", ignoreCase = true) -> "Understood. Recommending rest period to optimize cognitive focus. Current queue is persisted."
                    else -> "Instruction acknowledged. Processing request under executive guidelines."
                }
                PersonalityMode.AI_ASSISTANT -> when {
                    clean.contains("weather", ignoreCase = true) -> "I'll check the latest weather report for you right away."
                    clean.contains("day", ignoreCase = true) -> "It's been a great day assisting you, $name! Ready for anything you need."
                    clean.contains("ram", ignoreCase = true) -> "RAM is the short-term working memory of your device that holds active data for fast processor access."
                    clean.contains("tired", ignoreCase = true) -> "Make sure to get adequate rest, $name. I can handle any quick tasks if needed."
                    else -> "Online and ready, $name. Processing your request."
                }
            }
        }
        chatRepository.addTurn(userText = clean, jarvisText = reply)
    }

    override fun onCleared() {
        super.onCleared()
        timeClockJob?.cancel()
        systemMonitorJob?.cancel()
        try {
            getApplication<Application>().unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
        stopSession()
    }
}
