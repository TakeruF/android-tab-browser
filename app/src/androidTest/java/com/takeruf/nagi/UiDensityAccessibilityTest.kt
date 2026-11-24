package com.takeruf.nagi

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.domain.model.ThemeMode
import com.takeruf.nagi.ui.settings.SettingsAction
import com.takeruf.nagi.ui.theme.NagiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UiDensityAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test fun largeTextGrowsDenseSettingsAndKeyboardActivationStillWorks() {
        var scale by mutableFloatStateOf(1f)
        var hidden by mutableIntStateOf(Configuration.HARDKEYBOARDHIDDEN_NO)
        var clicks = 0
        val title = "長い説明を含む設定項目は、文字を省略せず高さを広げて表示します"
        compose.setContent {
            val density = LocalDensity.current
            val inputMode = LocalInputModeManager.current
            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
            val config = Configuration(LocalConfiguration.current).apply {
                keyboard = Configuration.KEYBOARD_QWERTY
                hardKeyboardHidden = hidden
            }
            CompositionLocalProvider(LocalConfiguration provides config,
                LocalDensity provides Density(density.density, scale)) {
                NagiTheme(ThemeMode.LIGHT) {
                    Surface {
                        Column(Modifier.width(300.dp)) {
                            SettingsAction(title, Modifier.testTag("density-action"), subtitle = "Supporting text stays readable.") { clicks++ }
                        }
                    }
                }
            }
        }
        val action = compose.onNodeWithTag("density-action")
        val regular = action.fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { scale = 2f }
        action.assertTextContains(title).assertIsDisplayed()
        assertTrue(action.fetchSemanticsNode().boundsInRoot.height > regular)
        compose.onRoot().performKeyInput { pressKey(Key.Tab) }
        action.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        assertEquals(1, clicks)
        compose.runOnIdle { hidden = Configuration.HARDKEYBOARDHIDDEN_YES }
        action.assertTextContains(title).assertIsFocused()
        action.performKeyInput { pressKey(Key.Enter) }
        assertEquals(2, clicks)
    }
}
