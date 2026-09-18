package com.jarvis.assistant.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class WakeWordDetector(
    private val context: Context,
    private val wakePhrase: String = "hello jarvis",
    private val onWakeDetected: () -> Unit
) {

    companion object {
        private const val TAG = "WakeWordDetector"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isDestroyed = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "WakeWordDetector ready for speech")
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            Log.d(TAG, "WakeWordDetector error: $error")
            // Ignore temporary speech errors and restart listening if still active
            if (!isDestroyed && isListening) {
                mainHandler.postDelayed({
                    restartListening()
                }, 400)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            checkMatches(matches)
            if (!isDestroyed && isListening) {
                restartListening()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (checkMatches(matches)) {
                // If wake word matched in partial results, stop immediately to handle command
                speechRecognizer?.stopListening()
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun checkMatches(matches: List<String>?): Boolean {
        if (matches.isNullOrEmpty()) return false

        val target = wakePhrase.lowercase(Locale.ROOT).trim()
        val nameOnly = target.removePrefix("hello ").removePrefix("hey ").removePrefix("ok ").trim()
        val helloTarget = "hello $nameOnly"
        val heyTarget = "hey $nameOnly"
        val okTarget = "ok $nameOnly"

        for (raw in matches) {
            val phrase = raw.lowercase(Locale.ROOT).trim()
            Log.d(TAG, "Detected phrase candidate: '$phrase'")
            if (phrase.contains(target) ||
                (nameOnly.isNotEmpty() && (
                    phrase.contains(helloTarget) ||
                    phrase.contains(heyTarget) ||
                    phrase.contains(okTarget) ||
                    phrase == nameOnly
                ))
            ) {
                Log.i(TAG, "Wake word matched: '$phrase' -> Triggering action!")
                mainHandler.post {
                    onWakeDetected()
                }
                return true
            }
        }
        return false
    }

    fun start() {
        mainHandler.post {
            if (isListening || isDestroyed) return@post
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "Speech recognition is not available on this device")
                return@post
            }

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(recognitionListener)
                }
                isListening = true
                startListeningInternal()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize SpeechRecognizer", e)
            }
        }
    }

    private fun startListeningInternal() {
        if (!isListening || isDestroyed) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
        }
    }

    private fun restartListening() {
        if (!isListening || isDestroyed) return
        try {
            speechRecognizer?.cancel()
            startListeningInternal()
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting listening", e)
        }
    }

    fun stop() {
        mainHandler.post {
            isListening = false
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping SpeechRecognizer", e)
            }
            speechRecognizer = null
        }
    }

    fun release() {
        isDestroyed = true
        stop()
    }
}
