package io.github.lihuihnu.xuaneynote.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lihuihnu.xuaneynote.model.Note
import io.github.lihuihnu.xuaneynote.model.ThemeMode
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DataContractTest {
    private val note = Note("00000000-0000-0000-0000-000000000001", "合成测试", "中文\n🙂\tquote: \" /", 1, 2, 1)
    private val backup = NoteBackup(listOf(note), ThemeMode.DARK, 1234)
    @Test fun backupRoundtripAndEmptyBackup() {
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
        val empty = backup.copy(notes = emptyList())
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty)))
    }
    @Test fun corruptUnknownVersionAndTruncatedBackupAreRejected() {
        val bytes = BackupCodec.encode(backup)
        val corrupt = bytes.toString(Charsets.UTF_8).replace("中文", "损坏").toByteArray()
        assertTrue(runCatching { BackupCodec.decode(corrupt) }.isFailure)
        assertTrue(runCatching { BackupCodec.decode(bytes.copyOf(bytes.size / 2)) }.isFailure)
        val unknown = JSONObject(bytes.toString(Charsets.UTF_8)).put("version", 2).toString().toByteArray()
        assertTrue(runCatching { BackupCodec.decode(unknown) }.isFailure)
        assertTrue(runCatching { BackupCodec.decode(byteArrayOf(0x80.toByte())) }.isFailure)
    }
    @Test fun duplicateIdsAndOversizedInputAreRejected() {
        assertTrue(runCatching { BackupCodec.encode(backup.copy(notes = listOf(note, note))) }.isFailure)
        assertTrue(runCatching { BackupCodec.read(ByteArrayInputStream(ByteArray(BackupCodec.MAX_BYTES + 1))) }.isFailure)
    }
    @Test fun databaseReopenAndAtomicRestore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "contract-${UUID.randomUUID()}.db"
        var database = NoteDatabase.open(context, name)
        try {
            var store = NoteStore(database)
            store.restore(listOf(note))
            val edited = note.copy(body = "已提交的正文", revision = 2)
            store.save(edited)
            database.close()
            database = NoteDatabase.open(context, name)
            store = NoteStore(database)
            assertEquals(edited, store.get(note.id))
            val before = store.snapshot()
            assertTrue(runCatching { store.restore(listOf(note.copy(id = UUID.randomUUID().toString()), note.copy(updatedAt = -1))) }.isFailure)
            assertEquals(before, store.snapshot())
            val result = store.restore(backup.notes)
            assertEquals(1, result.inserts.size)
            assertEquals(edited, store.get(note.id))
            assertTrue(store.restore(backup.notes).inserts.isEmpty())
            assertTrue(runCatching { store.save(note) }.isFailure)
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
