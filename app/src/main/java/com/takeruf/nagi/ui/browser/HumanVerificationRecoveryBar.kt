package com.takeruf.nagi.ui.browser

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.engine.GoogleSignInRecovery
import com.takeruf.nagi.browser.engine.PageState
import com.takeruf.nagi.ui.localization.rememberNagiStrings

@Composable
fun HumanVerificationRecoveryBar(page: PageState, supported: Boolean, onRetry: () -> Unit) {
    var dismissed by remember(page.humanVerification.documentId, page.desktopMode) { mutableStateOf(false) }
    if (!supported || !page.desktopMode || page.deviceDefaultMode || page.isLoading || page.error != null ||
        page.readerArticle != null || !page.humanVerification.turnstileFailed || dismissed ||
        GoogleSignInRecovery.isRejectedPage(page.url)) return
    val strings = rememberNagiStrings()
    BrowserStatusBar(strings(R.string.ui_human_verification_hint), strings(R.string.ui_human_verification_retry),
        onRetry, modifier = Modifier.testTag("human-verification-recovery-bar"), maxLines = 4,
        actionModifier = Modifier.testTag("human-verification-retry"), onDismiss = { dismissed = true },
        dismissLabel = strings(R.string.ui_human_verification_dismiss),
        dismissModifier = Modifier.testTag("human-verification-dismiss"))
}
