package com.jarvis.assistant.update

data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val releaseNotes: String,
    val publishedAt: String,
    val apkFileName: String,
    val apkSize: Long = 0L
)
