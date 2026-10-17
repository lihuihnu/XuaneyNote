package io.github.lihuihnu.xuaneynote.data

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lihuihnu.xuaneynote.model.Note
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * B3.2 合成 PNG/JPEG/WebP 测试，不经用户图册、不使用生产笔记。
 * 每个测试独立数据库与 app 私有附件目录。
 */
@RunWith(AndroidJUnit4::class)
class PrivateAttachmentFilesTest {
    private val first = Note("00000000-0000-0000-0000-000000000401", "合成图片笔记",
        "原笔记正文不因附件损坏", 100, 200, 3)
    private val second = Note("00000000-0000-0000-0000-000000000402", "第二篇",
        "共享引用不允许误删", 100, 201, 1)

    private fun picture(format: Bitmap.CompressFormat): ByteArray {
        val bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        return try {
            val output = ByteArrayOutputStream()
            check(bitmap.compress(format, 95, output))
            output.toByteArray()
        } finally { bitmap.recycle() }
    }

    private fun context() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun streamedFormatsAndReopenRetainExactPrivateBytes() = runBlocking {
        val ctx = context()
        val id = UUID.randomUUID().toString().take(10)
        val name = "b3-stream-$id.db"
        val folder = "b3-stream-$id"
        var db = NoteDatabase.open(ctx, name)
        try {
            NoteStore(db).restore(listOf(first))
            val store = PrivateAttachmentFiles(ctx, db, folder)
            @Suppress("DEPRECATION")
            val inputs = listOf(
                "image/png" to picture(Bitmap.CompressFormat.PNG),
                "image/jpeg" to picture(Bitmap.CompressFormat.JPEG),
                "image/webp" to picture(Bitmap.CompressFormat.WEBP)
            )
            val produced = inputs.map { (expectedMime, bytes) ->
                val saved = store.importStream(first.id, ByteArrayInputStream(bytes))
                assertEquals(expectedMime, saved.mimeType)
                assertEquals(bytes.size.toLong(), saved.byteSize)
                assertEquals("images/", saved.relativePath.take(7))
                assertTrue(saved.relativePath.contains(saved.id))
                assertEquals(4, saved.width)
                assertEquals(3, saved.height)
                val file = store.verifiedFile(saved.id)
                assertArrayEquals(bytes, file.readBytes())
                saved
            }
            assertEquals(3, db.attachments().countAttachments())
            assertEquals(3, db.attachments().forNote(first.id).size)
            db.close()
            db = NoteDatabase.open(ctx, name)
            val reopened = PrivateAttachmentFiles(ctx, db, folder)
            val recovery = reopened.recover()
            assertEquals(0, recovery.missingReferencedFiles)
            assertEquals(0, recovery.unreferencedFilesRemoved)
            assertEquals(0, recovery.failedCleanups)
            assertEquals(produced.map { it.id }, db.attachments().forNote(first.id).map { it.id })
            produced.forEach { assertTrue(reopened.verifiedFile(it.id).isFile) }
            assertEquals(first, NoteStore(db).get(first.id))
            assertTrue(runCatching { NoteStore(db).snapshot() }
                .exceptionOrNull() is LegacyBackupWouldOmitAttachments)
        } finally {
            db.close()
            ctx.deleteDatabase(name)
            File(ctx.filesDir, folder).deleteRecursively()
        }
    }

    @Test fun invalidTruncatedOversizeAndMissingNoteNeverPersistMetadata() = runBlocking {
        val ctx = context()
        val id = UUID.randomUUID().toString().take(10)
        val name = "b3-invalid-$id.db"
        val folder = "b3-invalid-$id"
        val db = NoteDatabase.open(ctx, name)
        try {
            NoteStore(db).restore(listOf(first))
            val store = PrivateAttachmentFiles(ctx, db, folder)
            val valid = picture(Bitmap.CompressFormat.PNG)
            for (bytes in listOf(
                byteArrayOf(1, 2, 3, 4, 5, 6),
                valid.copyOfRange(0, 12)
            )) {
                assertTrue(runCatching { store.importStream(first.id, ByteArrayInputStream(bytes)) }.isFailure)
            }
            assertTrue(runCatching {
                store.importStream("00000000-0000-0000-0000-000000000499",
                    ByteArrayInputStream(valid))
            }.isFailure)
            val tooLarge = object : InputStream() {
                var left = PrivateAttachmentFiles.MAX_IMAGE_BYTES + 1
                override fun read(): Int {
                    if (left <= 0) return -1
                    left--
                    return 65
                }
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    if (left <= 0) return -1
                    val count = minOf(left, length.toLong()).toInt()
                    java.util.Arrays.fill(bytes, offset, offset + count, 65.toByte())
                    left -= count
                    return count
                }
            }
            assertTrue(runCatching { store.importStream(first.id, tooLarge) }.isFailure)
            assertEquals(0, db.attachments().countAttachments())
            val cleanup = store.recover()
            assertEquals(0, cleanup.missingReferencedFiles)
            assertEquals(0, cleanup.unreferencedFilesRemoved)
            assertEquals(0, cleanup.failedCleanups)
            assertEquals(first, NoteStore(db).get(first.id))
        } finally {
            db.close()
            ctx.deleteDatabase(name)
            File(ctx.filesDir, folder).deleteRecursively()
        }
    }

    @Test fun sharedReferenceSurvivesFirstDeleteAndFinalFileIsRemoved() = runBlocking {
        val ctx = context()
        val id = UUID.randomUUID().toString().take(10)
        val name = "b3-shared-$id.db"
        val folder = "b3-shared-$id"
        val db = NoteDatabase.open(ctx, name)
        try {
            val notes = NoteStore(db)
            notes.restore(listOf(first, second))
            val store = PrivateAttachmentFiles(ctx, db, folder)
            val png = picture(Bitmap.CompressFormat.PNG)
            val att = store.importStream(first.id, ByteArrayInputStream(png))
            val localFile = store.verifiedFile(att.id)
            store.linkExisting(second.id, att.id)
            assertEquals(2, db.attachments().countReferences(att.id))
            assertTrue(runCatching { store.linkExisting(second.id, att.id) }.isFailure)
            assertTrue(notes.moveToTrash(first.id, 500))
            // B3.1 NoteStore 的保守接口仍拒绝图片，以免 B2 UI 在没有完整备份时误删。
            assertTrue(runCatching { notes.permanentlyDelete(first.id) }
                .exceptionOrNull() is AttachmentCleanupNotReady)
            assertEquals(AttachmentDeletion(true, 0), store.permanentlyDeleteTrashed(first.id))
            assertTrue(localFile.isFile)
            assertEquals(1, db.attachments().countReferences(att.id))
            assertNotNull(db.attachments().findAttachment(att.id))
            assertEquals(second, notes.get(second.id))
            assertTrue(notes.moveToTrash(second.id, 501))
            val finalDelete = store.permanentlyDeleteTrashed(second.id)
            assertTrue(finalDelete.deleted)
            assertEquals(0, finalDelete.pendingFileCleanup)
            assertNull(db.attachments().findAttachment(att.id))
            assertFalse(localFile.exists())
            assertFalse(store.permanentlyDeleteTrashed(second.id).deleted)
            assertEquals(0, db.attachments().countAttachments())
            assertEquals(0, store.recover().failedCleanups)
        } finally {
            db.close()
            ctx.deleteDatabase(name)
            File(ctx.filesDir, folder).deleteRecursively()
        }
    }

    @Test fun restartRecoveryRemovesOnlyRecognizedOrphansAndReportsMissingFiles() = runBlocking {
        val ctx = context()
        val id = UUID.randomUUID().toString().take(10)
        val name = "b3-recover-$id.db"
        val folder = "b3-recover-$id"
        val db = NoteDatabase.open(ctx, name)
        try {
            NoteStore(db).restore(listOf(first))
            val store = PrivateAttachmentFiles(ctx, db, folder)
            val png = picture(Bitmap.CompressFormat.PNG)
            val stored = store.importStream(first.id, ByteArrayInputStream(png))
            assertEquals(0, store.recover().missingReferencedFiles)
            val root = File(ctx.filesDir, folder)
            val orphan = File(root, "images/00000000-0000-0000-0000-000000000497.png")
            orphan.writeBytes(png)
            val staging = File(root, "staging/stage-abandoned.part")
            staging.writeBytes(png)
            val unknown = File(root, "images/unmanaged-foreign.txt")
            unknown.writeText("leave untouched")
            val recovered = PrivateAttachmentFiles(ctx, db, folder).recover()
            assertEquals(1, recovered.abandonedStagesRemoved)
            assertEquals(1, recovered.unreferencedFilesRemoved)
            assertEquals(0, recovered.missingReferencedFiles)
            assertFalse(orphan.exists())
            assertFalse(staging.exists())
            assertTrue(unknown.exists())
            assertTrue(store.verifiedFile(stored.id).isFile)
            assertEquals(1, db.attachments().countAttachments())
            // 物理字节丢失只报告异常，不能把数据库里原有的附件引用凭空清除。
            assertTrue(store.verifiedFile(stored.id).delete())
            assertEquals(1, store.recover().missingReferencedFiles)
            assertNotNull(db.attachments().findAttachment(stored.id))
        } finally {
            db.close()
            ctx.deleteDatabase(name)
            File(ctx.filesDir, folder).deleteRecursively()
        }
    }

    @Test fun unlinkSharedImageKeepsOtherNoteAndLastLinkDeletesFile() = runBlocking {
        val ctx = context()
        val id = UUID.randomUUID().toString().take(10)
        val name = "b3-unlink-$id.db"
        val folder = "b3-unlink-$id"
        val db = NoteDatabase.open(ctx, name)
        try {
            NoteStore(db).restore(listOf(first, second))
            val store = PrivateAttachmentFiles(ctx, db, folder)
            val item = store.importStream(first.id, ByteArrayInputStream(picture(Bitmap.CompressFormat.PNG)))
            val file = store.verifiedFile(item.id)
            store.linkExisting(second.id, item.id)
            assertEquals(AttachmentDeletion(true, 0), store.unlink(first.id, item.id))
            assertTrue(file.exists())
            assertEquals(1, db.attachments().countReferences(item.id))
            assertFalse(store.unlink(first.id, item.id).deleted)
            assertEquals(AttachmentDeletion(true, 0), store.unlink(second.id, item.id))
            assertFalse(file.exists())
            assertNull(db.attachments().findAttachment(item.id))
            assertEquals(first, NoteStore(db).get(first.id))
            assertEquals(second, NoteStore(db).get(second.id))
        } finally {
            db.close()
            ctx.deleteDatabase(name)
            File(ctx.filesDir, folder).deleteRecursively()
        }
    }

    @Test fun unsafeMetadataPathCannotEscapePrivateDirectory() = runBlocking {
        val ctx = context()
        val id = UUID.randomUUID().toString().take(10)
        val name = "b3-path-$id.db"
        val folder = "b3-path-$id"
        val db = NoteDatabase.open(ctx, name)
        try {
            val store = PrivateAttachmentFiles(ctx, db, folder)
            val fake = AttachmentEntity("00000000-0000-0000-0000-000000000498",
                "../../outside.jpg", "image/jpeg", 10, "b".repeat(64), 1, null, null)
            db.attachments().insertAttachment(fake)
            assertTrue(runCatching { store.verifiedFile(fake.id) }.isFailure)
            assertTrue(runCatching { store.recover() }.isFailure)
            assertEquals(fake, db.attachments().findAttachment(fake.id))
        } finally {
            db.close()
            ctx.deleteDatabase(name)
            File(ctx.filesDir, folder).deleteRecursively()
        }
    }
}
