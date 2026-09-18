package com.jarvis.assistant.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun testParseVersionCode_fromSemanticTag() {
        val code101 = AppUpdateManager.parseVersionCode("v1.0.1", null)
        assertEquals(116L, code101)

        val code102 = AppUpdateManager.parseVersionCode("v1.0.2", "")
        assertEquals(117L, code102)

        val code110 = AppUpdateManager.parseVersionCode("v1.1.0", "Regular release")
        assertEquals(215L, code110)
    }

    @Test
    fun testParseVersionCode_explicitInBody() {
        val bodyWithColon = "### Release v1.0.2\n- Fixed camera vision\nversionCode: 120"
        assertEquals(120L, AppUpdateManager.parseVersionCode("v1.0.2", bodyWithColon))

        val bodyWithEquals = "Release notes\nversionCode = 135\nEnjoy!"
        assertEquals(135L, AppUpdateManager.parseVersionCode("v1.0.2", bodyWithEquals))
    }

    @Test
    fun testCompareVersions() {
        // Upgrade needed
        assertTrue(AppUpdateManager.compareVersions(installedCode = 116L, remoteCode = 117L))
        assertTrue(AppUpdateManager.compareVersions(installedCode = 116L, remoteCode = 200L))

        // Same version - up to date
        assertFalse(AppUpdateManager.compareVersions(installedCode = 116L, remoteCode = 116L))

        // Remote is older (downgrade prevention)
        assertFalse(AppUpdateManager.compareVersions(installedCode = 116L, remoteCode = 115L))
        assertFalse(AppUpdateManager.compareVersions(installedCode = 116L, remoteCode = 100L))
    }

    @Test
    fun testParseReleaseMetadata_withApkAsset() {
        val json = """
            {
              "tag_name": "v1.0.2",
              "body": "## What's Changed in v1.0.2\n- In-app update engine\n- Stability updates\nversionCode: 117",
              "published_at": "2026-09-18T20:00:00Z",
              "assets": [
                {
                  "name": "Assistant-1.0.2-release.apk",
                  "browser_download_url": "https://github.com/sorecake12-dotcom/Assistant/releases/download/v1.0.2/Assistant-1.0.2-release.apk",
                  "size": 6857832
                }
              ]
            }
        """.trimIndent()

        val updateInfo = AppUpdateManager.parseReleaseMetadata(json)

        assertEquals("1.0.2", updateInfo.versionName)
        assertEquals(117L, updateInfo.versionCode)
        assertEquals("Assistant-1.0.2-release.apk", updateInfo.apkFileName)
        assertEquals("https://github.com/sorecake12-dotcom/Assistant/releases/download/v1.0.2/Assistant-1.0.2-release.apk", updateInfo.downloadUrl)
        assertEquals(6857832L, updateInfo.apkSize)
        assertTrue(updateInfo.releaseNotes.contains("In-app update engine"))
    }

    @Test
    fun testParseReleaseMetadata_withoutAssets_generatesFallbackUrl() {
        val json = """
            {
              "tag_name": "v1.0.3",
              "body": "Minor patch",
              "published_at": "2026-09-18T21:00:00Z",
              "assets": []
            }
        """.trimIndent()

        val updateInfo = AppUpdateManager.parseReleaseMetadata(json)

        assertEquals("1.0.3", updateInfo.versionName)
        assertEquals(118L, updateInfo.versionCode)
        assertEquals("Assistant-1.0.3-release.apk", updateInfo.apkFileName)
        assertEquals("https://github.com/sorecake12-dotcom/Assistant/releases/download/v1.0.3/Assistant-1.0.3-release.apk", updateInfo.downloadUrl)
    }
}
