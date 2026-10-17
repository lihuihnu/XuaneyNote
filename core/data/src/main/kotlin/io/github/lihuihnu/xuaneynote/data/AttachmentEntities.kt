package io.github.lihuihnu.xuaneynote.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * B3.1 元数据合同；B3.2 私有原始图片由 PrivateAttachmentFiles 管理。
 * 相对路径只可使用 images/<UUID>.<jpg|png|webp>，不会持久化外部 URI。
 */
@Entity(
    tableName = "attachments",
    indices = [Index(value = ["sha256"]), Index(value = ["relativePath"], unique = true)]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val relativePath: String,
    val mimeType: String,
    val byteSize: Long,
    val sha256: String,
    val createdAt: Long,
    val width: Int?,
    val height: Int?,
)

/** 共享图片可以被不同笔记引用；不得凭单篇笔记的删除来擦除所有引用。 */
@Entity(
    tableName = "note_attachments",
    primaryKeys = ["noteId", "attachmentId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AttachmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["attachmentId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["attachmentId"])]
)
data class NoteAttachmentEntity(
    val noteId: String,
    val attachmentId: String,
    val sortOrder: Int
)

@Dao
interface AttachmentDao {
    @Insert suspend fun insertAttachment(item: AttachmentEntity)
    @Insert suspend fun linkToNote(link: NoteAttachmentEntity)
    @Query("SELECT COUNT(*) FROM note_attachments WHERE noteId = :id")
    suspend fun countForNote(id: String): Int
    @Query("SELECT COUNT(*) FROM note_attachments WHERE attachmentId = :id")
    suspend fun countReferences(id: String): Int
    @Query("SELECT COUNT(*) FROM note_attachments")
    suspend fun countLinks(): Int
    @Query("SELECT COUNT(*) FROM attachments")
    suspend fun countAttachments(): Int

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun findAttachment(id: String): AttachmentEntity?

    @Query("SELECT * FROM attachments ORDER BY id ASC")
    suspend fun allAttachments(): List<AttachmentEntity>

    @Query("SELECT * FROM note_attachments ORDER BY noteId ASC, sortOrder ASC, attachmentId ASC")
    suspend fun allLinks(): List<NoteAttachmentEntity>

    @Query("""
        SELECT a.* FROM attachments AS a
        JOIN note_attachments AS l ON l.attachmentId = a.id
        WHERE l.noteId = :noteId
        ORDER BY l.sortOrder ASC, l.attachmentId ASC
    """)
    suspend fun forNote(noteId: String): List<AttachmentEntity>
    
    @Query("""
        SELECT a.* FROM attachments AS a
        JOIN note_attachments AS l ON l.attachmentId = a.id
        WHERE l.noteId = :noteId
        ORDER BY l.sortOrder ASC, l.attachmentId ASC
    """)
    fun observeForNote(noteId: String): Flow<List<AttachmentEntity>>

    @Query("DELETE FROM note_attachments WHERE noteId = :noteId AND attachmentId = :attachmentId")
    suspend fun unlink(noteId: String, attachmentId: String): Int

    @Query("DELETE FROM attachments WHERE id = :id AND " +
        "NOT EXISTS(SELECT 1 FROM note_attachments WHERE attachmentId = :id)")
    suspend fun deleteUnreferenced(id: String): Int
}
