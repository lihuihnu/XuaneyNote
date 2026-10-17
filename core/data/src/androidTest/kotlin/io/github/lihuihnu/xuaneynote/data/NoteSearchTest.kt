package io.github.lihuihnu.xuaneynote.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lihuihnu.xuaneynote.model.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** B1 仅测试隔离的合成资料，绝不读取用户私人笔记。 */
@RunWith(AndroidJUnit4::class)
class NoteSearchTest {
    @Test fun literalChineseEnglishAndDeletedNotesAreHandledCorrectly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "b1-search-${UUID.randomUUID()}.db"
        val database = NoteDatabase.open(context, name)
        try {
            val store = NoteStore(database)
            val first = Note("00000000-0000-0000-0000-000000000111",
                "地下水中文题目", "ALPHA note", 1, 20, 1)
            val second = Note("00000000-0000-0000-0000-000000000112",
                "ALPHA 记录", "这里也研究地下水🙂", 1, 30, 0)
            val special = Note("00000000-0000-0000-0000-000000000113",
                "literal %_ O'Reilly", "原样保留反斜杠 \\ 路径", 1, 40, 0)
            val deleted = Note("00000000-0000-0000-0000-000000000114",
                "alpha 已删除", "地下水内部草稿", 1, 50, 0)
            database.notes().insert(
                listOf(first, second, special).map(NoteEntity::from) +
                    NoteEntity.from(deleted).copy(deletedAt = 60)
            )
            assertEquals(listOf(second.id, first.id),
                store.search("地下水").first().map { it.id })
            assertEquals(listOf(second.id, first.id),
                store.search("alpha").first().map { it.id })
            assertEquals(listOf(special.id), store.search("%").first().map { it.id })
            assertEquals(listOf(special.id), store.search("_").first().map { it.id })
            assertEquals(listOf(special.id), store.search("'").first().map { it.id })
            assertEquals(listOf(special.id), store.search("\\").first().map { it.id })
            assertTrue(store.search("' OR 1=1 --").first().isEmpty())
            assertTrue(store.search("完全不存在").first().isEmpty())
            assertEquals(3, store.summaries.first().size)
            assertTrue(runCatching { store.search(" ") }.isFailure)
            assertTrue(runCatching { store.search("a".repeat(101)) }.isFailure)
            val updated = first.copy(body = "现在包含一个新关键词-雪山", revision = 2, updatedAt = 51)
            store.save(updated)
            assertEquals(listOf(first.id), store.search("雪山").first().map { it.id })
            assertEquals(listOf(first.id, second.id, special.id),
                store.summaries.first().map { it.id })
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun syntheticDatasetsBoundResultsAndRecordSearchTiming() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "b1-scale-${UUID.randomUUID()}.db"
        val database = NoteDatabase.open(context, name)
        fun idOf(i: Int) = UUID.nameUUIDFromBytes("XuaneyNote-B1-$i".toByteArray(Charsets.UTF_8)).toString()
        try {
            val store = NoteStore(database)
            var created = 0
            for (size in listOf(100, 1000, 10000)) {
                val entities = (created until size).map { i ->
                    NoteEntity.from(Note(idOf(i),
                        if (i % 2 == 0) "needle 模拟记录 $i" else "普通记录 $i",
                        "合成正文，提供基本测试内容。", 1L, i.toLong() + 2, 0))
                }
                database.notes().insert(entities)
                created = size
                val samples = mutableListOf<Double>()
                repeat(7) {
                    val start = System.nanoTime()
                    val rows = store.search("needle").first()
                    samples += (System.nanoTime() - start) / 1_000_000.0
                    assertEquals(minOf(size / 2, NoteStore.SEARCH_VISIBLE_LIMIT + 1), rows.size)
                    assertEquals(idOf(size - 2), rows.first().id)
                    assertTrue(rows.zipWithNext().all { (left, right) ->
                        left.updatedAt > right.updatedAt ||
                            (left.updatedAt == right.updatedAt && left.id <= right.id)
                    })
                }
                val ordered = samples.sorted()
                System.out.println("B1_SEARCH_PROFILE entries=$size samples=7 " +
                    "p95_ms=${ordered.last()} median_ms=${ordered[ordered.size / 2]} " +
                    "environment=API35_emulator_not_real_phone")
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
