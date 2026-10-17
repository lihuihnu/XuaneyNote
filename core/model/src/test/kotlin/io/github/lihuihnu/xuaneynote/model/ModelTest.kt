package io.github.lihuihnu.xuaneynote.model

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelTest {
    private val note = Note("00000000-0000-0000-0000-000000000001", "中文", "第一行\n🙂", 1, 2, 1)
    @Test fun unicodeIsPreserved() { note.validate(); assertEquals(note, planRestore(emptyList(), listOf(note)).inserts.single()) }
    @Test fun repeatedImportSkips() { assertEquals(1, planRestore(listOf(note), listOf(note)).skipped) }
    @Test fun conflictPreservesBothAndIsIdempotent() {
        val edited = note.copy(body = "原机修改", revision = 2)
        val first = planRestore(listOf(edited), listOf(note))
        assertEquals(1, first.conflicts)
        assertNotEquals(note.id, first.inserts.single().id)
        assertEquals(note.body, first.inserts.single().body)
        assertTrue(planRestore(listOf(edited) + first.inserts, listOf(note)).inserts.isEmpty())
    }
    @Test(expected = IllegalArgumentException::class)
    fun duplicateIdsFail() { planRestore(emptyList(), listOf(note, note)) }
    @Test(expected = IllegalArgumentException::class)
    fun invalidTimeFails() { note.copy(updatedAt = 0).validate() }
    @Test(expected = IllegalArgumentException::class)
    fun unknownBodyVersionFails() { note.copy(bodyVersion = 2).validate() }
    @Test fun coalescesToLatestRevision() = runTest {
        val written = mutableListOf<Note>()
        val queue = AutosaveQueue(backgroundScope, { written += it }, clock = { testScheduler.currentTime })
        repeat(30) { queue.submit(note.copy(revision = it + 1L)) }
        runCurrent(); advanceTimeBy(350); runCurrent()
        assertEquals(listOf(30L), written.map { it.revision })
        assertEquals(SaveState.Saved(30), queue.states.value[note.id])
    }
    @Test fun continuousTypingHasMaximumWait() = runTest {
        val written = mutableListOf<Note>()
        val queue = AutosaveQueue(backgroundScope, { written += it }, maxWaitMs = 600, clock = { testScheduler.currentTime })
        repeat(10) { queue.submit(note.copy(revision = it + 1L)); advanceTimeBy(100); runCurrent() }
        assertTrue(written.isNotEmpty())
    }
    @Test fun writeFailureIsNotSavedAndCanRetry() = runTest {
        var fail = true
        val queue = AutosaveQueue(backgroundScope, { if (fail) error("synthetic disk full") }, clock = { testScheduler.currentTime })
        queue.submit(note); queue.requestFlush(); runCurrent()
        assertTrue(queue.states.value[note.id] is SaveState.Failed)
        assertFalse(queue.flush())
        fail = false; queue.retry(note.id); runCurrent()
        assertTrue(queue.flush())
    }
}
