package com.takeruf.nagi.ui.browser

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.engine.GoogleSignInRecovery
import com.takeruf.nagi.browser.engine.PageState
import com.takeruf.nagi.ui.localization.rememberNagiStrings

@Composable
fun GoogleSignInRecoveryBar(page: PageState, onSwitchToMobile: () -> Unit) {
    var dismissed by remember(page.url, page.desktopMode) { mutableStateOf(false) }
    if (!page.desktopMode || page.isLoading || page.error != null || dismissed ||
        !GoogleSignInRecovery.isRejectedPage(page.url)) return
    val strings = rememberNagiStrings()
    BrowserStatusBar(strings(R.string.ui_google_sign_in_mobile_hint),
        strings(R.string.ui_google_sign_in_mobile_retry), onSwitchToMobile,
        modifier = Modifier.testTag("google-sign-in-recovery-bar"), maxLines = 4,
        actionModifier = Modifier.testTag("google-sign-in-mobile-retry"),
        onDismiss = { dismissed = true }, dismissLabel = strings(R.string.ui_google_sign_in_hint_dismiss),
        dismissModifier = Modifier.testTag("google-sign-in-hint-dismiss"))
}
