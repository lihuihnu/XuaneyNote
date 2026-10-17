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
    // PR-A 的旧记录迁移后为 NULL；B2 才开放实际删除入口。
    val deletedAt: Long? = null,
) {
    fun model() = Note(id, title, body, createdAt, updatedAt, revision, bodyFormat, bodyVersion)
    companion object {
        fun from(note: Note) = NoteEntity(note.id, note.title, note.body, note.createdAt,
            note.updatedAt, note.revision, note.bodyFormat, note.bodyVersion)
    }
}

@Dao
interface NoteDao {
    @Query("SELECT id, title, substr(body, 1, 100) AS preview, updatedAt FROM notes " +
        "WHERE deletedAt IS NULL ORDER BY updatedAt DESC, id ASC")
    fun observeSummaries(): Flow<List<NoteSummary>>

    // instr 匹配字面连续子串，百分号、下划线和引号不具有 LIKE 通配符含义。
    // SQLite lower() 仅保证英文 ASCII 大小写处理；中文按原文匹配。
    @Query("""
        SELECT id, title, substr(body, 1, 100) AS preview, updatedAt
        FROM notes
        WHERE deletedAt IS NULL AND
          (instr(lower(title), lower(:keyword)) > 0 OR instr(lower(body), lower(:keyword)) > 0)
        ORDER BY updatedAt DESC, id ASC
        LIMIT :limit
    """)
    fun observeMatching(keyword: String, limit: Int): Flow<List<NoteSummary>>
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

@Database(entities = [NoteEntity::class], version = 2, exportSchema = true)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    companion object {
        fun open(context: Context, name: String = "xuaneynote.db"): NoteDatabase =
            Room.databaseBuilder(context.applicationContext, NoteDatabase::class.java, name)
                .addMigrations(DatabaseMigrations.MIGRATION_1_2)
                .build()
    }
}

class NoteStore(private val database: NoteDatabase) {
    companion object {
        const val SEARCH_VISIBLE_LIMIT = 200
        const val SEARCH_MAX_LENGTH = 100
    }
    val summaries = database.notes().observeSummaries()

    fun search(keyword: String): Flow<List<NoteSummary>> {
        val term = keyword.trim()
        require(term.isNotEmpty() && term.length <= SEARCH_MAX_LENGTH) { "搜索关键词无效或过长" }
        // 多查询一条用于判断是否还有更多结果，不谎报检索到的总条数。
        return database.notes().observeMatching(term, SEARCH_VISIBLE_LIMIT + 1)
    }
    suspend fun get(id: String): Note? = database.notes().find(id)?.model()
    suspend fun create(): Note = Note.empty().also { note ->
        database.notes().insert(listOf(NoteEntity.from(note)))
    }
    suspend fun save(note: Note) = database.withTransaction {
        note.validate()
        val oldEntity = database.notes().find(note.id) ?: error("笔记不存在")
        check(oldEntity.deletedAt == null) { "已删除的笔记不能被旧草稿重新保存" }
        val old = oldEntity.model()
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
