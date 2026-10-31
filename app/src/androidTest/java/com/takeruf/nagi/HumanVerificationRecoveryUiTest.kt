package com.takeruf.nagi

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.browser.engine.HumanVerificationState
import com.takeruf.nagi.browser.engine.PageState
import com.takeruf.nagi.domain.model.ThemeMode
import com.takeruf.nagi.ui.browser.HumanVerificationRecoveryBar
import com.takeruf.nagi.ui.theme.NagiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Synthetic status bar only; no accounts, network, or screenshots. */
class HumanVerificationRecoveryUiTest {
    @get:Rule val compose = createComposeRule()
    private val failed = PageState(url = "https://example.test/login", desktopMode = true,
        humanVerification = HumanVerificationState(1, true))
    private val page = mutableStateOf(failed)
    private val supported = mutableStateOf(true)
    private var retries = 0

    private fun content() = compose.setContent {
        val context = LocalContext.current
        DisposableEffect(context) {
            val window = (context as Activity).window
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        NagiTheme(ThemeMode.LIGHT) {
            Box(Modifier.width(320.dp)) {
                HumanVerificationRecoveryBar(page.value, supported.value) {
                    retries++
                    page.value = page.value.copy(desktopMode = false, deviceDefaultMode = true)
                }
            }
        }
    }

    @Test fun recoveryRequiresAnExplicitTapAndHidesAfterChangingMode() {
        content()
        compose.onNodeWithTag("human-verification-recovery-bar").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, retries) }
        compose.onNodeWithTag("human-verification-retry").performClick()
        compose.onNodeWithTag("human-verification-recovery-bar").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun dismissalSurvivesSignalChangesButResetsForANewDocumentAtTheSameUrl() {
        content()
        compose.onNodeWithTag("human-verification-dismiss").performClick()
        compose.runOnIdle { page.value = failed.copy(humanVerification = HumanVerificationState(1, false)) }
        compose.waitForIdle()
        compose.runOnIdle { page.value = failed }
        compose.onNodeWithTag("human-verification-recovery-bar").assertDoesNotExist()
        compose.runOnIdle { page.value = failed.copy(humanVerification = HumanVerificationState(2, true)) }
        compose.onNodeWithTag("human-verification-recovery-bar").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, retries) }
    }

    @Test fun normalMobileLoadingErrorsAndGoogleRecoveryDoNotShowTheBar() {
        content()
        for (state in listOf(failed.copy(desktopMode = false), failed.copy(deviceDefaultMode = true),
            failed.copy(isLoading = true), failed.copy(error = "Offline"),
            failed.copy(humanVerification = HumanVerificationState(1, false)),
            failed.copy(url = "https://accounts.google.com/v3/signin/rejected"))) {
            compose.runOnIdle { page.value = state }
            compose.onNodeWithTag("human-verification-recovery-bar").assertDoesNotExist()
        }
        compose.runOnIdle { page.value = failed; supported.value = false }
        compose.onNodeWithTag("human-verification-recovery-bar").assertDoesNotExist()
    }
}
