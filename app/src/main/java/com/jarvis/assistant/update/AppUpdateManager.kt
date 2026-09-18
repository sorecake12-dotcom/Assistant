package com.jarvis.assistant.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"

    const val GITHUB_REPO_OWNER = "sorecake12-dotcom"
    const val GITHUB_REPO_NAME = "Assistant"
    const val DEFAULT_UPDATE_METADATA_URL = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Volatile
    private var activeDownloadCall: Call? = null

    fun getInstalledVersion(context: Context): Pair<String, Long> {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val vName = pInfo.versionName ?: "1.0.1"
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            Pair(vName, vCode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read installed package info", e)
            Pair("1.0.1", 116L)
        }
    }

    suspend fun checkForUpdates(
        context: Context,
        endpointUrl: String = DEFAULT_UPDATE_METADATA_URL
    ): UpdateStatus = withContext(Dispatchers.IO) {
        try {
            val (installedName, installedCode) = getInstalledVersion(context)
            Log.i(TAG, "Checking for updates. Installed: $installedName ($installedCode) from $endpointUrl")

            val request = Request.Builder()
                .url(endpointUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Assistant-Android-AppUpdate")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext UpdateStatus.Error("Failed to check for updates (HTTP ${response.code})")
            }

            val responseBody = response.body?.string() ?: ""
            if (responseBody.isBlank()) {
                return@withContext UpdateStatus.Error("Empty update metadata received")
            }

            val updateInfo = parseReleaseMetadata(responseBody)
            Log.i(TAG, "Parsed remote update: ${updateInfo.versionName} (code ${updateInfo.versionCode})")

            if (compareVersions(installedCode, updateInfo.versionCode)) {
                UpdateStatus.UpdateAvailable(
                    updateInfo = updateInfo,
                    installedVersionName = installedName,
                    installedVersionCode = installedCode
                )
            } else {
                UpdateStatus.UpToDate(
                    installedVersionName = installedName,
                    installedVersionCode = installedCode
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            UpdateStatus.Error("Network error: ${e.localizedMessage ?: "Unable to contact update server"}")
        }
    }

    fun parseReleaseMetadata(jsonStr: String): UpdateInfo {
        val root = JsonParser.parseString(jsonStr).asJsonObject
        val tagName = if (root.has("tag_name") && !root.get("tag_name").isJsonNull) {
            root.get("tag_name").asString
        } else "v1.0.1"

        val cleanVersionName = tagName.trim().removePrefix("v").removePrefix("V")
        val body = if (root.has("body") && !root.get("body").isJsonNull) {
            root.get("body").asString
        } else ""

        val publishedAt = if (root.has("published_at") && !root.get("published_at").isJsonNull) {
            root.get("published_at").asString
        } else ""

        val versionCode = parseVersionCode(tagName, body)

        // Find APK asset
        var downloadUrl = ""
        var apkFileName = "Assistant-$cleanVersionName-release.apk"
        var apkSize = 0L

        if (root.has("assets") && root.get("assets").isJsonArray) {
            val assets = root.getAsJsonArray("assets")
            for (elem in assets) {
                val assetObj = elem.asJsonObject
                val name = assetObj.get("name")?.asString ?: ""
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkFileName = name
                    downloadUrl = assetObj.get("browser_download_url")?.asString ?: ""
                    apkSize = assetObj.get("size")?.asLong ?: 0L
                    break
                }
            }
        }

        if (downloadUrl.isEmpty()) {
            downloadUrl = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/download/$tagName/$apkFileName"
        }

        return UpdateInfo(
            versionName = cleanVersionName,
            versionCode = versionCode,
            downloadUrl = downloadUrl,
            releaseNotes = body.ifBlank { "What's new in $cleanVersionName:\n• Performance improvements and bug fixes\n• System stability and AI optimizations" },
            publishedAt = publishedAt,
            apkFileName = apkFileName,
            apkSize = apkSize
        )
    }

    fun parseVersionCode(tag: String, body: String?): Long {
        // 1. Explicit versionCode in release body (e.g. "versionCode: 117")
        val codeRegex = Regex("(?i)versionCode\\s*[:=]\\s*(\\d+)")
        val match = body?.let { codeRegex.find(it) }
        if (match != null) {
            return match.groupValues[1].toLongOrNull() ?: 0L
        }

        // 2. Derive from semantic versioning (Major.Minor.Patch)
        val clean = tag.trim().removePrefix("v").removePrefix("V")
        val parts = clean.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size >= 3) {
            val major = parts[0]
            val minor = parts[1]
            val patch = parts[2]
            return 115L + (major - 1) * 1000L + minor * 100L + patch
        } else if (parts.size == 2) {
            val major = parts[0]
            val minor = parts[1]
            return 115L + (major - 1) * 1000L + minor * 100L
        }
        return 0L
    }

    fun compareVersions(installedCode: Long, remoteCode: Long): Boolean {
        return remoteCode > installedCode
    }

    suspend fun downloadApk(
        context: Context,
        updateInfo: UpdateInfo,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val targetDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir, "updates")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val apkFile = File(targetDir, updateInfo.apkFileName)
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .header("User-Agent", "Assistant-Android-AppUpdate")
                .get()
                .build()

            val call = httpClient.newCall(request)
            activeDownloadCall = call

            val response = call.execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed with HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty response body from download server"))
            val totalBytes = if (body.contentLength() > 0) body.contentLength() else updateInfo.apkSize

            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                inputStream = body.byteStream()
                outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloadedBytes = 0L
                var lastReportedPercent = -1

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val percent = if (totalBytes > 0) {
                        ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                    } else 0

                    if (percent != lastReportedPercent) {
                        lastReportedPercent = percent
                        withContext(Dispatchers.Main) {
                            onProgress(percent, downloadedBytes, totalBytes)
                        }
                    }
                }
                outputStream.flush()
            } finally {
                inputStream?.close()
                outputStream?.close()
                activeDownloadCall = null
            }

            // Verify file integrity
            if (!apkFile.exists() || apkFile.length() <= 0L) {
                return@withContext Result.failure(Exception("Downloaded APK file is empty or missing"))
            }

            // Verify with PackageManager that this is indeed a valid Android APK archive
            val archiveInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            if (archiveInfo == null) {
                apkFile.delete()
                return@withContext Result.failure(Exception("Downloaded file is not a valid Android APK package or is corrupted."))
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download APK", e)
            Result.failure(e)
        }
    }

    fun cancelDownload() {
        activeDownloadCall?.cancel()
        activeDownloadCall = null
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open unknown app sources settings", e)
                val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }
    }

    fun launchPackageInstaller(context: Context, apkFile: File): Boolean {
        return try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            false
        }
    }
}
