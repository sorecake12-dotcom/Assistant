package com.jarvis.assistant.update

import java.io.File

sealed class UpdateStatus {
    data object Idle : UpdateStatus()
    data object Checking : UpdateStatus()
    data class UpToDate(
        val installedVersionName: String,
        val installedVersionCode: Long
    ) : UpdateStatus()
    data class UpdateAvailable(
        val updateInfo: UpdateInfo,
        val installedVersionName: String,
        val installedVersionCode: Long
    ) : UpdateStatus()
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateStatus()
    data class ReadyToInstall(
        val updateInfo: UpdateInfo,
        val apkFile: File
    ) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}
