package io.github.lihuihnu.xuaneynote.data

import android.content.Context
import androidx.room.*
import io.github.lihuihnu.xuaneynote.model.Note
import io.github.lihuihnu.xuaneynote.model.NoteSummary
import io.github.lihuihnu.xuaneynote.model.RestorePlan
import io.github.lihuihnu.xuaneynote.model.planRestore
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes", indices = [Index(value = ["updatedAt"])])
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long,
    val bodyFormat: String,
    val bodyVersion: Int,
) {
    fun model() = Note(id, title, body, createdAt, updatedAt, revision, bodyFormat, bodyVersion)
    companion object {
        fun from(note: Note) = NoteEntity(note.id, note.title, note.body, note.createdAt,
            note.updatedAt, note.revision, note.bodyFormat, note.bodyVersion)
    }
}

@Dao
interface NoteDao {
    @Query("SELECT id, title, substr(body, 1, 100) AS preview, updatedAt FROM notes ORDER BY updatedAt DESC, id ASC")
    fun observeSummaries(): Flow<List<NoteSummary>>
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun find(id: String): NoteEntity?
    @Query("SELECT * FROM notes ORDER BY id ASC")
    suspend fun snapshot(): List<NoteEntity>
    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int
    @Query("SELECT COALESCE(SUM(length(CAST(title AS BLOB)) + length(CAST(body AS BLOB))), 0) FROM notes")
    suspend fun textBytes(): Long
    @Upsert suspend fun upsert(note: NoteEntity)
    @Insert suspend fun insert(notes: List<NoteEntity>)
}

@Database(entities = [NoteEntity::class], version = 1, exportSchema = true)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    companion object {
        fun open(context: Context, name: String = "xuaneynote.db"): NoteDatabase =
            Room.databaseBuilder(context.applicationContext, NoteDatabase::class.java, name).build()
    }
}

class NoteStore(private val database: NoteDatabase) {
    val summaries = database.notes().observeSummaries()
    suspend fun get(id: String): Note? = database.notes().find(id)?.model()
    suspend fun create(): Note = Note.empty().also { note ->
        database.notes().insert(listOf(NoteEntity.from(note)))
    }
    suspend fun save(note: Note) = database.withTransaction {
        note.validate()
        val old = database.notes().find(note.id)?.model() ?: error("笔记不存在")
        require(old == note || note.revision > old.revision) { "拒绝旧修订覆盖" }
        require(note.createdAt == old.createdAt) { "创建时间不能改变" }
        database.notes().upsert(NoteEntity.from(note))
    }
    suspend fun snapshot(): List<Note> = database.withTransaction {
        requireBackupBudget()
        database.notes().snapshot().map { it.model() }
    }
    private suspend fun requireBackupBudget() {
        require(database.notes().count() <= BackupCodec.MAX_NOTES && database.notes().textBytes() <= BackupCodec.MAX_BYTES) {
            "文本资料超过本阶段整库备份/恢复上限"
        }
    }
    suspend fun restore(notes: List<Note>): RestorePlan = database.withTransaction {
        requireBackupBudget()
        val plan = planRestore(database.notes().snapshot().map { it.model() }, notes)
        database.notes().insert(plan.inserts.map(NoteEntity::from))
        plan
    }
}
