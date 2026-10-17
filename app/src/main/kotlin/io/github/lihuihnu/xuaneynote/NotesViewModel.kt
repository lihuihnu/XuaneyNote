package io.github.lihuihnu.xuaneynote

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.lihuihnu.xuaneynote.data.BackupCodec
import io.github.lihuihnu.xuaneynote.data.NoteBackup
import io.github.lihuihnu.xuaneynote.data.NoteStore
import io.github.lihuihnu.xuaneynote.data.LegacyBackupWouldOmitTrash
import io.github.lihuihnu.xuaneynote.model.Note
import io.github.lihuihnu.xuaneynote.model.NoteSummary
import io.github.lihuihnu.xuaneynote.model.SaveState
import io.github.lihuihnu.xuaneynote.model.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NoteListing(val keyword: String, val items: List<NoteSummary>)

/** B1: 防抖并取消过时查询，数据库 I/O 不进入 Compose 编辑主线程。 */
@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotesViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as XuaneyApplication
    private val data = app.data
    private val writes = app.saves
    private val mutableNote = MutableStateFlow<Note?>(null)
    private val mutableBusy = MutableStateFlow(false)
    private val mutableMessage = MutableStateFlow<String?>(null)
    private val mutableImport = MutableStateFlow<NoteBackup?>(null)
    private val mutableSearchText = MutableStateFlow(savedState.get<String>("note_search").orEmpty())
    val searchText = mutableSearchText.asStateFlow()
    private val mutableTrashOpen = MutableStateFlow(savedState.get<Boolean>("trash_open") ?: false)
    val trashOpen = mutableTrashOpen.asStateFlow()
    val trashNotes = data.notes.trashSummaries
        .catch { mutableMessage.value = "读取回收站失败，已有笔记仍保留在本机。" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val note = mutableNote.asStateFlow()
    val busy = mutableBusy.asStateFlow()
    val message = mutableMessage.asStateFlow()
    val pendingImport = mutableImport.asStateFlow()
    val saveStates = writes.states
    val listing = mutableSearchText
        .debounce { if (it.isBlank()) 0L else 250L }
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { keyword ->
            val source = if (keyword.isEmpty()) data.notes.summaries else data.notes.search(keyword)
            source.map { NoteListing(keyword, it) }.catch {
                mutableMessage.value = "查询笔记失败，请稍后重试；已有笔记不会被修改。"
                emit(NoteListing(keyword, emptyList()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NoteListing("", emptyList()))
    val theme = data.preferences.theme.catch {
        mutableMessage.value = "读取外观设置失败，暂时跟随系统。"
        emit(ThemeMode.SYSTEM)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    init {
        operation {
            val id = savedState.get<String>("selected_note") ?: data.preferences.selectedNote.first()
            if (id != null) mutableNote.value = writes.draft(id) ?: data.notes.get(id)
        }
    }

    private fun operation(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: LegacyBackupWouldOmitTrash) { mutableMessage.value = error.message }
            catch (_: Exception) { mutableMessage.value = "操作未完成。请检查文件、权限或可用空间后重试；已有笔记不会被清空。" }
            finally { mutableBusy.value = false }
        }
    }

    private suspend fun requireSaved() {
        check(writes.flush()) { "存在保存失败的草稿" }
    }

    private suspend fun select(note: Note?) {
        mutableNote.value = note
        savedState["selected_note"] = note?.id
        data.preferences.select(note?.id)
    }

    fun updateSearchText(value: String) {
        if (value.length > NoteStore.SEARCH_MAX_LENGTH) return
        mutableSearchText.value = value
        savedState["note_search"] = value
    }

    fun openTrash() = operation {
        requireSaved()
        select(null)
        mutableTrashOpen.value = true
        savedState["trash_open"] = true
    }
    fun leaveTrash() {
        // 退出回收站只是展示状态更新；首次恢复 Activity 时也必须可重置。
        mutableTrashOpen.value = false
        savedState["trash_open"] = false
    }
    fun moveToTrash(id: String) = operation {
        requireSaved()
        check(data.notes.moveToTrash(id)) { "未找到可删除笔记" }
        if (mutableNote.value?.id == id) select(null)
        mutableMessage.value = "已移入回收站，仍可以恢复。"
    }
    fun restoreFromTrash(id: String) = operation {
        requireSaved()
        check(data.notes.restoreFromTrash(id)) { "笔记已恢复或不存在" }
        mutableTrashOpen.value = false
        savedState["trash_open"] = false
        select(data.notes.get(id))
        mutableMessage.value = "笔记已从回收站恢复。"
    }
    fun permanentlyDeleteTrashed(id: String) = operation {
        requireSaved()
        check(data.notes.permanentlyDelete(id)) { "只有回收站中的笔记才能永久删除" }
        mutableMessage.value = "本机笔记已删除；此前导出的外部备份不会自动清除。"
    }

    fun createNote() = operation { requireSaved(); select(data.notes.create()) }
    fun openNote(id: String) = operation { requireSaved(); select(writes.draft(id) ?: data.notes.get(id)) }
    fun closeNote() = operation { requireSaved(); select(null) }

    fun editTitle(value: String) = edit(title = value)
    fun editBody(value: String) = edit(body = value)
    private fun edit(title: String? = null, body: String? = null) {
        if (mutableBusy.value) return
        val previous = mutableNote.value ?: return
        val nextTitle = title ?: previous.title
        val nextBody = body ?: previous.body
        if (nextTitle == previous.title && nextBody == previous.body) return
        if (nextTitle.length > Note.MAX_TITLE || nextBody.length > Note.MAX_BODY || previous.revision == Long.MAX_VALUE) {
            mutableMessage.value = "已达到单篇笔记长度或修订限制，请新建笔记。"
            return
        }
        val next = previous.copy(title = nextTitle, body = nextBody,
            updatedAt = maxOf(System.currentTimeMillis(), previous.updatedAt), revision = previous.revision + 1)
        mutableNote.value = next
        writes.submit(next)
    }

    fun retrySaves() { writes.states.value.filterValues { it is SaveState.Failed }.keys.forEach(writes::retry) }
    fun clearMessage() { mutableMessage.value = null }
    fun setTheme(mode: ThemeMode) = operation { data.preferences.setTheme(mode) }

    fun exportBackup(uri: Uri) = operation {
        requireSaved()
        val backup = NoteBackup(data.notes.snapshot(), data.preferences.theme.first(), System.currentTimeMillis())
        withContext(Dispatchers.IO) {
            val bytes = BackupCodec.encode(backup)
            val resolver = app.contentResolver
            requireNotNull(resolver.openOutputStream(uri, "wt")).use { stream -> stream.write(bytes); stream.flush() }
            // 完成关闭后重新读回验证，不能把打开文件选择器当作导出成功。
            val verified = requireNotNull(resolver.openInputStream(uri)).use(BackupCodec::read)
            check(verified == backup.copy(notes = backup.notes.sortedBy { it.id }))
        }
        mutableMessage.value = "备份已写入并读回校验。请保管好未加密的备份文件。"
    }

    fun inspectBackup(uri: Uri) = operation {
        requireSaved()
        mutableImport.value = withContext(Dispatchers.IO) {
            requireNotNull(app.contentResolver.openInputStream(uri)).use(BackupCodec::read)
        }
    }
    fun cancelRestore() { if (!mutableBusy.value) mutableImport.value = null }
    fun confirmRestore() = operation {
        requireSaved()
        val backup = mutableImport.value ?: return@operation
        val result = withContext(Dispatchers.IO) { data.notes.restore(backup.notes) }
        mutableImport.value = null
        // Room 与 DataStore 不是跨存储事务；主题失败须单独报告，不假称全部恢复。
        val settingsRestored = try { data.preferences.setTheme(backup.theme); true }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { false }
        mutableMessage.value = "已恢复 ${result.inserts.size} 条，跳过 ${result.skipped} 条，冲突 ${result.conflicts} 条。" +
            if (settingsRestored) "" else "笔记已恢复，但主题设置失败；可再次导入重试。"
    }
}
