package io.github.lihuihnu.xuaneynote.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SaveState {
    val revision: Long
    data class Saving(override val revision: Long) : SaveState
    data class Saved(override val revision: Long) : SaveState
    data class Failed(override val revision: Long) : SaveState
}

/** 应用作用域的单消费者；合并同一笔记的连续输入，不取消正在提交的事务。 */
class AutosaveQueue(
    scope: CoroutineScope,
    private val write: suspend (Note) -> Unit,
    private val debounceMs: Long = 350,
    private val maxWaitMs: Long = 1500,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private data class Pending(val note: Note, val firstAt: Long, val dueAt: Long)
    private val lock = Any()
    private val pending = linkedMapOf<String, Pending>()
    private val drafts = mutableMapOf<String, Note>()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val mutableStates = MutableStateFlow<Map<String, SaveState>>(emptyMap())
    val states: StateFlow<Map<String, SaveState>> = mutableStates.asStateFlow()

    init {
        require(debounceMs >= 0 && maxWaitMs >= debounceMs)
        scope.launch {
            for (ignored in signal) {
                while (true) {
                    val next = synchronized(lock) { pending.values.minByOrNull { it.dueAt } } ?: break
                    val wait = next.dueAt - clock()
                    if (wait > 0) { withTimeoutOrNull(wait) { signal.receive() }; continue }
                    val work = synchronized(lock) {
                        val current = pending[next.note.id]
                        if (current == next) pending.remove(next.note.id) else null
                    } ?: continue
                    val result = try {
                        write(work.note)
                        SaveState.Saved(work.note.revision)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // 不把笔记正文或文件路径写入日志；错误通过界面显式呈现。
                        SaveState.Failed(work.note.revision)
                    }
                    synchronized(lock) {
                        if (drafts[work.note.id]?.revision == work.note.revision) {
                            mutableStates.value = mutableStates.value + (work.note.id to result)
                            if (result is SaveState.Saved) drafts.remove(work.note.id)
                        }
                    }
                }
            }
        }
    }

    fun submit(note: Note) {
        note.validate()
        synchronized(lock) {
            val previousRevision = mutableStates.value[note.id]?.revision ?: -1
            require(note.revision >= previousRevision) { "不能提交旧修订" }
            val now = clock()
            val firstAt = pending[note.id]?.firstAt ?: now
            pending[note.id] = Pending(note, firstAt, minOf(now + debounceMs, firstAt + maxWaitMs))
            drafts[note.id] = note
            mutableStates.value = mutableStates.value + (note.id to SaveState.Saving(note.revision))
        }
        signal.trySend(Unit)
    }

    fun draft(id: String): Note? = synchronized(lock) { drafts[id] }
    fun retry(id: String) { draft(id)?.let(::submit); requestFlush() }

    fun requestFlush() {
        synchronized(lock) { pending.replaceAll { _, value -> value.copy(dueAt = clock()) } }
        signal.trySend(Unit)
    }

    /** 调用者在等待期间禁用编辑；失败不能被当作成功的屏障。 */
    suspend fun flush(): Boolean {
        requestFlush()
        return states.first { values -> values.values.none { it is SaveState.Saving } }
            .values.none { it is SaveState.Failed }
    }
}
