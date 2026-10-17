package io.github.lihuihnu.xuaneynote.data

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lihuihnu.xuaneynote.model.Note
import io.github.lihuihnu.xuaneynote.model.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** 完全使用隔离合成数据；不动目标用户默认数据库或系统图库。 */
@RunWith(AndroidJUnit4::class)
class BundleTransferServiceTest {
    private val first = Note("00000000-0000-0000-0000-000000000601",
        "共享图片笔记🙂", "中文和原始正文\n继续存在", 100L, 200L, 7L)
    private val second = Note("00000000-0000-0000-0000-000000000602",
        "第二篇 Markdown", "# 每个字仍在", 101L, 201L, 5L, "markdown", 1)

    private fun png(): ByteArray {
        val image = Bitmap.createBitmap(5, 4, Bitmap.Config.ARGB_8888)
        return try {
            val output = ByteArrayOutputStream()
            check(image.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        } finally { image.recycle() }
    }

    private fun dbAndFiles(prefix: String): Triple<NoteDatabase, PrivateAttachmentFiles, String> {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = prefix + "-" + UUID.randomUUID().toString().take(10)
        val db = NoteDatabase.open(ctx, name + ".db")
        return Triple(db, PrivateAttachmentFiles(ctx, db, name), name)
    }

    private fun deleteTemporary(db: NoteDatabase, name: String) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db.close()
        ctx.deleteDatabase(name + ".db")
        File(ctx.filesDir, name).deleteRecursively()
    }

    @Test fun exportedZipRestoresImagesTrashAndSharedLinksIntoEmptyDatabase() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val (sourceDb, sourceFiles, sourceName) = dbAndFiles("b4source")
        val (targetDb, targetFiles, targetName) = dbAndFiles("b4target")
        try {
            val notes = NoteStore(sourceDb)
            notes.restore(listOf(first, second))
            val bytes = png()
            val att = sourceFiles.importStream(first.id, ByteArrayInputStream(bytes))
            sourceFiles.linkExisting(second.id, att.id)
            assertTrue(notes.moveToTrash(second.id, 300L))
            val service = BundleTransferService(ctx, sourceDb, sourceFiles)
            val output = ByteArrayOutputStream()
            val expected = service.export(output, ThemeMode.DARK, at = 400L)
            assertEquals(2, expected.notes.size)
            assertEquals(1, expected.attachments.size)
            assertEquals(2, expected.links.size)
            assertEquals(300L, expected.notes.first { it.id == second.id }.deletedAt)
            val receiving = BundleTransferService(ctx, targetDb, targetFiles)
            receiving.inspect(ByteArrayInputStream(output.toByteArray())).use { bundle ->
                assertEquals(expected, bundle.snapshot)
                val result = receiving.restore(bundle)
                assertEquals(2, result.insertedNotes)
                assertEquals(0, result.conflicts)
                assertEquals(1, result.newAttachments)
                assertEquals(first, NoteStore(targetDb).get(first.id))
                assertNull(NoteStore(targetDb).get(second.id))
                assertEquals(300L, targetDb.notes().find(second.id)?.deletedAt)
                assertEquals(2, targetDb.attachments().countReferences(att.id))
                assertArrayEquals(bytes, targetFiles.verifiedFile(att.id).readBytes())
                val repeat = receiving.restore(bundle)
                assertEquals(0, repeat.insertedNotes)
                assertEquals(2, repeat.skippedNotes)
                assertEquals(0, repeat.newAttachments)
                assertEquals(2, targetDb.attachments().countLinks())
            }
            assertEquals(0, targetFiles.recover().failedCleanups)
        } finally {
            deleteTemporary(sourceDb, sourceName)
            deleteTemporary(targetDb, targetName)
        }
    }

    @Test fun sameIdConflictCreatesStableCopyWithoutOverwritingExistingNote() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val (sdb, sfiles, sn) = dbAndFiles("b4confsrc")
        val (tdb, tfiles, tn) = dbAndFiles("b4confdst")
        try {
            NoteStore(sdb).restore(listOf(first))
            val image = sfiles.importStream(first.id, ByteArrayInputStream(png()))
            val old = first.copy(body = "原有本地笔记，不得被替换", revision = 10)
            NoteStore(tdb).restore(listOf(old))
            val export = ByteArrayOutputStream()
            BundleTransferService(ctx, sdb, sfiles).export(export, ThemeMode.SYSTEM, 999L)
            val receiver = BundleTransferService(ctx, tdb, tfiles)
            receiver.inspect(ByteArrayInputStream(export.toByteArray())).use { bundle ->
                val result = receiver.restore(bundle)
                assertEquals(1, result.conflicts)
                assertEquals(1, result.insertedNotes)
                assertEquals(1, result.newAttachments)
                assertEquals(old, NoteStore(tdb).get(old.id))
                val now = tdb.notes().snapshot()
                assertEquals(2, now.size)
                val copy = now.single { it.id != old.id }
                assertTrue(copy.title.contains("恢复副本"))
                assertEquals(first.body, copy.body)
                assertEquals(first.revision, copy.revision)
                assertEquals(listOf(image.sha256), tdb.attachments().forNote(copy.id).map { it.sha256 })
                val secondImport = receiver.restore(bundle)
                assertEquals(0, secondImport.insertedNotes)
                assertEquals(1, secondImport.skippedNotes)
                assertEquals(1, secondImport.conflicts)
                assertEquals(1, tdb.attachments().countAttachments())
                assertEquals(2, tdb.notes().snapshot().size)
            }
        } finally {
            deleteTemporary(sdb, sn)
            deleteTemporary(tdb, tn)
        }
    }

    @Test fun corruptedAfterInspectionCannotModifyExistingDatabase() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val (sdb, sfiles, sn) = dbAndFiles("b4tamper")
        val (tdb, tfiles, tn) = dbAndFiles("b4tgt")
        try {
            NoteStore(sdb).restore(listOf(first))
            sfiles.importStream(first.id, ByteArrayInputStream(png()))
            val current = first.copy(id = "00000000-0000-0000-0000-000000000699",
                title = "已有资料", body = "绝不能因损坏备份而消失")
            NoteStore(tdb).restore(listOf(current))
            val out = ByteArrayOutputStream()
            BundleTransferService(ctx, sdb, sfiles).export(out, ThemeMode.DARK, 2000L)
            val receiver = BundleTransferService(ctx, tdb, tfiles)
            receiver.inspect(ByteArrayInputStream(out.toByteArray())).use { inspected ->
                val file = inspected.imagesByDigest.values.single()
                file.writeBytes("image modified after inspect".toByteArray(Charsets.UTF_8))
                assertTrue(runCatching { receiver.restore(inspected) }.isFailure)
            }
            assertEquals(listOf(current), tdb.notes().snapshot().map { it.model() })
            assertEquals(0, tdb.attachments().countAttachments())
            assertEquals(0, tdb.attachments().countLinks())
            assertEquals(0, tfiles.recover().unreferencedFilesRemoved)
            assertEquals(0, tfiles.recover().failedCleanups)
        } finally {
            deleteTemporary(sdb, sn)
            deleteTemporary(tdb, tn)
        }
    }

    @Test fun noteDeletionStateConflictRemainsSeparateAndIdempotent() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val (sdb, sfiles, sn) = dbAndFiles("b4trashsrc")
        val (tdb, tfiles, tn) = dbAndFiles("b4trashdst")
        try {
            NoteStore(sdb).restore(listOf(first))
            assertTrue(NoteStore(sdb).moveToTrash(first.id, 333L))
            NoteStore(tdb).restore(listOf(first))
            val service = BundleTransferService(ctx, sdb, sfiles)
            val raw = ByteArrayOutputStream()
            service.export(raw, ThemeMode.SYSTEM, 444L)
            val receiver = BundleTransferService(ctx, tdb, tfiles)
            receiver.inspect(ByteArrayInputStream(raw.toByteArray())).use { bundle ->
                assertEquals(1, receiver.restore(bundle).insertedNotes)
                assertEquals(first, NoteStore(tdb).get(first.id))
                val notes = tdb.notes().snapshot()
                assertEquals(2, notes.size)
                val deletedCopy = notes.single { it.id != first.id }
                assertEquals(333L, deletedCopy.deletedAt)
                assertEquals(first.body, deletedCopy.body)
                assertEquals(0, receiver.restore(bundle).insertedNotes)
            }
        } finally {
            deleteTemporary(sdb, sn)
            deleteTemporary(tdb, tn)
        }
    }

    @Test fun interruptedInspectionCacheIsCleanedWithoutTouchingOtherFiles() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val (db, files, name) = dbAndFiles("b4clean")
        val foreign = File(ctx.cacheDir, "not-xuaney-" + UUID.randomUUID().toString())
        try {
            val dir = File(ctx.cacheDir, "xuaney-bundle-v2-" + UUID.randomUUID().toString())
            check(dir.mkdir())
            File(dir, "abandoned-data.txt").writeText("synthetic")
            foreign.writeText("do not delete")
            val service = BundleTransferService(ctx, db, files)
            assertTrue(service.cleanupInspections() >= 1)
            assertFalse(dir.exists())
            assertTrue(foreign.exists())
        } finally {
            foreign.delete()
            deleteTemporary(db, name)
        }
    }
}
