package com.jarvis.assistant.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "JarvisA11yService"
        var instance: JarvisAccessibilityService? = null
            private set

        fun isServiceEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "JARVIS Accessibility Service connected and active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events are monitored to maintain awareness of active window
    }

    override fun onInterrupt() {
        Log.w(TAG, "JARVIS Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "JARVIS Accessibility Service destroyed")
    }

    fun scrollDown(): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollableNode(root)
        val result = scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
        scrollable?.recycle()
        root.recycle()
        return result
    }

    fun scrollUp(): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollableNode(root)
        val result = scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) ?: false
        scrollable?.recycle()
        root.recycle()
        return result
    }

    fun clickByText(targetText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val lowerTarget = targetText.lowercase(Locale.ROOT).trim()
        val node = findNodeMatching(root) { nodeInfo ->
            val text = nodeInfo.text?.toString()?.lowercase(Locale.ROOT)
            val desc = nodeInfo.contentDescription?.toString()?.lowercase(Locale.ROOT)
            (text != null && text.contains(lowerTarget)) || (desc != null && desc.contains(lowerTarget))
        }

        var clicked = false
        if (node != null) {
            // Find clickable parent if node itself is not clickable
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable) {
                    clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    break
                }
                current = current.parent
            }
            node.recycle()
        }
        root.recycle()
        return clicked
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findNodeMatching(root) { it.isEditable }

        var success = false
        if (focused != null && focused.isEditable) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            success = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            focused.recycle()
        }
        root.recycle()
        return success
    }

    fun goBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun goHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun getVisibleHierarchySummary(): String {
        val root = rootInActiveWindow ?: return "No active window content"
        val sb = StringBuilder()
        collectText(root, sb)
        root.recycle()
        return sb.toString().trim()
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun findNodeMatching(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeMatching(child, predicate)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            sb.append(text).append(" | ")
        } else if (!desc.isNullOrEmpty()) {
            sb.append(desc).append(" | ")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, sb)
            child.recycle()
        }
    }
}
