import io.github.lihuihnu.xuaneynote.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** 无 Android SDK 的补充烟雾检查；不能代替锁定版本 Gradle/设备门禁。 */
fun main() = runBlocking {
    val note = Note("00000000-0000-0000-0000-000000000001", "中文", "第一行\n🙂", 1, 2, 1)
    val checks = mutableListOf<String>()
    fun passed(name: String) { checks += name; println("PASS $name") }
    note.validate(); passed("unicode_note_validation")
    check(planRestore(emptyList(), listOf(note)).inserts == listOf(note)); passed("restore_into_empty")
    check(runCatching { planRestore(emptyList(), listOf(note, note)) }.isFailure); passed("reject_duplicate_ids")
    check(runCatching { note.copy(updatedAt = 0).validate() }.isFailure); passed("reject_invalid_time")
    val existing = note.copy(body = "原机修改", revision = 2)
    val first = planRestore(listOf(existing), listOf(note))
    check(first.inserts.single().id != existing.id && first.inserts.single().body == note.body)
    passed("conflict_keeps_both")
    check(planRestore(listOf(existing) + first.inserts, listOf(note)).inserts.isEmpty()); passed("repeat_import_idempotent")
    val editedCopy = first.inserts.single().copy(body = "副本也修改了", revision = 3)
    check(planRestore(listOf(existing, editedCopy), listOf(note)).inserts.single().id != editedCopy.id)
    passed("modified_copy_is_preserved")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    try {
        val written = java.util.Collections.synchronizedList(mutableListOf<Note>())
        val queue = AutosaveQueue(scope, { written += it }, debounceMs = 200, maxWaitMs = 1000)
        repeat(30) { queue.submit(note.copy(revision = it + 1L)) }
        check(withTimeout(3000) { queue.flush() })
        check(written.last().revision == 30L && written.size == 1); passed("coalesces_and_flushes")
        var fail = true
        val failing = AutosaveQueue(scope, { if (fail) error("synthetic disk full") }, debounceMs = 0)
        failing.submit(note)
        check(!withTimeout(3000) { failing.flush() })
        check(failing.states.value[note.id] is SaveState.Failed); passed("failure_not_reported_as_saved")
        fail = false; failing.retry(note.id)
        check(withTimeout(3000) { failing.flush() }); passed("failed_write_retry")
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val delayed = AutosaveQueue(scope, { if (it.revision == 1L) { started.complete(Unit); release.await() } }, debounceMs = 0)
        delayed.submit(note); withTimeout(3000) { started.await() }
        delayed.submit(note.copy(revision = 2)); release.complete(Unit)
        check(withTimeout(3000) { delayed.flush() })
        check(delayed.states.value[note.id] == SaveState.Saved(2)); passed("old_completion_cannot_mark_new_revision_saved")
    } finally { scope.cancel() }
    println("RESULT ${checks.size}/${checks.size} passed; supplemental local JVM smoke only")
}
