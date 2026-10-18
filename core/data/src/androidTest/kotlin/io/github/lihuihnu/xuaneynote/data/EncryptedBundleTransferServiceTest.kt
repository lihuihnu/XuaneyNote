package io.github.lihuihnu.xuaneynote.data

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lihuihnu.xuaneynote.data.b5.Zip4jInteropProbe
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

/** B5.2 only: encrypted backend is NOT exposed to the user until B5.3. */
@RunWith(AndroidJUnit4::class)
class EncryptedBundleTransferServiceTest {
    private val first = Note("00000000-0000-0000-0000-000000000851",
        "加密合成笔记🙂", "不应在预览前写回\n原始内容", 100L, 200L, 7L)
    private val second = Note("00000000-0000-0000-0000-000000000852",
        "回收站中的合成笔记", "图片可以共享，不可错误清理", 101L, 201L, 3L)

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private data class Sandbox(
        val name: String,
        val db: NoteDatabase,
        val files: PrivateAttachmentFiles,
        val bundle: BundleTransferService,
        val encrypted: EncryptedBundleTransferService
    ) {
        fun close(ctx: Context) {
            db.close()
            ctx.deleteDatabase(name + ".db")
            File(ctx.filesDir, name).deleteRecursively()
        }
    }

    private fun make(ctx: Context): Sandbox {
        val name = "b5-backend-" + UUID.randomUUID().toString().take(12)
        val database = NoteDatabase.open(ctx, name + ".db")
        val files = PrivateAttachmentFiles(ctx, database, name)
        val bundle = BundleTransferService(ctx, database, files)
        return Sandbox(name, database, files, bundle,
            EncryptedBundleTransferService(ctx, bundle))
    }

    private fun png(): ByteArray {
        val bmp = Bitmap.createBitmap(6, 5, Bitmap.Config.ARGB_8888)
        try {
            val stream = ByteArrayOutputStream()
            check(bmp.compress(Bitmap.CompressFormat.PNG, 100, stream))
            return stream.toByteArray()
        } finally { bmp.recycle() }
    }

    @Test fun encryptedB4SnapshotWithImageAndTrashDoesNotWriteBeforeConfirm() = runBlocking {
        val ctx = context()
        val from = make(ctx)
        val to = make(ctx)
        val password = "B5测试🧪-Long-Password-2026".toCharArray()
        try {
            NoteStore(from.db).restore(listOf(first, second))
            val bytes = png()
            val image = from.files.importStream(first.id, ByteArrayInputStream(bytes))
            from.files.linkExisting(second.id, image.id)
            assertTrue(NoteStore(from.db).moveToTrash(second.id, 333L))

            val encrypted = ByteArrayOutputStream()
            val expected = from.encrypted.export(encrypted, ThemeMode.DARK, password, at = 444L)
            assertTrue(encrypted.size() > 100)
            assertFalse(encrypted.toString("ISO-8859-1").contains("notes.json"))
            assertEquals(2, expected.notes.size)
            assertEquals(2, expected.links.size)

            // A verified preview never modifies the receiving Room database.
            to.encrypted.inspect(ByteArrayInputStream(encrypted.toByteArray()), password).use { inspected ->
                assertEquals(expected, inspected.snapshot)
                assertEquals(0, to.db.notes().count())
                assertEquals(0, to.db.attachments().countAttachments())
                val result = to.encrypted.restore(inspected) // B5.3 UI will invoke only after user confirms.
                assertEquals(2, result.insertedNotes)
                assertEquals(1, result.newAttachments)
                assertEquals(2, to.db.attachments().countReferences(image.id))
                assertEquals(333L, to.db.notes().find(second.id)?.deletedAt)
                assertEquals(first, NoteStore(to.db).get(first.id))
                assertArrayEquals(bytes, to.files.verifiedFile(image.id).readBytes())
                assertEquals(0, to.encrypted.restore(inspected).insertedNotes)
            }
            assertEquals(0, to.files.recover().failedCleanups)
        } finally {
            password.fill('\u0000')
            from.close(ctx)
            to.close(ctx)
        }
    }

    @Test fun incorrectPasswordTamperedMacAndBadOuterFormatLeaveRoomUntouched() = runBlocking {
        val ctx = context()
        val from = make(ctx)
        val to = make(ctx)
        val temp = File(ctx.cacheDir, "b5-neg-" + UUID.randomUUID()).apply { check(mkdir()) }
        val password = "B5🧪-Integrity-Test-2026".toCharArray()
        val wrong = "Wrong-Synthetic-Password-2026".toCharArray()
        try {
            NoteStore(from.db).restore(listOf(first))
            val existing = first.copy(id = "00000000-0000-0000-0000-000000000859",
                body = "接收方原有正文不能被修改")
            NoteStore(to.db).restore(listOf(existing))
            val output = ByteArrayOutputStream()
            from.encrypted.export(output, ThemeMode.LIGHT, password, 999L)
            val original = File(temp, "good.zip").apply { writeBytes(output.toByteArray()) }
            val damaged = File(temp, "mac.zip")
            Zip4jInteropProbe.corruptMac(original, damaged)
            val multi = File(temp, "two.zip")
            val payload = File(temp, "payload.xuaney.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            Zip4jInteropProbe.writeTwoEntries(payload, multi, password)
            val cases = listOf(
                output.toByteArray() to wrong,
                damaged.readBytes() to password,
                multi.readBytes() to password,
                byteArrayOf(0x50, 0x4b, 0x03, 0x04) to password
            )
            for ((bytes, attemptedPassword) in cases) {
                assertTrue(runCatching {
                    to.encrypted.inspect(ByteArrayInputStream(bytes), attemptedPassword).close()
                }.isFailure)
                assertEquals(listOf(existing), to.db.notes().snapshot().map { it.model() })
                assertEquals(0, to.db.attachments().countAttachments())
                assertEquals(0, to.db.attachments().countLinks())
            }

            // Wrong-password/corrupt staging is never converted into a restore.
            val previous = to.db.notes().count()
            assertTrue(to.encrypted.cleanupOrphans() >= 0)
            assertEquals(previous, to.db.notes().count())
        } finally {
            password.fill('\u0000')
            wrong.fill('\u0000')
            temp.deleteRecursively()
            from.close(ctx)
            to.close(ctx)
        }
    }
}
