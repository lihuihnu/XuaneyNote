package io.github.lihuihnu.xuaneynote.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.lihuihnu.xuaneynote.model.Note
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** B3.1 仅验证数据库合同；不写入真实图片文件、不引用个人资料。 */
@RunWith(AndroidJUnit4::class)
class AttachmentSchemaTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), NoteDatabase::class.java
    )
    private val note = Note("00000000-0000-0000-0000-000000000331",
        "v2 附件迁移合成笔记", "中文和🙂保持原样", 100, 200, 5)
    private val second = Note("00000000-0000-0000-0000-000000000332",
        "第二篇笔记", "用于验证共享文件的引用", 120, 240, 3)

    private fun item() = AttachmentEntity(
        id = "00000000-0000-0000-0000-000000000333",
        relativePath = "images/00000000-0000-0000-0000-000000000333.png",
        mimeType = "image/png",
        byteSize = 1024,
        sha256 = "a".repeat(64),
        createdAt = 300,
        width = 10,
        height = 20
    )

    @Test fun v2SchemaMigratesToV3WithoutChangingNotesOrTrash() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "b3-v2-migration-${UUID.randomUUID()}.db"
        try {
            helper.createDatabase(name, 2).use { old ->
                for (entry in listOf(note, second)) {
                    old.execSQL(
                        "INSERT INTO `notes` (`id`, `title`, `body`, `createdAt`, " +
                            "`updatedAt`, `revision`, `bodyFormat`, `bodyVersion`, " +
                            "`deletedAt`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(entry.id, entry.title, entry.body, entry.createdAt,
                            entry.updatedAt, entry.revision, entry.bodyFormat, entry.bodyVersion,
                            if (entry.id == note.id) 222L else null)
                    )
                }
            }
            helper.runMigrationsAndValidate(name, 3, true,
                DatabaseMigrations.MIGRATION_2_3).use { upgraded ->
                upgraded.query("SELECT id, body, revision, deletedAt FROM notes ORDER BY id").use { cursor ->
                    assertEquals(2, cursor.count)
                    assertTrue(cursor.moveToFirst())
                    assertEquals(note.id, cursor.getString(0))
                    assertEquals(note.body, cursor.getString(1))
                    assertEquals(5L, cursor.getLong(2))
                    assertEquals(222L, cursor.getLong(3))
                    assertTrue(cursor.moveToNext())
                    assertEquals(second.id, cursor.getString(0))
                    assertEquals(second.body, cursor.getString(1))
                    assertTrue(cursor.isNull(3))
                }
                for (nameOfTable in listOf("attachments", "note_attachments")) {
                    upgraded.query("SELECT COUNT(*) FROM `$nameOfTable`").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(0, cursor.getInt(0))
                    }
                }
            }
            val db = NoteDatabase.open(ctx, name)
            try {
                assertEquals(3, db.openHelper.readableDatabase.version)
                val deleted = requireNotNull(db.notes().find(note.id))
                assertEquals(222L, deleted.deletedAt)
                assertEquals(note.body, deleted.body)
                assertEquals(second, NoteStore(db).get(second.id))
            } finally { db.close() }
        } finally { ctx.deleteDatabase(name) }
    }

    @Test fun productionV1ToV3UpgradeKeepsOriginalNotebookData() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "b3-v1-migration-${UUID.randomUUID()}.db"
        try {
            helper.createDatabase(name, 1).use { old ->
                old.execSQL(
                    "INSERT INTO `notes` (`id`, `title`, `body`, `createdAt`, " +
                        "`updatedAt`, `revision`, `bodyFormat`, `bodyVersion`) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(note.id, note.title, note.body, note.createdAt,
                        note.updatedAt, note.revision, note.bodyFormat, note.bodyVersion)
                )
            }
            // 此处走生产 Room.builder 注册的 1→2→3 链，而非 helper 显式代跑。
            val db = NoteDatabase.open(ctx, name)
            try {
                assertEquals(3, db.openHelper.readableDatabase.version)
                assertEquals(note, NoteStore(db).get(note.id))
                assertNull(requireNotNull(db.notes().find(note.id)).deletedAt)
                assertEquals(0, db.attachments().countAttachments())
            } finally { db.close() }
        } finally { ctx.deleteDatabase(name) }
    }

    @Test fun linkedAttachmentCannotBeDeletedOrSilentlyExcludedFromV1Backup() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "b3-linked-protection-${UUID.randomUUID()}.db"
        val db = NoteDatabase.open(ctx, name)
        try {
            val store = NoteStore(db)
            store.restore(listOf(note, second))
            val attachment = item()
            db.attachments().insertAttachment(attachment)
            db.attachments().linkToNote(NoteAttachmentEntity(note.id, attachment.id, 0))
            db.attachments().linkToNote(NoteAttachmentEntity(second.id, attachment.id, 0))
            assertEquals(2, db.attachments().countReferences(attachment.id))
            assertEquals(1, db.attachments().countForNote(note.id))
            assertEquals(1, db.attachments().countAttachments())
            assertTrue(runCatching { store.verifyLegacyBackupAvailability() }
                .exceptionOrNull() is LegacyBackupWouldOmitAttachments)
            assertTrue(runCatching { store.snapshot() }
                .exceptionOrNull() is LegacyBackupWouldOmitAttachments)
            assertTrue(store.moveToTrash(note.id, 350))
            assertTrue(runCatching { store.permanentlyDelete(note.id) }
                .exceptionOrNull() is AttachmentCleanupNotReady)
            assertNotNull(db.notes().find(note.id))
            assertEquals(2, db.attachments().countReferences(attachment.id))
            assertEquals(second, store.get(second.id))
            assertTrue(store.restoreFromTrash(note.id))
        } finally { db.close(); ctx.deleteDatabase(name) }
    }
}
