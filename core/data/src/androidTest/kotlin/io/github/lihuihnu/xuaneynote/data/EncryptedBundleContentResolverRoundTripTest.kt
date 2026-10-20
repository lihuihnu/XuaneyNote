package io.github.lihuihnu.xuaneynote.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lihuihnu.xuaneynote.model.Note
import io.github.lihuihnu.xuaneynote.model.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** MediaStore synthetic provider test. Not a substitute for human SAF CreateDocument. */
@RunWith(AndroidJUnit4::class)
class EncryptedBundleContentResolverRoundTripTest {
    @Test fun systemProviderCloseAndReopenAuthenticatesEncryptedFullSnapshot() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "b5-saf-" + UUID.randomUUID().toString()
        val db = NoteDatabase.open(ctx, "$name.db")
        val files = PrivateAttachmentFiles(ctx, db, name)
        val raw = BundleTransferService(ctx, db, files)
        val crypt = EncryptedBundleTransferService(ctx, raw)
        val resolver = ctx.contentResolver
        val password = "AES-256-Synthetic-2026-🧪".toCharArray()
        val uri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.xuaney.aes.zip")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        ) ?: error("System provider rejected synthetic test file")
        try {
            val note = Note("00000000-0000-0000-0000-000000000861",
                "合成的加密备份文件", "关闭、读取并校验后才算成功", 100L, 200L, 1L)
            NoteStore(db).restore(listOf(note))
            val snapshot = requireNotNull(resolver.openOutputStream(uri, "wt")).use { stream ->
                crypt.export(stream, ThemeMode.LIGHT, password, at = 865L)
            }
            val verified = requireNotNull(resolver.openInputStream(uri)).use { input ->
                crypt.inspect(input, password)
            }
            verified.use { assertEquals(snapshot, it.snapshot) }
            assertEquals(listOf(note), db.notes().snapshot().map { it.model() })
            val wrong = "Definitely-Wrong-Synthetic-2026".toCharArray()
            try {
                assertTrue(runCatching {
                    requireNotNull(resolver.openInputStream(uri)).use { input ->
                        crypt.inspect(input, wrong).close()
                    }
                }.isFailure)
            } finally { wrong.fill('\u0000') }
            assertEquals(listOf(note), db.notes().snapshot().map { it.model() })
        } finally {
            password.fill('\u0000')
            resolver.delete(uri, null, null)
            db.close()
            ctx.deleteDatabase("$name.db")
            java.io.File(ctx.filesDir, name).deleteRecursively()
            crypt.cleanupOrphans()
        }
    }
}
