package com.jarvis.assistant.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActionParserTest {

    @Before
    fun setUp() {
        MediaContextHolder.lastPlayedTrack = null
        MediaContextHolder.lastPlatform = MediaPlatform.DEFAULT
    }

    @Test
    fun testCloseAssistantCommands() {
        assertTrue(ActionParser.parse("close the app") is AssistantAction.CloseAssistantAction)
        assertTrue(ActionParser.parse("close assistant") is AssistantAction.CloseAssistantAction)
        assertTrue(ActionParser.parse("exit assistant") is AssistantAction.CloseAssistantAction)
        assertTrue(ActionParser.parse("close app") is AssistantAction.CloseAssistantAction)
        assertTrue(ActionParser.parse("quit") is AssistantAction.CloseAssistantAction)
    }

    @Test
    fun testEndSessionCommands() {
        assertTrue(ActionParser.parse("bye") is AssistantAction.EndSessionAction)
        assertTrue(ActionParser.parse("goodbye") is AssistantAction.EndSessionAction)
        assertTrue(ActionParser.parse("end call") is AssistantAction.EndSessionAction)
        assertTrue(ActionParser.parse("stop session") is AssistantAction.EndSessionAction)
        assertTrue(ActionParser.parse("bye Jarvis") is AssistantAction.EndSessionAction)
    }

    @Test
    fun testHomeScreenCommands() {
        assertTrue(ActionParser.parse("go to home screen") is AssistantAction.GoHomeAction)
        assertTrue(ActionParser.parse("go home") is AssistantAction.GoHomeAction)
        assertTrue(ActionParser.parse("home screen") is AssistantAction.GoHomeAction)
    }

    @Test
    fun testAssistantHomeCommands() {
        assertTrue(ActionParser.parse("go to assistant home") is AssistantAction.OpenAssistantHomeAction)
        assertTrue(ActionParser.parse("open assistant home") is AssistantAction.OpenAssistantHomeAction)
        assertTrue(ActionParser.parse("return to assistant") is AssistantAction.OpenAssistantHomeAction)
    }

    @Test
    fun testSettingsCommands() {
        assertTrue(ActionParser.parse("open settings") is AssistantAction.SettingsAction)
        assertTrue(ActionParser.parse("settings") is AssistantAction.SettingsAction)
        assertTrue(ActionParser.parse("configure assistant") is AssistantAction.SettingsAction)
    }

    @Test
    fun testPlayStoreCommands() {
        val action = ActionParser.parse("install Spotify")
        assertTrue(action is AssistantAction.OpenPlayStoreAction)
        val playAction = action as AssistantAction.OpenPlayStoreAction
        assertEquals("Spotify", playAction.appName)
        assertEquals("com.spotify.music", playAction.packageName)

        val openPlayStore = ActionParser.parse("open play store")
        assertTrue(openPlayStore is AssistantAction.OpenPlayStoreAction)
    }

    @Test
    fun testMediaCommandsAndContextResolution() {
        // 1. Play track on YouTube
        val action1 = ActionParser.parse("Play Barsaat on YouTube")
        assertTrue(action1 is AssistantAction.PlayMediaAction)
        val media1 = action1 as AssistantAction.PlayMediaAction
        assertEquals("Barsaat", media1.query)
        assertEquals(MediaPlatform.YOUTUBE, media1.platform)

        // 2. Play that on Spotify -> resolves "that" to "Barsaat"
        val action2 = ActionParser.parse("Play that on Spotify")
        assertTrue(action2 is AssistantAction.PlayMediaAction)
        val media2 = action2 as AssistantAction.PlayMediaAction
        assertEquals("Barsaat", media2.query)
        assertEquals(MediaPlatform.SPOTIFY, media2.platform)

        // 3. Media Controls
        assertTrue(ActionParser.parse("pause") is AssistantAction.PauseMediaAction)
        assertTrue(ActionParser.parse("resume") is AssistantAction.ResumeMediaAction)
        assertTrue(ActionParser.parse("next") is AssistantAction.NextMediaAction)
        assertTrue(ActionParser.parse("stop music") is AssistantAction.StopMediaAction)
    }

    @Test
    fun testMultipleUrlsCommand() {
        val action = ActionParser.parse("open Vercel Netflix and Prime Video")
        assertTrue(action is AssistantAction.OpenMultipleUrlsAction)
        val multi = action as AssistantAction.OpenMultipleUrlsAction
        assertEquals(3, multi.destinations.size)
        assertEquals("Vercel", multi.destinations[0].name)
        assertEquals("Netflix", multi.destinations[1].name)
        assertEquals("Prime Video", multi.destinations[2].name)
    }

    @Test
    fun testAppOpeningCommand() {
        val action = ActionParser.parse("open WhatsApp")
        assertTrue(action is AssistantAction.OpenAppAction)
        val app = action as AssistantAction.OpenAppAction
        assertEquals("WhatsApp", app.appName)
        assertEquals("com.whatsapp", app.packageName)
    }

    @Test
    fun testCallAndMessageCommands() {
        val callAction = ActionParser.parse("call Mom")
        assertTrue(callAction is AssistantAction.CallContactAction)
        assertEquals("Mom", (callAction as AssistantAction.CallContactAction).target)

        val msgAction = ActionParser.parse("message Rahul saying I'll be late")
        assertTrue(msgAction is AssistantAction.SendMessageAction)
        val msg = msgAction as AssistantAction.SendMessageAction
        assertEquals("Rahul", msg.contact)
        assertEquals("I'll be late", msg.text)
        assertEquals(MessagePlatform.SMS, msg.platform)

        val waAction = ActionParser.parse("send whatsapp message to Rahul saying on my way")
        assertTrue(waAction is AssistantAction.SendMessageAction)
        val waMsg = waAction as AssistantAction.SendMessageAction
        assertEquals("Rahul", waMsg.contact)
        assertEquals("on my way", waMsg.text)
        assertEquals(MessagePlatform.WHATSAPP, waMsg.platform)
    }

    @Test
    fun testAccessibilityNavigationCommands() {
        val scrollDown = ActionParser.parse("scroll down")
        assertTrue(scrollDown is AssistantAction.AccessibilityAction)
        assertEquals(ScreenNavType.SCROLL_DOWN, (scrollDown as AssistantAction.AccessibilityAction).navType)

        val clickAction = ActionParser.parse("click Submit")
        assertTrue(clickAction is AssistantAction.AccessibilityAction)
        assertEquals(ScreenNavType.CLICK_TEXT, (clickAction as AssistantAction.AccessibilityAction).navType)
        assertEquals("Submit", (clickAction as AssistantAction.AccessibilityAction).targetText)
    }

    @Test
    fun testCustomAssistantNameStripping() {
        val action = ActionParser.parse("Hello Iris, open Spotify", assistantName = "Iris")
        assertTrue(action is AssistantAction.OpenAppAction)
        assertEquals("Spotify", (action as AssistantAction.OpenAppAction).appName)
    }
}
