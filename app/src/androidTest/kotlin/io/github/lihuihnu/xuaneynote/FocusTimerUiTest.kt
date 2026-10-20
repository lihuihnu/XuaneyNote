package io.github.lihuihnu.xuaneynote

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 只测 C0 导航及状态交互。模拟器不能替代真机熄屏/重启验收。 */
@RunWith(AndroidJUnit4::class)
class FocusTimerUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun focusNavigationPauseResumeAndRecreate() {
        compose.onNodeWithTag("focus-nav").performClick()
        compose.onNodeWithTag("focus-screen").assertExists()
        compose.onNodeWithTag("focus-mode-short").performClick()
        compose.onNodeWithTag("focus-start").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("focus-pause").fetchSemanticsNodes().isNotEmpty()
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("focus-screen").assertExists()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("focus-pause").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("focus-pause").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("focus-resume").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("focus-resume").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("focus-reset").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("focus-reset").performClick()
        compose.onNodeWithTag("focus-reset-confirm").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("focus-start").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("focus-clock").assertExists()
    }
}
