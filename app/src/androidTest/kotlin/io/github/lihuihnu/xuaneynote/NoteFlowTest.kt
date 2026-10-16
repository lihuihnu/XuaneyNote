package io.github.lihuihnu.xuaneynote

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun createEditAutosaveAndActivityRecreation() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("busy").fetchSemanticsNodes().isEmpty() }
        if (compose.onAllNodesWithTag("single-pane").fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodesWithTag("note-title").fetchSemanticsNodes().isNotEmpty()) {
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("note-title").fetchSemanticsNodes().isEmpty() }
        }
        val width = compose.activity.resources.configuration.screenWidthDp
        compose.onNodeWithTag(if (width >= 840) "two-pane" else "single-pane").assertExists()
        compose.onNodeWithTag("new-note").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("note-body").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("busy").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("note-title").performTextReplacement("CI 合成笔记")
        compose.onNodeWithTag("note-body").performTextReplacement("手机和平板测试\n重开后应保留🙂")
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("save-state") and hasText("已保存")).fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("note-body").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("note-title").assertTextContains("CI 合成笔记")
        compose.onNodeWithTag("note-body").assertTextContains("手机和平板测试\n重开后应保留🙂")
    }
    @Test fun applicationHasNoNetworkPermissionsOrAutomaticBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions.orEmpty().toList()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        assertFalse(permissions.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
