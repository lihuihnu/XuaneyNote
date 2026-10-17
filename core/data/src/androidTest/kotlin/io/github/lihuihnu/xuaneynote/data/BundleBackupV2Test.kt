package io.github.lihuihnu.xuaneynote.data

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lihuihnu.xuaneynote.model.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * B4.1 在合成图片和临时目录上测试 ZIP 编解码；inspect 绝不修改用户 Room。
 * 真正执行恢复的数据库事务、SAF 界面和加密功能均未开放。
 */
@RunWith(AndroidJUnit4::class)
class BundleBackupV2Test {
    private val first = NoteEntity("00000000-0000-0000-0000-000000000501",
        "标题：地下水🙂", "跨行正文\n仍需要保留", 100, 200, 5, "plain", 1)
    private val trashed = NoteEntity("00000000-0000-0000-0000-000000000502",
        "已回收的手账", "# Markdown\n**记录**", 120, 220, 8, "markdown", 1, 300)
    private val imageId = "00000000-0000-0000-0000-000000000503"

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888)
        return try {
            val out = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
            out.toByteArray()
        } finally { bitmap.recycle() }
    }

    private fun snapshot(bytes: ByteArray): BundleSnapshot {
        val item = AttachmentEntity(imageId, "images/" + imageId + ".png",
            "image/png", bytes.size.toLong(), sha(bytes), 300, 8, 6)
        return BundleSnapshot(listOf(first, trashed), listOf(item),
            listOf(NoteAttachmentEntity(first.id, imageId, 0),
                NoteAttachmentEntity(trashed.id, imageId, 1)), ThemeMode.DARK, 400)
    }

    private fun archive(snapshot: BundleSnapshot, bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        BundleBackupV2.write(out, snapshot) { ByteArrayInputStream(bytes) }
        return out.toByteArray()
    }

    private fun fixtureZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Test fun fullRoundTripPreservesUnicodeTrashAndSharedImageReference() {
        val png = png()
        val original = snapshot(png)
        val packed = archive(original, png)
        val examined = BundleBackupV2.inspect(ByteArrayInputStream(packed), context().cacheDir)
        val local = examined.imagesByDigest[sha(png)]
        try {
            assertEquals(original, examined.snapshot)
            assertEquals(300L, examined.snapshot.notes[1].deletedAt)
            assertEquals(2, examined.snapshot.links.size)
            assertEquals(1, examined.imagesByDigest.size)
            assertNotNull(local)
            assertArrayEquals(png, requireNotNull(local).readBytes())
            assertTrue(local.isFile)
        } finally { examined.close() }
        assertFalse(requireNotNull(local).exists())
    }

    @Test fun emptyArchiveIsValidAndOldFormatRemainsIndependent() {
        val blank = BundleSnapshot(emptyList(), emptyList(), emptyList(), ThemeMode.SYSTEM, 1)
        val zip = archive(blank, byteArrayOf())
        BundleBackupV2.inspect(ByteArrayInputStream(zip), context().cacheDir).use {
            assertEquals(blank, it.snapshot)
            assertTrue(it.imagesByDigest.isEmpty())
        }
        assertTrue(runCatching {
            BackupCodec.decode(zip)
        }.isFailure)
    }

    @Test fun rejectsUnsafePathsUnknownEntriesAndInvalidArchiveVersion() {
        val invalid = fixtureZip(
            "manifest.json" to "{}".toByteArray(),
            "../outside.txt" to "attack".toByteArray(),
            "notes.json" to "{}".toByteArray()
        )
        assertTrue(runCatching {
            BundleBackupV2.inspect(ByteArrayInputStream(invalid), context().cacheDir)
        }.isFailure)
        val unknown = fixtureZip(
            "manifest.json" to """{"format":"XuaneyNote.bundle-backup","version":99}""".toByteArray(),
            "notes.json" to "{}".toByteArray()
        )
        assertTrue(runCatching {
            BundleBackupV2.inspect(ByteArrayInputStream(unknown), context().cacheDir)
        }.isFailure)
        val extra = fixtureZip(
            "manifest.json" to "{}".toByteArray(),
            "notes.json" to "{}".toByteArray(),
            "subdir/unknown.json" to "data".toByteArray()
        )
        assertTrue(runCatching {
            BundleBackupV2.inspect(ByteArrayInputStream(extra), context().cacheDir)
        }.isFailure)
    }

    @Test fun truncatedArchiveAndChangedImageBytesFailBeforeAnyRestore() = runBlocking {
        val ctx = context()
        val bytes = png()
        val packed = archive(snapshot(bytes), bytes)
        assertTrue(runCatching {
            BundleBackupV2.inspect(ByteArrayInputStream(packed.copyOf(packed.size - 24)), ctx.cacheDir)
        }.isFailure)

        val modified = bytes.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 17).toByte() }
        assertTrue(runCatching { archive(snapshot(bytes), modified) }.isFailure)
        assertTrue(runCatching { archive(snapshot(bytes), ByteArray(0)) }.isFailure)

        val dbName = "b4-preserve-" + UUID.randomUUID() + ".db"
        val db = NoteDatabase.open(ctx, dbName)
        try {
            val note = first.model()
            NoteStore(db).restore(listOf(note))
            assertTrue(runCatching {
                BundleBackupV2.inspect(ByteArrayInputStream(packed.copyOf(packed.size / 3)), ctx.cacheDir)
            }.isFailure)
            assertEquals(note, NoteStore(db).get(note.id))
        } finally { db.close(); ctx.deleteDatabase(dbName) }
    }

    @Test fun inconsistentMetadataOrImageDataCannotBePacked() {
        val bytes = png()
        val good = snapshot(bytes)
        val wrong = good.copy(links = good.links.dropLast(1) +
            NoteAttachmentEntity("00000000-0000-0000-0000-000000000599", imageId, 0))
        assertTrue(runCatching { archive(wrong, bytes) }.isFailure)
        assertTrue(runCatching { archive(good.copy(notes = listOf(first, first)), bytes) }.isFailure)
        val outside = good.copy(attachments = good.attachments.map {
            it.copy(relativePath = "../../outside.png")
        })
        assertTrue(runCatching { archive(outside, bytes) }.isFailure)
        val changedHash = good.copy(attachments = good.attachments.map {
            it.copy(sha256 = "f".repeat(64))
        })
        assertTrue(runCatching { archive(changedHash, bytes) }.isFailure)
        val orphan = good.copy(links = emptyList())
        assertTrue(runCatching { archive(orphan, bytes) }.isFailure)
    }

    @Test fun imageWithCorrectHashButInvalidFormatIsRejectedWhenInspecting() {
        val fake = "PNG_is_not_really_an_image".toByteArray(Charsets.UTF_8)
        val forged = snapshot(fake).copy(attachments = snapshot(fake).attachments.map {
            it.copy(width = 8, height = 6)
        })
        val zip = archive(forged, fake)
        assertTrue(runCatching {
            BundleBackupV2.inspect(ByteArrayInputStream(zip), context().cacheDir)
        }.isFailure)
    }

    @Test fun compressedBombWithOversizedNotesEntryIsRejected() {
        // 可压缩的无害合成大条目；低压缩字节不应绕过展开上限。
        val malicious = fixtureZip(
            "manifest.json" to "{}".toByteArray(),
            "notes.json" to ByteArray((BundleBackupV2.MAX_NOTE_BYTES + 1).toInt()) { 65 }
        )
        assertTrue(runCatching {
            BundleBackupV2.inspect(ByteArrayInputStream(malicious), context().cacheDir)
        }.isFailure)
    }
}
