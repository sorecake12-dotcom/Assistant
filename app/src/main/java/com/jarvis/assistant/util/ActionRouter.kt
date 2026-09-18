package com.jarvis.assistant.util

import android.content.Context
import android.util.Log
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.action.ActionExecutor
import com.jarvis.assistant.action.ActionParser
import com.jarvis.assistant.action.AssistantAction
import com.jarvis.assistant.action.ExecutionResult
import java.util.Locale

class ActionRouter(private val context: Context) {

    companion object {
        private const val TAG = "ActionRouter"
    }

    data class ActionResult(
        val success: Boolean,
        val spokenFeedback: String,
        val requiresUnlock: Boolean = false,
        val pendingConfirmation: Boolean = false,
        val shouldEndSession: Boolean = false,
        val shouldCloseApp: Boolean = false
    )

    private val executor = ActionExecutor(context)
    private var pendingMessage: Pair<String, String>? = null

    fun isDeviceLocked(): Boolean = executor.isDeviceLocked()

    fun handleCommand(rawQuery: String): ActionResult {
        val prefs = (context.applicationContext as? JarvisApp)?.preferences
            ?: JarvisApp.instance.preferences
        val assistantName = prefs.assistantName
        val query = rawQuery.lowercase(Locale.ROOT).trim()

        Log.i(TAG, "Routing voice/text command: '$query' with Assistant '$assistantName'")

        // 1. Pending Confirmation Handling (e.g. for messaging)
        if (pendingMessage != null) {
            if (query.contains("yes") || query.contains("send") || query.contains("confirm") || query.contains("sure")) {
                val (contact, text) = pendingMessage!!
                pendingMessage = null
                val result = executor.execute(
                    AssistantAction.SendMessageAction(
                        contact = contact,
                        text = text
                    )
                )
                return result.toActionResult()
            } else if (query.contains("no") || query.contains("cancel") || query.contains("stop")) {
                pendingMessage = null
                val userName = prefs.userName.ifBlank { "Boss" }
                return ActionResult(true, "Message cancelled, $userName.")
            }
        }

        // 2. Structured Action Parsing
        val action = ActionParser.parse(rawQuery, assistantName)
        Log.i(TAG, "Parsed structured action: $action")

        // 3. Execution via Structured ActionExecutor
        val executionResult = executor.execute(action)

        if (executionResult.pendingConfirmation && action is AssistantAction.SendMessageAction) {
            pendingMessage = Pair(action.contact, action.text)
        }

        return executionResult.toActionResult()
    }

    private fun ExecutionResult.toActionResult(): ActionResult {
        return ActionResult(
            success = success,
            spokenFeedback = spokenFeedback,
            requiresUnlock = requiresUnlock,
            pendingConfirmation = pendingConfirmation,
            shouldEndSession = shouldEndSession,
            shouldCloseApp = shouldCloseApp
        )
    }
}
