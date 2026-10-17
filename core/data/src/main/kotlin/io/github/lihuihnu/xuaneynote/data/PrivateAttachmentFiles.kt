package io.github.lihuihnu.xuaneynote.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

data class AttachmentRecovery(
    val abandonedStagesRemoved: Int,
    val unreferencedFilesRemoved: Int,
    val missingReferencedFiles: Int,
    val failedCleanups: Int
)

data class AttachmentDeletion(val deleted: Boolean, val pendingFileCleanup: Int)

/**
 * B3.2 私有文件生命周期后端，不开放图片 UI（B4 完整备份之前）。
 *
 * 图片先流式保存到私有 staging、fsync、解码校验，然后改名为 UUID 原图；
 * 再在单个 Room 事务中提交元数据与引用。数据库提交失败删除新原图，
 * 异常中断遗留的孤儿由下次 recover() 清理；数据库引用存在的图片不会被清理。
 * 删除则先更新数据库引用，再执行文件删除，失败可以在下次启动幂等重试。
 *
 * 仅保证同一应用进程的 Mutex 串行化和常规 SQLite 事务，不冒称跨进程/跨文件系统原子性。
 */
class PrivateAttachmentFiles(
    context: Context,
    private val database: NoteDatabase,
    directoryName: String = "xuaney-attachments"
) {
    companion object {
        const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
        const val MAX_ATTACHMENTS_PER_NOTE = 128
        private const val MAX_DIMENSION = 16384
        private const val MAX_PIXELS = 100_000_000L
        private val gate = Mutex()
        private val imagePattern = Regex(
            """images/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|webp)"""
        )
    }

    private val appContext = context.applicationContext
    private val root: File
    private val images: File
    private val staging: File

    init {
        require(directoryName.matches(Regex("[a-z][a-z0-9-]{0,63}")))
        root = File(appContext.filesDir, directoryName)
        images = File(root, "images")
        staging = File(root, "staging")
    }

    private fun ensureDirs() {
        val parent = appContext.filesDir.canonicalFile
        require(!Files.isSymbolicLink(root.toPath())) { "非法图片目录" }
        check(root.isDirectory || root.mkdir()) { "无法创建私有图片目录" }
        require(root.canonicalFile.parentFile == parent) { "图片目录越界" }
        for (dir in listOf(images, staging)) {
            require(!Files.isSymbolicLink(dir.toPath())) { "非法图片子目录" }
            check(dir.isDirectory || dir.mkdir()) { "无法创建私有图片子目录" }
            require(dir.canonicalFile.parentFile == root.canonicalFile) { "图片子目录越界" }
        }
    }

    private fun managedFile(relativePath: String): File {
        require(imagePattern.matches(relativePath)) { "非法图片相对路径" }
        ensureDirs()
        val file = File(root, relativePath)
        require(!Files.isSymbolicLink(file.toPath())) { "禁止图片符号链接" }
        require(file.canonicalFile.parentFile == images.canonicalFile) { "图片路径越界" }
        return file
    }

    private fun digestHex(hash: MessageDigest) =
        hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }

    private suspend fun copyBounded(source: InputStream, target: File): Pair<Long, String> {
        val hash = MessageDigest.getInstance("SHA-256")
        var total = 0L
        var emptyReads = 0
        source.use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) {
                        check(++emptyReads < 128) { "图片来源未返回有效数据" }
                        continue
                    }
                    emptyReads = 0
                    total += n
                    require(total <= MAX_IMAGE_BYTES) { "单张图片超过 20 MiB" }
                    hash.update(buffer, 0, n)
                    output.write(buffer, 0, n)
                }
                require(total > 0) { "图片为空" }
                output.fd.sync()
            }
        }
        return total to digestHex(hash)
    }

    private data class ImageInfo(val mime: String, val extension: String, val width: Int, val height: Int)

    private fun inspectImage(file: File): ImageInfo {
        val header = ByteArray(16)
        val count = FileInputStream(file).use { it.read(header) }
        require(count >= 12) { "图片头过短" }
        val format = when {
            (header[0].toInt() and 255) == 255 &&
                (header[1].toInt() and 255) == 216 &&
                (header[2].toInt() and 255) == 255 -> "image/jpeg" to "jpg"
            header.sliceArray(0..7).contentEquals(
                byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
            ) -> "image/png" to "png"
            String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(header, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp" to "webp"
            else -> throw IllegalArgumentException("只接受 JPEG、PNG 或 WebP")
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth in 1..MAX_DIMENSION && bounds.outHeight in 1..MAX_DIMENSION &&
            bounds.outWidth.toLong() * bounds.outHeight <= MAX_PIXELS) { "图片尺寸非法或过大" }
        require(bounds.outMimeType == format.first) { "图片头与实际格式不一致" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: throw IllegalArgumentException("图片不可解码")
        decoded.recycle()
        return ImageInfo(format.first, format.second, bounds.outWidth, bounds.outHeight)
    }

    /** Picker/SAF 返回的一次性 content URI；只读取原始字节，绝不持久化 URI。 */
    suspend fun importFromUri(noteId: String, uri: Uri): AttachmentEntity {
        require(uri.scheme == "content") { "仅接受系统内容提供方" }
        val input = withContext(Dispatchers.IO) {
            requireNotNull(appContext.contentResolver.openInputStream(uri)) { "图片无法读取" }
        }
        return importStream(noteId, input)
    }

    /** 调用方移交流的所有权，本函数无论成功失败均关闭流。 */
    suspend fun importStream(noteId: String, input: InputStream): AttachmentEntity =
        withContext(Dispatchers.IO) {
            gate.withLock {
                ensureDirs()
                val stage = File.createTempFile("stage-", ".part", staging)
                var committedFile: File? = null
                var candidate: AttachmentEntity? = null
                try {
                    val (size, hash) = copyBounded(input, stage)
                    currentCoroutineContext().ensureActive()
                    val format = inspectImage(stage)
                    val id = UUID.randomUUID().toString()
                    val path = "images/" + id + "." + format.extension
                    val final = managedFile(path)
                    check(!final.exists()) { "原图文件已存在" }
                    check(stage.renameTo(final)) { "无法安全提交原图" }
                    committedFile = final
                    currentCoroutineContext().ensureActive()
                    val record = AttachmentEntity(id, path, format.mime, size, hash,
                        System.currentTimeMillis(), format.width, format.height)
                    candidate = record
                    database.withTransaction {
                        check(database.notes().findActive(noteId) != null) { "只能向正常笔记添加图片" }
                        val count = database.attachments().countForNote(noteId)
                        require(count < MAX_ATTACHMENTS_PER_NOTE) { "单篇笔记图片数量超过限制" }
                        database.attachments().insertAttachment(record)
                        database.attachments().linkToNote(NoteAttachmentEntity(noteId, id, count))
                    }
                    record
                } finally {
                    // 即使调用方恰好在 COMMIT 后取消，也先查询数据库引用，不误删已提交文件。
                    withContext(NonCancellable) {
                        val file = committedFile
                        val entry = candidate
                        if (file != null && entry != null &&
                            database.attachments().findAttachment(entry.id) == null) {
                            file.delete() // 删除失败由进程重启的 recover() 幂等扫尾
                        }
                        stage.delete()
                    }
                }
            }
        }

    /** 将同一原图关联到第二篇正常笔记；不复制原图。 */
    suspend fun linkExisting(noteId: String, attachmentId: String) =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val item = requireNotNull(database.attachments().findAttachment(attachmentId))
                validateStored(item)
                database.withTransaction {
                    check(database.notes().findActive(noteId) != null) { "笔记已删除或不存在" }
                    check(database.attachments().forNote(noteId).none { it.id == attachmentId }) {
                        "图片已关联到此笔记"
                    }
                    val count = database.attachments().countForNote(noteId)
                    require(count < MAX_ATTACHMENTS_PER_NOTE) { "单篇笔记图片数量超过限制" }
                    database.attachments().linkToNote(NoteAttachmentEntity(noteId, attachmentId, count))
                }
            }
        }

    private fun validateStored(item: AttachmentEntity): File {
        val file = managedFile(item.relativePath)
        require(file.isFile && !Files.isSymbolicLink(file.toPath())) { "原图已缺失" }
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        FileInputStream(file).use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                size += n
                require(size <= MAX_IMAGE_BYTES) { "原图超过大小限制" }
                digest.update(buf, 0, n)
            }
        }
        require(size == item.byteSize && digestHex(digest) == item.sha256) {
            "图片字节校验失败"
        }
        return file
    }

    /** B4 备份层可以取得验证过的受控 File，并自行流式读取。 */
    suspend fun verifiedFile(attachmentId: String): File = withContext(Dispatchers.IO) {
        gate.withLock {
            val item = requireNotNull(database.attachments().findAttachment(attachmentId))
            validateStored(item)
        }
    }

    /** 删除 Room 状态优先于文件操作；中断时残留的无引用文件由 recover() 清理。 */
    suspend fun permanentlyDeleteTrashed(noteId: String): AttachmentDeletion =
        withContext(Dispatchers.IO) {
            gate.withLock {
                ensureDirs()
                val released = database.withTransaction {
                    val note = database.notes().find(noteId)
                    if (note?.deletedAt == null) return@withTransaction null
                    val attachments = database.attachments().forNote(noteId)
                    attachments.forEach { managedFile(it.relativePath) }
                    check(database.notes().permanentlyDeleteTrashed(noteId) == 1)
                    attachments.filter { database.attachments().countReferences(it.id) == 0 }
                        .onEach { check(database.attachments().deleteUnreferenced(it.id) == 1) }
                        .map { it.relativePath }
                }
                if (released == null) AttachmentDeletion(false, 0)
                else {
                    val failures = released.count { relative ->
                        val file = managedFile(relative)
                        file.exists() && !file.delete()
                    }
                    AttachmentDeletion(true, failures)
                }
            }
        }

    /** 只清理私有目录受控文件名；被数据库引用但遗失的文件必须报告。 */
    suspend fun recover(): AttachmentRecovery = withContext(Dispatchers.IO) {
        gate.withLock {
            ensureDirs()
            val references = database.attachments().allAttachments()
            val known = references.map { managedFile(it.relativePath).canonicalPath }.toSet()
            var stages = 0
            var orphans = 0
            var failed = 0
            for (file in staging.listFiles() ?: error("无法枚举暂存目录")) {
                if (file.name.startsWith("stage-") && file.name.endsWith(".part") &&
                    file.isFile && !Files.isSymbolicLink(file.toPath())) {
                    if (file.delete()) stages++ else failed++
                }
            }
            for (file in images.listFiles() ?: error("无法枚举图片目录")) {
                val relative = "images/" + file.name
                if (!imagePattern.matches(relative) || !file.isFile ||
                    Files.isSymbolicLink(file.toPath())) continue
                if (file.canonicalPath !in known) {
                    if (file.delete()) orphans++ else failed++
                }
            }
            val missing = references.count {
                val file = managedFile(it.relativePath)
                !file.isFile || Files.isSymbolicLink(file.toPath())
            }
            AttachmentRecovery(stages, orphans, missing, failed)
        }
    }
}
