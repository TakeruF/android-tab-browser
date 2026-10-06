package com.takeruf.nagi.updates

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Play owns updates. This variant has no APK transport or installer. */
class AppUpdates(@Suppress("UNUSED_PARAMETER") context: Context,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope) {
    val state = MutableStateFlow(UpdateState(UpdateStatus.CURRENT)).asStateFlow()
    fun check() = Unit
}
