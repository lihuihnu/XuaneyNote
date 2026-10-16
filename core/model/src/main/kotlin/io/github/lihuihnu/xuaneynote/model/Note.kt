package io.github.lihuihnu.xuaneynote.model

import java.util.UUID

/** 文本版本与修订号独立：修改正文不会隐式改变格式。 */
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long = 0,
    val bodyFormat: String = "plain",
    val bodyVersion: Int = 1,
) {
    fun validate() {
        require(UUID.fromString(id).toString() == id) { "无效的笔记 ID" }
        require(title.length <= MAX_TITLE && body.length <= MAX_BODY) { "笔记超过长度限制" }
        require(createdAt >= 0 && updatedAt >= createdAt && revision >= 0) { "无效的时间或修订号" }
        require(bodyFormat in setOf("plain", "markdown") && bodyVersion == 1) { "不支持的正文格式" }
    }

    companion object {
        const val MAX_TITLE = 500
        const val MAX_BODY = 1_000_000
        fun empty(now: Long = System.currentTimeMillis()) = Note(UUID.randomUUID().toString(), "", "", now, now)
    }
}

data class NoteSummary(val id: String, val title: String, val preview: String, val updatedAt: Long)
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class RestorePlan(val inserts: List<Note>, val skipped: Int, val conflicts: Int)

/** ID 冲突一律保留副本；稳定派生 ID 使同一份备份重复导入幂等。 */
fun planRestore(existing: List<Note>, incoming: List<Note>): RestorePlan {
    require(incoming.map { it.id }.toSet().size == incoming.size) { "备份包含重复 ID" }
    val known = existing.associateBy { it.id }.toMutableMap()
    val inserts = mutableListOf<Note>()
    var skipped = 0
    var conflicts = 0
    for (note in incoming) {
        note.validate()
        val original = known[note.id]
        if (original == note) { skipped++; continue }
        var candidate = note
        if (original != null) {
            conflicts++
            // 长度前缀防止字段分隔符造成不同笔记的指纹碰撞。
            val fingerprint = listOf(note.id, note.title, note.body, note.createdAt.toString(),
                note.updatedAt.toString(), note.revision.toString(), note.bodyFormat, note.bodyVersion.toString())
                .joinToString("") { "${it.length}:$it" }
            var attempt = 0
            do {
                val id = UUID.nameUUIDFromBytes("XuaneyNote/restore/$attempt/$fingerprint".toByteArray(Charsets.UTF_8)).toString()
                candidate = note.copy(id = id, title = note.title.take(Note.MAX_TITLE - 6) + "（恢复副本）")
                attempt++
            } while (known[candidate.id] != null && known[candidate.id] != candidate)
        }
        if (known[candidate.id] == candidate) { skipped++; continue }
        known[candidate.id] = candidate
        inserts += candidate
    }
    return RestorePlan(inserts, skipped, conflicts)
}
