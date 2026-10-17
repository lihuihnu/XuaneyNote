package io.github.lihuihnu.xuaneynote.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lihuihnu.xuaneynote.model.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** 只使用隔离的合成笔记。B2 不新增数据库列或附件表。 */
@RunWith(AndroidJUnit4::class)
class NoteTrashTest {
    private val sample = Note("00000000-0000-0000-0000-000000000211",
        "B2 回收站合成记录", "不可丢失的中文正文🙂", 1, 2, 3)
    private val other = Note("00000000-0000-0000-0000-000000000212",
        "另一篇正常记录", "永久删除不得误伤这条", 2, 3, 0)

    @Test fun softDeleteRestoreAndRevisionFencePreventResurrection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "b2-soft-${UUID.randomUUID()}.db"
        val db = NoteDatabase.open(context, name)
        try {
            val store = NoteStore(db)
            store.restore(listOf(sample, other))
            assertTrue(store.moveToTrash(sample.id, at = 42))
            assertFalse(store.moveToTrash(sample.id, at = 43))
            assertNull(store.get(sample.id))
            assertEquals(listOf(other.id), store.summaries.first().map { it.id })
            assertTrue(store.search("不可丢失").first().isEmpty())
            assertEquals(listOf(sample.id), store.trashSummaries.first().map { it.id })
            val deleted = requireNotNull(db.notes().find(sample.id))
            assertEquals(42L, deleted.deletedAt)
            assertEquals(sample.revision + 1, deleted.revision)
            assertEquals(sample.body, deleted.body)
            assertEquals(sample.createdAt, deleted.createdAt)
            assertEquals(sample.updatedAt, deleted.updatedAt)
            assertTrue(runCatching {
                store.save(sample.copy(body = "旧草稿不得复活", revision = sample.revision + 1))
            }.isFailure)

            db.close()
            val reopened = NoteDatabase.open(context, name)
            try {
                val continued = NoteStore(reopened)
                assertEquals(listOf(sample.id), continued.trashSummaries.first().map { it.id })
                assertTrue(continued.restoreFromTrash(sample.id))
                assertFalse(continued.restoreFromTrash(sample.id))
                val recovered = requireNotNull(continued.get(sample.id))
                assertEquals(sample.id, recovered.id)
                assertEquals(sample.body, recovered.body)
                assertEquals(sample.createdAt, recovered.createdAt)
                assertEquals(sample.updatedAt, recovered.updatedAt)
                assertEquals(sample.revision + 2, recovered.revision)
                assertTrue(runCatching {
                    continued.save(sample.copy(body = "迟到的自动保存", revision = sample.revision + 1))
                }.isFailure)
                val fresh = recovered.copy(body = "重新编辑成功", revision = recovered.revision + 1)
                continued.save(fresh)
                assertEquals(fresh, continued.get(sample.id))
                assertEquals(2, continued.summaries.first().size)
            } finally { reopened.close() }
        } finally {
            if (db.isOpen) db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun permanentDeleteOnlyAcceptsTrashedRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "b2-permanent-${UUID.randomUUID()}.db"
        val db = NoteDatabase.open(context, name)
        try {
            val store = NoteStore(db)
            store.restore(listOf(sample, other))
            assertFalse(store.permanentlyDelete(sample.id))
            assertEquals(sample, store.get(sample.id))
            assertTrue(store.moveToTrash(sample.id, 40))
            assertTrue(store.permanentlyDelete(sample.id))
            assertFalse(store.permanentlyDelete(sample.id))
            assertFalse(store.restoreFromTrash(sample.id))
            assertNull(db.notes().find(sample.id))
            assertEquals(other, store.get(other.id))
            assertTrue(store.trashSummaries.first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun oldBackupCannotSilentlyOmitTrashAndImportPreservesDeletedRecord() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "b2-backup-${UUID.randomUUID()}.db"
        val db = NoteDatabase.open(context, name)
        try {
            val store = NoteStore(db)
            store.restore(listOf(sample))
            assertTrue(store.moveToTrash(sample.id, 42))
            assertTrue(runCatching { store.snapshot() }.exceptionOrNull() is LegacyBackupWouldOmitTrash)
            assertNull(store.get(sample.id))
            assertEquals(sample.body, requireNotNull(db.notes().find(sample.id)).body)

            // 用户显式导入 PR-A v1 备份时，保留已删的原记录，另创建可见稳定副本。
            val first = store.restore(listOf(sample))
            assertEquals(1, first.inserts.size)
            assertEquals(1, first.conflicts)
            val copy = first.inserts.single()
            assertNotEquals(sample.id, copy.id)
            assertEquals(sample.body, copy.body)
            assertEquals(copy, store.get(copy.id))
            assertEquals(1, store.restore(listOf(sample)).skipped)
            assertEquals(listOf(sample.id), store.trashSummaries.first().map { it.id })
            assertTrue(runCatching { store.snapshot() }.exceptionOrNull() is LegacyBackupWouldOmitTrash)

            assertTrue(store.restoreFromTrash(sample.id))
            val exported = store.snapshot()
            assertEquals(2, exported.size)
            assertTrue(exported.any { it.id == sample.id })
            assertTrue(exported.any { it.id == copy.id })
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
