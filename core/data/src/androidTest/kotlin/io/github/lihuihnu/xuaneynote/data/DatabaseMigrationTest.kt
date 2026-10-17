package io.github.lihuihnu.xuaneynote.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.lihuihnu.xuaneynote.model.Note
import io.github.lihuihnu.xuaneynote.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** 使用 PR-A 导出的 1.json 创建旧数据库，而不是手写一个相似的表。 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NoteDatabase::class.java
    )

    private val prARecords = listOf(
        Note("00000000-0000-0000-0000-000000000201", "PR-A 中文笔记🙂",
            "原有正文\n第二行/🙂", 1710000000100L, 1710000000200L, 7L),
        Note("00000000-0000-0000-0000-000000000202", "markdown 原笔记",
            "# 旧正文\n**保留格式**", 1710000000300L, 1710000000400L, 13L, "markdown", 1),
    )

    @Test
    fun prAV1SchemaMigratesWithoutChangingNotesOrPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "pr-a-upgrade-${UUID.randomUUID()}.db"
        val prefs = UserPreferences(context)
        val previousTheme = prefs.theme.first()
        val previousSelection = prefs.selectedNote.first()
        try {
            prefs.setTheme(ThemeMode.DARK)
            prefs.select(prARecords[0].id)

            helper.createDatabase(name, 1).use { original ->
                // 对原版 PR-A 的真实 schema 插入数据，包含中文、Emoji、Markdown 与非零修订。
                for (note in prARecords) {
                    original.execSQL(
                        "INSERT INTO `notes` " +
                            "(`id`, `title`, `body`, `createdAt`, `updatedAt`, " +
                            "`revision`, `bodyFormat`, `bodyVersion`) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(note.id, note.title, note.body, note.createdAt, note.updatedAt,
                            note.revision, note.bodyFormat, note.bodyVersion)
                    )
                }
            }

            helper.runMigrationsAndValidate(name, 2, true, DatabaseMigrations.MIGRATION_1_2).use { upgraded ->
                upgraded.query(
                    "SELECT `id`, `title`, `body`, `createdAt`, `updatedAt`, " +
                        "`revision`, `bodyFormat`, `bodyVersion`, `deletedAt` " +
                        "FROM `notes` ORDER BY `id` ASC"
                ).use { cursor ->
                    assertEquals(prARecords.size, cursor.count)
                    prARecords.sortedBy { it.id }.forEach { note ->
                        assertTrue(cursor.moveToNext())
                        assertEquals(note.id, cursor.getString(0))
                        assertEquals(note.title, cursor.getString(1))
                        assertEquals(note.body, cursor.getString(2))
                        assertEquals(note.createdAt, cursor.getLong(3))
                        assertEquals(note.updatedAt, cursor.getLong(4))
                        assertEquals(note.revision, cursor.getLong(5))
                        assertEquals(note.bodyFormat, cursor.getString(6))
                        assertEquals(note.bodyVersion, cursor.getInt(7))
                        assertTrue("迁移后的旧笔记必须保持正常状态", cursor.isNull(8))
                    }
                }
            }

            val firstOpen = NoteDatabase.open(context, name)
            try {
                val store = NoteStore(firstOpen)
                assertEquals(prARecords.sortedBy { it.id }, store.snapshot())
                assertEquals(prARecords[0], store.get(prARecords[0].id))
                val edited = prARecords[0].copy(
                    body = "v2 中继续编辑，原 ID 和时间保持不变",
                    updatedAt = prARecords[0].updatedAt + 1,
                    revision = prARecords[0].revision + 1
                )
                store.save(edited)
                assertEquals(edited, store.get(edited.id))
                assertTrue(runCatching { store.save(prARecords[0]) }.isFailure)
            } finally {
                firstOpen.close()
            }
            val reopened = NoteDatabase.open(context, name)
            try {
                assertEquals("v2 中继续编辑，原 ID 和时间保持不变",
                    NoteStore(reopened).get(prARecords[0].id)?.body)
                assertEquals(prARecords[1], NoteStore(reopened).get(prARecords[1].id))
                assertEquals(ThemeMode.DARK, prefs.theme.first())
                assertEquals(prARecords[0].id, prefs.selectedNote.first())
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
            prefs.setTheme(previousTheme)
            prefs.select(previousSelection)
        }
    }

    @Test
    fun frozenPrAV1BackupRestoresIntoV2Idempotently() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 合成的 PR-A v1 格式快照，SHA 和 payload_bytes 已冻结；并非真实用户资料。
        val bytes = instrument.context.assets.open("pr_a_v1_export.xuaney.json")
            .use { it.readBytes() }
        val exported = BackupCodec.decode(bytes)
        assertEquals(2, exported.notes.size)
        assertEquals(ThemeMode.DARK, exported.theme)
        assertEquals("旧版合成日记", exported.notes[0].title)

        val name = "pr-a-backup-${UUID.randomUUID()}.db"
        val database = NoteDatabase.open(context, name)
        try {
            val store = NoteStore(database)
            val first = store.restore(exported.notes)
            assertEquals(2, first.inserts.size)
            assertEquals(exported.notes.sortedBy { it.id }, store.snapshot())
            val second = store.restore(exported.notes)
            assertTrue(second.inserts.isEmpty())
            assertEquals(2, second.skipped)
            assertEquals(exported.notes.sortedBy { it.id }, store.snapshot())

            // v1 校验失败不能进入数据库事务，不能影响已保存笔记。
            val corrupted = bytes.toString(Charsets.UTF_8)
                .replace("旧版合成日记", "旧版合成日记被篡改").toByteArray(Charsets.UTF_8)
            assertFalse(corrupted.contentEquals(bytes))
            assertTrue(runCatching { BackupCodec.decode(corrupted) }.isFailure)
            assertEquals(exported.notes.sortedBy { it.id }, store.snapshot())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
