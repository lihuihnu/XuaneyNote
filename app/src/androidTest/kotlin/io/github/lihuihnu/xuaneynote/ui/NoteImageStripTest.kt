package io.github.lihuihnu.xuaneynote.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lihuihnu.xuaneynote.data.AttachmentEntity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 直接渲染隔离组件以验证未来 Picker 编辑 UI，但正式 app 仍默认禁止添加图片。 */
@RunWith(AndroidJUnit4::class)
class NoteImageStripTest {
    @get:Rule val compose = createComposeRule()

    private val item = AttachmentEntity(
        "00000000-0000-0000-0000-000000000801",
        "images/00000000-0000-0000-0000-000000000801.png",
        "image/png", 2048, "a".repeat(64), 100, 16, 12
    )

    @Test fun editableComponentHasAddAndExplicitRemoveConfirmation() {
        val bitmap = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888)
        var addCount = 0
        var removedId: String? = null
        try {
            compose.setContent {
                MaterialTheme {
                    NoteImageStrip(listOf(item), busy = false, allowEdit = true,
                        onAdd = { addCount++ },
                        onRemove = { removedId = it },
                        loadImage = { _, _ -> bitmap })
                }
            }
            compose.onNodeWithTag("add-note-image").performClick()
            assertEquals(1, addCount)
            compose.onNodeWithTag("remove-note-image-" + item.id).performClick()
            compose.onNodeWithTag("cancel-remove-image").performClick()
            assertNull(removedId)
            compose.onNodeWithTag("remove-note-image-" + item.id).performClick()
            compose.onNodeWithTag("confirm-remove-image").performClick()
            assertEquals(item.id, removedId)
            assertEquals(1, addCount)
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun readOnlyComponentShowsRecoveredImageButNoAddOrRemove() {
        val bitmap = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888)
        try {
            compose.setContent {
                MaterialTheme {
                    NoteImageStrip(listOf(item), busy = false, allowEdit = false,
                        onAdd = { error("未开放的 Photo Picker 不可触发") },
                        onRemove = { error("只读图片不可删除") },
                        loadImage = { _, _ -> bitmap })
                }
            }
            compose.onNodeWithTag("add-note-image").assertDoesNotExist()
            compose.onNodeWithTag("remove-note-image-" + item.id).assertDoesNotExist()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("image-loaded").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("note-image-" + item.id).performClick()
            compose.onNodeWithTag("image-preview-expanded").assertExists()
        } finally {
            bitmap.recycle()
        }
    }
}
