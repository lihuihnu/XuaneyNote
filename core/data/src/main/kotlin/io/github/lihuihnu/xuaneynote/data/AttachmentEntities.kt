package io.github.lihuihnu.xuaneynote.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * B3.1 只冻结附件元数据合同；字节文件和 SAF/Photo Picker 写入路径后续单独实现。
 * relativePath 必须是应用私有附件目录下的规范相对路径，禁止 content:// 和绝对路径。
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
}
