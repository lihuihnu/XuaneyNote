package io.github.lihuihnu.xuaneynote.data

import android.content.Context
import androidx.room.withTransaction
import io.github.lihuihnu.xuaneynote.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

data class BundleRestoreResult(
    val insertedNotes: Int,
    val skippedNotes: Int,
    val conflicts: Int,
    val newAttachments: Int
)

/**
 * B4.2 安全导入/导出后端；已验证的 ZIP 仍要先由用户确认才能执行 restore。
 *
 * 并发附件操作与缓存恢复共用 B3.2 文件 Mutex；Room 内容使用事务取快照，
 * 进入恢复事务前对已有记录和关联再次验证，发现并发修改即整体拒绝并清理本次新文件。
 * 数据库提交成功后保留图片；崩溃留下的孤儿图片交给 B3.2 recover() 处理。
 */
class BundleTransferService(
    private val context: Context,
    private val database: NoteDatabase,
    private val privateFiles: PrivateAttachmentFiles
) {
    private val appContext = context.applicationContext

    private suspend fun snapshot(theme: ThemeMode, timestamp: Long): BundleSnapshot =
        database.withTransaction {
            BundleSnapshot(
                notes = database.notes().snapshot(),
                attachments = database.attachments().allAttachments(),
                links = database.attachments().allLinks(),
                theme = theme,
                exportedAt = timestamp
            )
        }

    /** 不关闭传入的 OutputStream 与已存在数据；底层 ZIP writer 会完成并关闭 ZIP 流。 */
    suspend fun export(
        output: OutputStream, theme: ThemeMode,
        at: Long = System.currentTimeMillis()
    ): BundleSnapshot = privateFiles.withLockedFiles {
        currentCoroutineContext().ensureActive()
        val snap = snapshot(theme, at)
        BundleBackupV2.write(output, snap) { item ->
            FileInputStream(privateFiles.verifiedExistingUnderLock(item))
        }
        snap
    }

    /** 只写到隔离的 cache 子目录，绝不操作 Room/现存图片。 */
    suspend fun inspect(input: InputStream): InspectedBundle = withContext(Dispatchers.IO) {
        BundleBackupV2.inspect(input, appContext.cacheDir)
    }

    private data class RestorePlan(
        val newNotes: List<NoteEntity>,
        val newAttachments: List<AttachmentEntity>,
        val newLinks: List<NoteAttachmentEntity>,
        val reusedAttachments: List<AttachmentEntity>,
        val skipped: Int,
        val conflicts: Int
    )

    private fun sameImage(a: AttachmentEntity, b: AttachmentEntity) =
        a.sha256 == b.sha256 && a.byteSize == b.byteSize &&
            a.mimeType == b.mimeType && a.width == b.width && a.height == b.height

    private fun linksSignature(
        noteId: String, links: List<NoteAttachmentEntity>,
        attachments: Map<String, AttachmentEntity>
    ): List<String> = links.asSequence().filter { it.noteId == noteId }
        .sortedWith(compareBy<NoteAttachmentEntity> { it.sortOrder }.thenBy { it.attachmentId })
        .map { link ->
            val image = requireNotNull(attachments[link.attachmentId])
            listOf(link.sortOrder.toString(), image.sha256, image.mimeType,
                image.byteSize.toString(), image.width.toString(),
                image.height.toString()).joinToString(":")
        }.toList()

    /** 对冲突笔记生成稳定副本；已导入的相同内容不会再次插入。 */
    private fun plan(incoming: BundleSnapshot, existing: BundleSnapshot): RestorePlan {
        val present = existing.notes.associateBy { it.id }.toMutableMap()
        val existingImage = existing.attachments.associateBy { it.id }
        val sourceImage = incoming.attachments.associateBy { it.id }
        val newNotes = mutableListOf<NoteEntity>()
        val mapping = mutableMapOf<String, String>()
        var skipped = 0
        var conflicts = 0

        // 在冲突判断中比较文本和图片的真实内容指纹及展示次序，而非仅看笔记 UUID。
        for (note in incoming.notes.sortedBy { it.id }) {
            val attachmentsKey = linksSignature(note.id, incoming.links, sourceImage)
            val original = present[note.id]
            if (original == null) {
                newNotes += note
                present[note.id] = note
                mapping[note.id] = note.id
                continue
            }
            if (original == note &&
                linksSignature(original.id, existing.links, existingImage) == attachmentsKey) {
                skipped++
                continue
            }
            conflicts++
            val fields = listOf(note.id, note.title, note.body, note.createdAt.toString(),
                note.updatedAt.toString(), note.revision.toString(), note.bodyFormat,
                note.bodyVersion.toString(), note.deletedAt.toString()) + attachmentsKey
            val fingerprint = MessageDigest.getInstance("SHA-256")
                .digest(fields.joinToString("") { it.length.toString() + ":" + it }
                    .toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val suffix = "（恢复副本）"
            var handled = false
            for (attempt in 0..63) {
                val id = UUID.nameUUIDFromBytes(
                    ("XuaneyNote/bundle-v2/restore/" + attempt + "/" + fingerprint)
                        .toByteArray(Charsets.UTF_8)
                ).toString()
                val copy = note.copy(id = id,
                    title = note.title.take(500 - suffix.length) + suffix)
                val known = present[id]
                if (known == null) {
                    newNotes += copy
                    present[id] = copy
                    mapping[note.id] = id
                    handled = true
                    break
                }
                if (known == copy &&
                    linksSignature(id, existing.links, existingImage) == attachmentsKey) {
                    skipped++
                    handled = true
                    break
                }
            }
            check(handled) { "恢复副本 ID 冲突过多" }
        }

        val expectedLinks = incoming.links.filter { it.noteId in mapping }.sortedWith(
            compareBy<NoteAttachmentEntity> { it.noteId }
                .thenBy { it.sortOrder }.thenBy { it.attachmentId })
        val referencedIds = expectedLinks.mapTo(mutableSetOf()) { it.attachmentId }
        val occupied = existingImage.toMutableMap()
        val newAtt = mutableListOf<AttachmentEntity>()
        val reused = mutableListOf<AttachmentEntity>()
        val attIdMap = HashMap<String, String>()

        for (att in incoming.attachments.sortedBy { it.id }.filter { it.id in referencedIds }) {
            var candidateId = att.id
            val own = occupied[candidateId]
            if (own != null && sameImage(own, att)) {
                reused += own
                attIdMap[att.id] = own.id
                continue
            }
            if (own != null) {
                val seed = att.id + ":" + att.sha256 + ":" + att.mimeType
                var found = false
                for (attempt in 0..63) {
                    candidateId = UUID.nameUUIDFromBytes(
                        ("XuaneyNote/bundle-v2/image/" + attempt + "/" + seed)
                            .toByteArray(Charsets.UTF_8)
                    ).toString()
                    val current = occupied[candidateId]
                    if (current == null || sameImage(current, att)) {
                        found = true
                        break
                    }
                }
                check(found) { "图片 ID 冲突过多" }
            }
            val presentAtt = occupied[candidateId]
            if (presentAtt != null) {
                reused += presentAtt
                attIdMap[att.id] = presentAtt.id
            } else {
                val ext = when (att.mimeType) {
                    "image/jpeg" -> "jpg"
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> error("图片类型不支持")
                }
                val new = att.copy(id = candidateId,
                    relativePath = "images/" + candidateId + "." + ext)
                newAtt += new
                occupied[candidateId] = new
                attIdMap[att.id] = candidateId
            }
        }
        val newLinks = expectedLinks.map { old ->
            NoteAttachmentEntity(
                noteId = requireNotNull(mapping[old.noteId]),
                attachmentId = requireNotNull(attIdMap[old.attachmentId]),
                sortOrder = old.sortOrder
            )
        }
        require(newLinks.distinctBy { it.noteId to it.attachmentId }.size == newLinks.size) {
            "多个不同图片映射到了同一笔记关联"
        }
        return RestorePlan(newNotes, newAtt, newLinks, reused.distinctBy { it.id },
            skipped, conflicts)
    }

    /** 恢复仅执行新增操作，不会静默更新/清空用户已有笔记。 */
    suspend fun restore(inspected: InspectedBundle): BundleRestoreResult =
        privateFiles.withLockedFiles {
            currentCoroutineContext().ensureActive()
            val original = snapshot(ThemeMode.SYSTEM, 0)
            val plan = plan(inspected.snapshot, original)
            // 不能把新数据导入后制造出无法再次完整导出的库。
            require(original.notes.size + plan.newNotes.size <= 10_000) {
                "恢复后笔记超出完整备份上限"
            }
            require(original.attachments.size + plan.newAttachments.size <= 512) {
                "恢复后图片超出完整备份上限"
            }
            require(original.links.size + plan.newLinks.size <= 10_000 * 128) {
                "恢复后图片引用超出完整备份上限"
            }
            val textBytes = (original.notes + plan.newNotes).sumOf {
                it.title.toByteArray(Charsets.UTF_8).size.toLong() +
                    it.body.toByteArray(Charsets.UTF_8).size
            }
            require(textBytes <= 8L * 1024 * 1024) {
                "恢复后文本超出本版可完整备份的上限"
            }
            val created = mutableListOf<AttachmentEntity>()
            try {
                for (already in plan.reusedAttachments) {
                    privateFiles.verifiedExistingUnderLock(already)
                }
                for (item in plan.newAttachments) {
                    currentCoroutineContext().ensureActive()
                    val staged = requireNotNull(inspected.imagesByDigest[item.sha256]) {
                        "备份图片缺失"
                    }
                    privateFiles.installVerifiedUnderLock(item, staged)
                    created += item
                }
                currentCoroutineContext().ensureActive()
                database.withTransaction {
                    // 不持有 Room 事务执行文件复制，复制完成后在事务内确认状态未变。
                    check(database.notes().snapshot() == original.notes &&
                        database.attachments().allAttachments() == original.attachments &&
                        database.attachments().allLinks() == original.links) {
                        "恢复期间现有笔记发生变化，请重试；已有内容未被修改"
                    }
                    if (plan.newNotes.isNotEmpty()) database.notes().insert(plan.newNotes)
                    for (att in plan.newAttachments) database.attachments().insertAttachment(att)
                    for (link in plan.newLinks) database.attachments().linkToNote(link)
                }
                BundleRestoreResult(plan.newNotes.size, plan.skipped, plan.conflicts,
                    plan.newAttachments.size)
            } finally {
                // 事务失败或调用方刚好在提交之后取消时只删没有 Room 元数据的新增文件。
                withContext(NonCancellable) {
                    privateFiles.cleanupAbortedUnderLock(created)
                }
            }
        }

    /**
     * 进程被终止时 BundleBackupV2.inspect 的临时 cleartext 缓存可能残留。
     * 下次启动仅清理自己 prefix + 受控目录，不触碰任何用户的图库/外部 SAF 路径。
     */
    suspend fun cleanupInspections(): Int = withContext(Dispatchers.IO) {
        val base = appContext.cacheDir
        var removed = 0
        for (item in base.listFiles().orEmpty()) {
            if (item.name.matches(Regex("xuaney-bundle-v2-[a-zA-Z0-9_-]+")) &&
                item.isDirectory && !Files.isSymbolicLink(item.toPath()) &&
                item.canonicalFile.parentFile == base.canonicalFile) {
                if (item.deleteRecursively()) removed++
            }
        }
        removed
    }
}
