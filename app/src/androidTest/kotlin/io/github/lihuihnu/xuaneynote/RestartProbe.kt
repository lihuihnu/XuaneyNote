package io.github.lihuihnu.xuaneynote

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** CI 分两次 instrumentation 执行，中间 adb force-stop；不能把 recreate 冒充进程重启。 */
@RunWith(AndroidJUnit4::class)
class RestartProbe {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun seed() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("busy").fetchSemanticsNodes().isEmpty() }
        if (compose.onAllNodesWithTag("single-pane").fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodesWithTag("note-title").fetchSemanticsNodes().isNotEmpty()) {
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("note-title").fetchSemanticsNodes().isEmpty() }
        }
        compose.onNodeWithTag("new-note").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("note-body").fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodesWithTag("busy").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("note-title").performTextReplacement("CI_PROCESS_RESTART_SENTINEL")
        compose.onNodeWithTag("note-body").performTextReplacement("进程终止后：已保存的中文与🙂必须完整找回。")
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("save-state") and hasText("已保存")).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun verify() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("busy").fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithTag("note-body").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("note-title").assertTextContains("CI_PROCESS_RESTART_SENTINEL")
        compose.onNodeWithTag("note-body").assertTextContains("进程终止后：已保存的中文与🙂必须完整找回。")
        val notes = runBlocking { (compose.activity.application as XuaneyApplication).data.notes.snapshot() }
        assertTrue(notes.any { it.title == "CI_PROCESS_RESTART_SENTINEL" && it.revision >= 2 })
    }
}
