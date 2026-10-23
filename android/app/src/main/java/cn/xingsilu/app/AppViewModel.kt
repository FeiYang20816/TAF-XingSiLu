package cn.xingsilu.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import cn.xingsilu.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

enum class Screen { HOME, EDITOR, SETTINGS, TRASH }
class AppViewModel(application: Application, private val state: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as XingSiLuApplication
    private val repo = app.repository
    var notes by mutableStateOf<List<Note>>(emptyList()); private set
    var drafts by mutableStateOf<List<Draft>>(emptyList()); private set
    var preferences by mutableStateOf(UserPreferences()); private set
    var ready by mutableStateOf(false); private set
    var failure by mutableStateOf<String?>(null); private set
    var screen by mutableStateOf(Screen.HOME); private set
    var kind by mutableStateOf(NoteKind.EXPERIENCE)
    var query by mutableStateOf("")
    var tag by mutableStateOf<String?>(null)
    var editor by mutableStateOf<Draft?>(null); private set
    var saved by mutableStateOf(false); private set
    var saveStatus by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set
    var leaveDialog by mutableStateOf(false)
    var pendingImport by mutableStateOf<Backup?>(null); private set
    val messages = Channel<String>(Channel.BUFFERED)
    private var lastWrite: Job? = null
    init {
        viewModelScope.launch {
            try { repo.notes.collect { records ->
                notes = records
                if (tag != null && records.none { !it.isDeleted && it.kind == kind && tag in it.tags }) tag = null
                ready = true
            } }
            catch (e: Exception) { failure = "无法读取本地记录，请重新打开应用。${e.message.orEmpty()}" }
        }
        viewModelScope.launch { try { repo.drafts.collect { drafts = it } } catch (e: Exception) { failure = "无法读取草稿：${e.message}" } }
        viewModelScope.launch { try { app.preferences.values.collect { preferences = it } } catch (e: Exception) { messages.send("设置读取失败：${e.message}") } }
    }
    val visibleNotes get() = selectNotes(notes, kind, query, tag, preferences.sort)
    val unfinished get() = drafts.filter { d ->
        d.kind == kind && !d.isEmpty && notes.none { it.id == d.id && !it.isDeleted && Draft.from(it) == d }
    }
    fun navigate(value: Screen) { screen = value }
    fun switchKind(value: NoteKind) { kind = value; tag = null; query = "" }
    fun create() { if (busy || !ready) return; open(Draft(kind = kind)) }
    fun edit(note: Note) { open(drafts.find { it.id == note.id } ?: Draft.from(note)) }
    fun open(draft: Draft) {
        editor = draft; saved = notes.any { it.id == draft.id }; screen = Screen.EDITOR
        saveStatus = if (saved) "已保存在本机" else if (draft.isEmpty) "" else "草稿已保存"
    }
    fun update(transform: (Draft) -> Draft) {
        if (busy) return
        val d = editor?.let(transform)?.copy(updatedAt = System.currentTimeMillis()) ?: return
        editor = d; saveStatus = "正在保存…"
        val previous = lastWrite
        lastWrite = viewModelScope.launch {
            previous?.join()
            try {
                val result = repo.saveEditor(d)
                if (editor == d) { saved = saved || result.saved; saveStatus = if (result.saved) "已自动保存" else "草稿已保存" }
            } catch (e: Exception) { if (editor?.id == d.id) saveStatus = "保存失败，请重试"; messages.send("保存失败：${e.message}") }
        }
    }
    fun finish() {
        val d = editor ?: return
        if (!d.isEmpty && d.error() != null || d.isEmpty && saved) { leaveDialog = true; return }
        action {
            lastWrite?.join()
            if (!d.isEmpty) { val result = repo.saveEditor(d, true); require(result.saved) { result.error.orEmpty() } }
            repo.clearDraft(d.id); editor = null; screen = Screen.HOME
        }
    }
    fun leaveDraft(discard: Boolean) {
        val d = editor ?: return
        action {
            lastWrite?.join()
            if (discard) repo.clearDraft(d.id) else repo.saveEditor(d)
            leaveDialog = false; editor = null; screen = Screen.HOME
        }
    }
    fun pin(note: Note) = action { lastWrite?.join(); repo.pin(note.id) }
    fun trash(note: Note, deleted: Boolean = true) = action {
        lastWrite?.join(); repo.trash(note.id, deleted)
        if (screen == Screen.EDITOR) { screen = Screen.HOME; editor = null }
        messages.send(if (deleted) "已移入回收站" else "已恢复记录")
    }
    fun purge(note: Note) = action { repo.purge(note.id); messages.send("已永久删除") }
    fun appearance(value: Appearance) = action { app.preferences.appearance(value) }
    fun dates(value: Boolean) = action { app.preferences.showDates(value) }
    fun sort(value: NoteSort) = action { app.preferences.sort(value) }
    fun prepareExport(format: String, type: NoteKind? = null, id: String? = null): String {
        state["exportFormat"] = format; state["exportKind"] = type?.wire; state["exportId"] = id
        return if (format == "json") "行思录-备份-${fileDate()}.json" else "行思录-${type?.label ?: "记录"}-${fileDate()}.md"
    }
    fun export(uri: Uri?) {
        if (uri == null) return
        val format = state.get<String>("exportFormat") ?: return
        val type = state.get<String>("exportKind")?.let(NoteKind::parse)
        val id = state.get<String>("exportId")
        action {
            lastWrite?.join()
            withContext(Dispatchers.IO) {
                val snapshot = repo.snapshot()
                val text = if (format == "json") BackupCodec.encode(snapshot, preferences) else {
                    val records = if (id == null) snapshot.records else snapshot.records.filter { it.id == id }
                    Markdown.encode(records, type ?: records.firstOrNull()?.kind ?: kind)
                }
                val output = app.contentResolver.openOutputStream(uri, "wt") ?: error("无法打开保存位置")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            }
            messages.send("已导出到所选位置")
        }
    }
    fun readImport(uri: Uri?) {
        if (uri == null) return
        action { pendingImport = withContext(Dispatchers.IO) {
            val input = app.contentResolver.openInputStream(uri) ?: error("无法打开文件")
            input.use(BackupCodec::read)
        } }
    }
    fun cancelImport() { pendingImport = null }
    fun confirmImport() {
        val backup = pendingImport ?: return
        action {
            val result = repo.merge(backup); pendingImport = null
            messages.send("已导入 ${result.added} 条记录、${result.drafts} 条草稿；跳过 ${result.skipped} 条重复记录。")
        }
    }
    private fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try { block() } catch (e: Exception) { if (e is CancellationException) throw e; messages.send(e.message ?: "操作失败，请重试") }
            finally { busy = false }
        }
    }
}
