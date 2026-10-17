package io.github.lihuihnu.xuaneynote.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.lihuihnu.xuaneynote.model.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** B4.1 的一致逻辑快照，后续 B4.2 再与 Room 事务和文件互斥接线。 */
data class BundleSnapshot(
    val notes: List<NoteEntity>,
    val attachments: List<AttachmentEntity>,
    val links: List<NoteAttachmentEntity>,
    val theme: ThemeMode,
    val exportedAt: Long
)

/**
 * 所有 ZIP 条目和图片字节已校验的暂存包；inspect 不写入任何现有笔记数据库。
 * 关闭后删除临时目录。B4.2 真正提交恢复时需要另行事务/磁盘就绪确认。
 */
class InspectedBundle internal constructor(
    val snapshot: BundleSnapshot,
    val imagesByDigest: Map<String, File>,
    private val temporaryDirectory: File
) : Closeable {
    override fun close() {
        temporaryDirectory.deleteRecursively()
    }
}

/**
 * XuaneyNote.bundle-backup/version=2 的有界可读 ZIP 容器。
 *
 * 仅数据编解码及全量预检，没有 DB restore/SAF UI；哈希用于意外损坏检测，
 * 明文 ZIP + SHA-256 不是密码加密、签名或攻击者身份认证。
 */
object BundleBackupV2 {
    const val FORMAT = "XuaneyNote.bundle-backup"
    const val VERSION = 2
    const val MAX_COMPRESSED_BYTES = 256L * 1024 * 1024
    const val MAX_EXPANDED_BYTES = 512L * 1024 * 1024
    const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
    const val MAX_ATTACHMENTS = 512
    const val MAX_NOTE_BYTES = 24L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 1024L * 1024
    const val MAX_ENTRIES = MAX_ATTACHMENTS + 2
    private const val MAX_NOTES = 10_000
    private const val MAX_TEXT_BYTES = 8L * 1024 * 1024
    private val hashPattern = Regex("[0-9a-f]{64}")
    private val imageNamePattern = Regex("attachments/[0-9a-f]{64}")
    private val relativePattern = Regex(
        """images/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|webp)"""
    )

    private fun digest(): MessageDigest = MessageDigest.getInstance("SHA-256")
    private fun hex(hash: MessageDigest): String =
        hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun sha256(bytes: ByteArray): String = hex(digest().also { it.update(bytes) })

    private fun validId(value: String) {
        require(UUID.fromString(value).toString() == value) { "ID 必须是规范 UUID" }
    }

    private fun validate(snapshot: BundleSnapshot) {
        require(snapshot.exportedAt >= 0L && snapshot.notes.size <= MAX_NOTES)
        require(snapshot.attachments.size <= MAX_ATTACHMENTS)
        val notes = HashSet<String>()
        var textBytes = 0L
        for (note in snapshot.notes) {
            note.model().validate()
            require(notes.add(note.id)) { "笔记 ID 重复" }
            require(note.deletedAt == null || note.deletedAt >= 0L)
            textBytes += note.title.toByteArray(Charsets.UTF_8).size +
                note.body.toByteArray(Charsets.UTF_8).size
            require(textBytes <= MAX_TEXT_BYTES) { "文本资料超过整包预算" }
        }
        val attachments = HashMap<String, AttachmentEntity>()
        val paths = HashSet<String>()
        val sameDigest = HashMap<String, AttachmentEntity>()
        for (item in snapshot.attachments) {
            validId(item.id)
            require(attachments.put(item.id, item) == null) { "附件 ID 重复" }
            require(paths.add(item.relativePath) && relativePattern.matches(item.relativePath))
            require(item.sha256.matches(hashPattern))
            require(item.mimeType in setOf("image/jpeg", "image/png", "image/webp"))
            require(item.relativePath.endsWith(when (item.mimeType) {
                "image/jpeg" -> ".jpg"
                "image/png" -> ".png"
                else -> ".webp"
            }))
            require(item.byteSize in 1..MAX_IMAGE_BYTES && item.createdAt >= 0)
            require(item.width != null && item.height != null &&
                item.width in 1..16384 && item.height in 1..16384 &&
                item.width.toLong() * item.height <= 100_000_000L)
            val prior = sameDigest.putIfAbsent(item.sha256, item)
            if (prior != null) {
                require(prior.byteSize == item.byteSize &&
                    prior.mimeType == item.mimeType && prior.width == item.width &&
                    prior.height == item.height) { "同一图片指纹的元数据互相矛盾" }
            }
        }
        val uniqueLinks = HashSet<Pair<String, String>>()
        val referencedAttachments = HashSet<String>()
        for (link in snapshot.links) {
            require(link.noteId in notes && link.attachmentId in attachments)
            require(link.sortOrder >= 0)
            require(uniqueLinks.add(link.noteId to link.attachmentId)) { "重复关联" }
            referencedAttachments.add(link.attachmentId)
        }
        require(referencedAttachments.size == attachments.size) { "备份含无主附件" }
    }

    private fun notesJson(snapshot: BundleSnapshot): ByteArray {
        val notes = JSONArray()
        snapshot.notes.sortedBy { it.id }.forEach { n ->
            notes.put(JSONObject()
                .put("id", n.id).put("title", n.title).put("body", n.body)
                .put("created_at", n.createdAt).put("updated_at", n.updatedAt)
                .put("revision", n.revision).put("body_format", n.bodyFormat)
                .put("body_version", n.bodyVersion)
                .put("deleted_at", n.deletedAt ?: JSONObject.NULL))
        }
        val attachments = JSONArray()
        snapshot.attachments.sortedBy { it.id }.forEach { a ->
            attachments.put(JSONObject()
                .put("id", a.id).put("relative_path", a.relativePath)
                .put("mime_type", a.mimeType).put("byte_size", a.byteSize)
                .put("sha256", a.sha256).put("created_at", a.createdAt)
                .put("width", a.width ?: JSONObject.NULL)
                .put("height", a.height ?: JSONObject.NULL))
        }
        val links = JSONArray()
        snapshot.links.sortedWith(compareBy<NoteAttachmentEntity> { it.noteId }
            .thenBy { it.sortOrder }.thenBy { it.attachmentId }).forEach { l ->
            links.put(JSONObject().put("note_id", l.noteId)
                .put("attachment_id", l.attachmentId).put("sort_order", l.sortOrder))
        }
        return JSONObject().put("notes", notes).put("attachments", attachments)
            .put("links", links).toString().toByteArray(Charsets.UTF_8).also {
                require(it.size.toLong() <= MAX_NOTE_BYTES) { "笔记记录清单过大" }
            }
    }

    private data class EntryInfo(val name: String, val bytes: Long, val sha256: String) {
        fun json() = JSONObject().put("name", name).put("bytes", bytes).put("sha256", sha256)
    }

    private fun manifest(snapshot: BundleSnapshot, entries: List<EntryInfo>): ByteArray {
        val array = JSONArray()
        entries.forEach { array.put(it.json()) }
        val raw = JSONObject().put("format", FORMAT).put("version", VERSION)
            .put("database_version", 3).put("exported_at", snapshot.exportedAt)
            .put("theme", snapshot.theme.name)
            .put("notes_count", snapshot.notes.size)
            .put("attachments_count", snapshot.attachments.size)
            .put("links_count", snapshot.links.size)
            .put("entries", array)
            .toString().toByteArray(Charsets.UTF_8)
        require(raw.size.toLong() <= MAX_MANIFEST_BYTES) { "manifest 超限" }
        return raw
    }

    private class CappedOutput(out: OutputStream, private val max: Long) : FilterOutputStream(out) {
        var count = 0L
            private set
        override fun write(value: Int) {
            require(count + 1 <= max) { "ZIP 压缩包过大" }
            out.write(value)
            count++
        }
        override fun write(b: ByteArray, offset: Int, length: Int) {
            require(length >= 0 && count <= max - length) { "ZIP 压缩包过大" }
            out.write(b, offset, length)
            count += length
        }
    }

    /**
     * 输出实际 ZIP 字节。附件源函数必须返回对应独立 InputStream；
     * 写入时重新校验每个 SHA/size，以拒绝快照取得后被修改的图片。
     * 无数据库快照协调；B4.2 将在受控文件临界区中取得一致的数据和源文件。
     */
    fun write(
        output: OutputStream,
        snapshot: BundleSnapshot,
        openImage: (AttachmentEntity) -> InputStream
    ) {
        validate(snapshot)
        val notes = notesJson(snapshot)
        val physical = snapshot.attachments.distinctBy { it.sha256 }.sortedBy { it.sha256 }
        val entries = listOf(EntryInfo("notes.json", notes.size.toLong(), sha256(notes))) +
            physical.map { EntryInfo("attachments/" + it.sha256, it.byteSize, it.sha256) }
        require(entries.size + 1 <= MAX_ENTRIES)
        val expanded = entries.sumOf { it.bytes } + manifest(snapshot, entries).size
        require(expanded <= MAX_EXPANDED_BYTES) { "备份展开总量过大" }
        val header = manifest(snapshot, entries)
        ZipOutputStream(CappedOutput(output, MAX_COMPRESSED_BYTES)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(header)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("notes.json"))
            zip.write(notes)
            zip.closeEntry()
            val buffer = ByteArray(8192)
            for (item in physical) {
                zip.putNextEntry(ZipEntry("attachments/" + item.sha256))
                val checksum = digest()
                var copied = 0L
                var emptyReads = 0
                openImage(item).use { source ->
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        if (n == 0) {
                            check(++emptyReads < 128) { "ZIP 来源没有返回字节" }
                            continue
                        }
                        emptyReads = 0
                        copied += n
                        require(copied <= item.byteSize && copied <= MAX_IMAGE_BYTES) {
                            "附件在导出时发生大小变化"
                        }
                        checksum.update(buffer, 0, n)
                        zip.write(buffer, 0, n)
                    }
                }
                require(copied == item.byteSize && hex(checksum) == item.sha256) {
                    "附件在导出期间发生内容变化"
                }
                zip.closeEntry()
            }
            zip.finish()
        }
    }

    private fun strictUtf8(bytes: ByteArray): String =
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()

    private fun objectOf(bytes: ByteArray): JSONObject {
        val text = strictUtf8(bytes)
        var depth = 0
        var quoted = false
        var escaped = false
        for (c in text) {
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 16) { "嵌套过深" } }
                '}', ']' -> { depth--; require(depth >= 0) }
            }
        }
        require(!quoted && depth == 0)
        val tokener = JSONTokener(text)
        val parsed = tokener.nextValue()
        require(parsed is JSONObject && tokener.nextClean() == '\u0000') {
            "JSON 内容不完整或存在尾随数据"
        }
        return parsed
    }

    private fun JSONObject.hasOnly(vararg names: String) {
        require(keys().asSequence().toSet() == names.toSet()) { "JSON 字段不符合格式" }
    }
    private fun JSONObject.string(key: String): String =
        get(key) as? String ?: throw IllegalArgumentException("JSON 字符串字段无效：" + key)
    private fun JSONObject.long(key: String): Long {
        val v = get(key)
        require(v is Int || v is Long) { "JSON 整数字段无效：" + key }
        return (v as Number).toLong()
    }
    private fun JSONObject.int(key: String): Int =
        long(key).also { require(it in 0L..Int.MAX_VALUE.toLong()) }.toInt()
    private fun JSONObject.nullableLong(key: String): Long? =
        if (get(key) === JSONObject.NULL) null else long(key)
    private fun JSONObject.nullableInt(key: String): Int? =
        if (get(key) === JSONObject.NULL) null else int(key)

    private fun parseNotes(bytes: ByteArray, theme: ThemeMode, at: Long): BundleSnapshot {
        val root = objectOf(bytes)
        root.hasOnly("notes", "attachments", "links")
        val noteArray = root.getJSONArray("notes")
        val imageArray = root.getJSONArray("attachments")
        val linkArray = root.getJSONArray("links")
        require(noteArray.length() <= MAX_NOTES && imageArray.length() <= MAX_ATTACHMENTS)
        require(linkArray.length() <= MAX_NOTES * 128)
        val notes = (0 until noteArray.length()).map { i ->
            val n = noteArray.getJSONObject(i)
            n.hasOnly("id", "title", "body", "created_at", "updated_at", "revision",
                "body_format", "body_version", "deleted_at")
            NoteEntity(n.string("id"), n.string("title"), n.string("body"),
                n.long("created_at"), n.long("updated_at"), n.long("revision"),
                n.string("body_format"), n.int("body_version"), n.nullableLong("deleted_at"))
        }
        val attachments = (0 until imageArray.length()).map { i ->
            val v = imageArray.getJSONObject(i)
            v.hasOnly("id", "relative_path", "mime_type", "byte_size",
                "sha256", "created_at", "width", "height")
            AttachmentEntity(v.string("id"), v.string("relative_path"),
                v.string("mime_type"), v.long("byte_size"), v.string("sha256"),
                v.long("created_at"), v.nullableInt("width"), v.nullableInt("height"))
        }
        val links = (0 until linkArray.length()).map { i ->
            val v = linkArray.getJSONObject(i)
            v.hasOnly("note_id", "attachment_id", "sort_order")
            NoteAttachmentEntity(v.string("note_id"), v.string("attachment_id"), v.int("sort_order"))
        }
        return BundleSnapshot(notes, attachments, links, theme, at).also(::validate)
    }

    private fun manifestEntries(bytes: ByteArray): Triple<BundleSnapshotHeader, Map<String, EntryInfo>, List<EntryInfo>> {
        val obj = objectOf(bytes)
        obj.hasOnly("format", "version", "database_version", "exported_at", "theme",
            "notes_count", "attachments_count", "links_count", "entries")
        require(obj.string("format") == FORMAT && obj.int("version") == VERSION)
        require(obj.int("database_version") == 3)
        val header = BundleSnapshotHeader(
            ThemeMode.valueOf(obj.string("theme")), obj.long("exported_at"),
            obj.int("notes_count"), obj.int("attachments_count"), obj.int("links_count")
        )
        require(header.exportedAt >= 0 && header.notes <= MAX_NOTES &&
            header.attachments <= MAX_ATTACHMENTS && header.links <= MAX_NOTES * 128)
        val array = obj.getJSONArray("entries")
        require(array.length() in 1 until MAX_ENTRIES)
        val result = ArrayList<EntryInfo>(array.length())
        for (i in 0 until array.length()) {
            val v = array.getJSONObject(i)
            v.hasOnly("name", "bytes", "sha256")
            val name = v.string("name")
            require(name == "notes.json" || imageNamePattern.matches(name)) { "未知 ZIP 条目" }
            val size = v.long("bytes")
            require(size in 1..(if (name == "notes.json") MAX_NOTE_BYTES else MAX_IMAGE_BYTES))
            val sha = v.string("sha256")
            require(sha.matches(hashPattern))
            if (name.startsWith("attachments/")) require(sha == name.removePrefix("attachments/"))
            result.add(EntryInfo(name, size, sha))
        }
        require(result.map { it.name }.toSet().size == result.size &&
            result.count { it.name == "notes.json" } == 1)
        require(result.sumOf { it.bytes } + bytes.size <= MAX_EXPANDED_BYTES)
        return Triple(header, result.associateBy { it.name }, result)
    }

    private data class BundleSnapshotHeader(
        val theme: ThemeMode, val exportedAt: Long, val notes: Int, val attachments: Int, val links: Int
    )

    private fun limitedBytes(source: InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        var emptyReads = 0
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            if (count == 0) {
                check(++emptyReads < 128) { "数据源没有返回字节" }
                continue
            }
            emptyReads = 0
            total += count
            require(total <= limit) { "ZIP 条目超出大小限制" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    /** 
     * 对 ZIP 做完整预检并安全解包到工作缓存；不修改原有数据库与附件目录。
     * 必须使用 ZipFile 校验 ZIP 中央目录，防止仅用 ZipInputStream 忽略截断尾部。
     */
    fun inspect(input: InputStream, workDir: File): InspectedBundle {
        require(workDir.isDirectory && !Files.isSymbolicLink(workDir.toPath()))
        val stage = Files.createTempDirectory(workDir.toPath(), "xuaney-bundle-v2-").toFile()
        val zipFile = File(stage, "bundle.zip")
        try {
            var copied = 0L
            var emptyReads = 0
            input.use { source ->
                FileOutputStream(zipFile).use { out ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        if (n == 0) {
                            check(++emptyReads < 128) { "附件来源没有返回字节" }
                            continue
                        }
                        emptyReads = 0
                        copied += n
                        require(copied <= MAX_COMPRESSED_BYTES) { "ZIP 文件超过大小上限" }
                        out.write(buffer, 0, n)
                    }
                    out.fd.sync()
                }
            }
            require(copied > 0) { "ZIP 备份为空" }
            val files = HashMap<String, File>()
            var result: BundleSnapshot? = null
            ZipFile(zipFile).use { zip ->
                val all = mutableListOf<ZipEntry>()
                val enumeration = zip.entries()
                val names = HashSet<String>()
                while (enumeration.hasMoreElements()) {
                    val e = enumeration.nextElement()
                    val name = e.name
                    require(!e.isDirectory && !name.contains('\\') &&
                        (name == "manifest.json" || name == "notes.json" ||
                            imageNamePattern.matches(name))) { "ZIP 包含非白名单路径" }
                    require(names.add(name)) { "ZIP 重复路径" }
                    require(e.size >= 0 && e.compressedSize >= 0)
                    require(e.size <= when (name) {
                        "manifest.json" -> MAX_MANIFEST_BYTES
                        "notes.json" -> MAX_NOTE_BYTES
                        else -> MAX_IMAGE_BYTES
                    }) { "条目声明大小超限" }
                    all += e
                    require(all.size <= MAX_ENTRIES) { "ZIP 条目过多" }
                }
                require(all.size >= 2 && names.contains("manifest.json") && names.contains("notes.json"))
                val manifestBytes = zip.getInputStream(zip.getEntry("manifest.json")).use {
                    limitedBytes(it, MAX_MANIFEST_BYTES)
                }
                    val (header, expected, _) = manifestEntries(manifestBytes)
                require(names - "manifest.json" == expected.keys) { "ZIP 列表与 manifest 不一致" }
                var expanded = manifestBytes.size.toLong()
                var notesBytes: ByteArray? = null
                val destination = File(stage, "verified-images")
                check(destination.mkdir())
                for (entry in all) {
                    if (entry.name == "manifest.json") continue
                    val expectedEntry = requireNotNull(expected[entry.name])
                    val max = if (entry.name == "notes.json") MAX_NOTE_BYTES else MAX_IMAGE_BYTES
                    var entryCount = 0L
                    val hash = digest()
                    var emptyReads = 0
                    val destinationFile = if (entry.name == "notes.json") null else
                        File(destination, entry.name.removePrefix("attachments/"))
                    val memory = if (entry.name == "notes.json") ByteArrayOutputStream() else null
                    zip.getInputStream(entry).use { source ->
                        val target = destinationFile?.let { FileOutputStream(it) }
                        try {
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                if (count == 0) {
                                    check(++emptyReads < 128) { "条目没有返回字节" }
                                    continue
                                }
                                emptyReads = 0
                                entryCount += count
                                expanded += count
                                require(entryCount <= max && expanded <= MAX_EXPANDED_BYTES) {
                                    "ZIP 展开大小超过限制"
                                }
                                hash.update(buffer, 0, count)
                                if (target != null) target.write(buffer, 0, count)
                                else memory?.write(buffer, 0, count)
                            }
                            target?.fd?.sync()
                        } finally { target?.close() }
                    }
                    require(entryCount == expectedEntry.bytes &&
                        hex(hash) == expectedEntry.sha256) { "ZIP 校验和或大小错误" }
                    if (memory != null) notesBytes = memory.toByteArray()
                    if (destinationFile != null) files[entry.name.removePrefix("attachments/")] = destinationFile
                }
                val snapshot = parseNotes(requireNotNull(notesBytes), header.theme, header.exportedAt)
                require(snapshot.notes.size == header.notes &&
                    snapshot.attachments.size == header.attachments &&
                    snapshot.links.size == header.links)
                val usedDigests = snapshot.attachments.map { it.sha256 }.toSet()
                require(usedDigests == files.keys) { "缺失图片或混入额外图片" }
                for (attachment in snapshot.attachments) {
                    val bytes = requireNotNull(files[attachment.sha256])
                    require(bytes.length() == attachment.byteSize)
                    validatePicture(bytes, attachment)
                }
                result = snapshot
            }
            require(zipFile.delete()) { "ZIP 暂存不能安全清理" }
            return InspectedBundle(requireNotNull(result), files, stage)
        } catch (failure: Throwable) {
            stage.deleteRecursively()
            throw failure
        }
    }

    private fun validatePicture(file: File, item: AttachmentEntity) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        require(options.outMimeType == item.mimeType &&
            options.outWidth == item.width && options.outHeight == item.height &&
            options.outWidth in 1..16384 && options.outHeight in 1..16384) {
            "图片格式或尺寸被篡改"
        }
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / sample > 1024) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: throw IllegalArgumentException("备份包含不可解码的图片")
        bitmap.recycle()
    }
}
