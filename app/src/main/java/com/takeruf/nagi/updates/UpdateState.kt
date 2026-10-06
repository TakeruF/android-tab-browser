package com.takeruf.nagi.updates

import java.io.File

data class AppRelease(val versionCode: Long, val versionName: String, val apkUrl: String,
    val sha256: String, val size: Long, val notes: String, val minSdk: Int)

enum class UpdateStatus { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, ERROR, UNSUPPORTED }
data class UpdateState(val status: UpdateStatus = UpdateStatus.IDLE, val release: AppRelease? = null,
    val progress: Int = 0, val apk: File? = null)

