package com.takeruf.nagi

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry

/** Follow the same category/detail hierarchy on compact and expanded windows. */
fun ComposeTestRule.selectSettingsCategory(category: String) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val tag = "settings-category-$category"
    repeat(3) {
        if (onAllNodesWithTag("settings-categories").fetchSemanticsNodes().isNotEmpty()) {
            onNodeWithTag("settings-categories").performScrollToNode(hasTestTag(tag))
            onNodeWithTag(tag).performClick()
            waitForIdle()
            return
        }
        onNodeWithContentDescription(context.getString(R.string.ui_back_to_settings)).performClick()
        waitForIdle()
    }
    error("Settings categories were not reachable")
}

fun ComposeTestRule.returnToBrowserFromSettings() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    repeat(3) {
        if (onAllNodesWithContentDescription(context.getString(R.string.ui_back_to_browser)).fetchSemanticsNodes().isNotEmpty()) {
            onNodeWithContentDescription(context.getString(R.string.ui_back_to_browser)).performClick()
            waitForIdle()
            return
        }
        onNodeWithContentDescription(context.getString(R.string.ui_back_to_settings)).performClick()
        waitForIdle()
    }
    error("Browser was not reachable from Settings")
}
