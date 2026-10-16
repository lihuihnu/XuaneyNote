package io.github.lihuihnu.xuaneynote.data

import io.github.lihuihnu.xuaneynote.model.Note
import io.github.lihuihnu.xuaneynote.model.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** PR-A 仅覆盖当前存在的文本笔记与主题；没有附件、提醒、云同步或密码加密。 */
data class NoteBackup(val notes: List<Note>, val theme: ThemeMode, val exportedAt: Long)

object BackupCodec {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_NOTES = 10_000
    private const val FORMAT = "XuaneyNote.text-backup"

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun read(input: InputStream): NoteBackup {
        val bytes = input.readBytesBounded()
        return decode(bytes)
    }

    private fun InputStream.readBytesBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "备份超过 8 MiB 限制" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun encode(backup: NoteBackup): ByteArray {
        validate(backup)
        val records = JSONArray()
        backup.notes.sortedBy { it.id }.forEach { note ->
            records.put(JSONObject().put("id", note.id).put("type", "NOTE")
                .put("title", note.title).put("body", note.body)
                .put("created_at", note.createdAt).put("updated_at", note.updatedAt)
                .put("revision", note.revision).put("body_format", note.bodyFormat)
                .put("body_version", note.bodyVersion))
        }
        val payload = JSONObject().put("app_version", "0.1.0-pr-a").put("database_version", 1)
            .put("exported_at", backup.exportedAt).put("theme", backup.theme.name)
            .put("attachments", JSONArray()).put("notes", records).toString()
        val raw = payload.toByteArray(Charsets.UTF_8)
        val bytes = JSONObject().put("format", FORMAT).put("version", 1)
            .put("payload_bytes", raw.size).put("sha256", sha256(raw)).put("payload", payload)
            .toString(2).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "备份超过 8 MiB 限制" }
        return bytes
    }

    fun decode(bytes: ByteArray): NoteBackup {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "备份为空或超过限制" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val root = parseObject(text)
        root.exactKeys("format", "version", "payload_bytes", "sha256", "payload")
        require(root.string("format") == FORMAT && root.integer("version") == 1L) { "不支持的备份版本" }
        val raw = root.string("payload").toByteArray(Charsets.UTF_8)
        require(root.integer("payload_bytes") == raw.size.toLong()) { "备份大小不匹配" }
        require(root.string("sha256").matches(Regex("[0-9a-f]{64}"))) { "无效的校验值" }
        require(MessageDigest.isEqual(root.string("sha256").toByteArray(), sha256(raw).toByteArray())) { "备份校验失败" }
        val payload = parseObject(root.string("payload"))
        payload.exactKeys("app_version", "database_version", "exported_at", "theme", "attachments", "notes")
        require(payload.string("app_version").isNotBlank() && payload.integer("database_version") == 1L)
        require(payload.getJSONArray("attachments").length() == 0) { "当前版本不支持附件恢复" }
        val records = payload.getJSONArray("notes")
        require(records.length() <= MAX_NOTES) { "笔记数量超过限制" }
        val notes = (0 until records.length()).map { index ->
            val obj = records.getJSONObject(index)
            obj.exactKeys("id", "type", "title", "body", "created_at", "updated_at", "revision", "body_format", "body_version")
            require(obj.string("type") == "NOTE" && obj.integer("body_version") == 1L)
            Note(obj.string("id"), obj.string("title"), obj.string("body"), obj.integer("created_at"),
                obj.integer("updated_at"), obj.integer("revision"), obj.string("body_format"), 1)
        }
        return NoteBackup(notes, ThemeMode.valueOf(payload.string("theme")), payload.integer("exported_at"))
            .also(::validate)
    }

    private fun parseObject(text: String): JSONObject {
        // 拒绝深层嵌套和尾随内容，防止受损/恶意 JSON 耗尽解析栈。
        var depth = 0
        var quoted = false
        var escaped = false
        for (char in text) {
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 16) { "备份嵌套过深" } }
                '}', ']' -> { depth--; require(depth >= 0) }
            }
        }
        require(!quoted && depth == 0) { "备份不完整" }
        val tokenizer = JSONTokener(text)
        val value = tokenizer.nextValue()
        require(value is JSONObject && tokenizer.nextClean() == '\u0000') { "备份含尾随内容" }
        return value
    }

    private fun validate(backup: NoteBackup) {
        require(backup.exportedAt >= 0 && backup.notes.size <= MAX_NOTES)
        require(backup.notes.map { it.id }.toSet().size == backup.notes.size) { "备份包含重复 ID" }
        backup.notes.forEach(Note::validate)
    }
    private fun JSONObject.string(key: String): String = (get(key) as? String) ?: error("字段类型错误：$key")
    private fun JSONObject.integer(key: String): Long {
        val value = get(key)
        require(value is Int || value is Long) { "字段类型错误：$key" }
        return (value as Number).toLong()
    }
    private fun JSONObject.exactKeys(vararg allowed: String) {
        require(keys().asSequence().toSet() == allowed.toSet()) { "备份字段缺失或未知" }
    }
}
