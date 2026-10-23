package cn.xingsilu.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.map
import java.util.UUID

data class SaveResult(val saved: Boolean, val error: String? = null)
data class ImportResult(val added: Int, val skipped: Int, val conflicts: Int, val drafts: Int)
class NoteRepository(val database: NoteDatabase) {
    private val dao = database.dao()
    val notes = dao.observeNotes().map { it.map(JoinedNote::toNote) }
    val drafts = dao.observeDrafts().map { it.map(DraftRow::toDraft) }
    suspend fun snapshot(): Backup = database.withTransaction { Backup(dao.allNotes().map(JoinedNote::toNote), dao.allDrafts().map(DraftRow::toDraft)) }
    suspend fun note(id: String) = dao.findNote(id)?.toNote()
    private suspend fun write(note: Note) {
        dao.upsertNote(note.toRow()); dao.deleteLinks(note.id)
        dao.insertTags(note.tags.map(::TagRow)); dao.insertLinks(note.tags.mapIndexed { i, tag -> NoteTagRow(note.id, tag, i) })
    }
    suspend fun saveEditor(draft: Draft, force: Boolean = false): SaveResult = database.withTransaction {
        val old = dao.findNote(draft.id)?.toNote()
        if (draft.isEmpty && old == null) { dao.deleteDraft(draft.id); return@withTransaction SaveResult(false) }
        dao.upsertDraft(draft.toRow())
        val error = draft.error()
        if (error != null) return@withTransaction SaveResult(false, error)
        if (old?.isDeleted == true) return@withTransaction SaveResult(false, "这条记录已在回收站，草稿已保留。")
        if (force || draft.kind == NoteKind.IDEA || old != null) {
            write(draft.toNote(old)); dao.deleteUnusedTags(); SaveResult(true)
        } else SaveResult(false)
    }
    suspend fun clearDraft(id: String) { dao.deleteDraft(id) }
    suspend fun pin(id: String) = database.withTransaction {
        val old = dao.findNote(id)?.toNote() ?: return@withTransaction
        write(old.copy(isPinned = !old.isPinned, updatedAt = System.currentTimeMillis()))
    }
    suspend fun trash(id: String, deleted: Boolean) = database.withTransaction {
        val old = dao.findNote(id)?.toNote() ?: return@withTransaction
        write(old.copy(isDeleted = deleted, updatedAt = System.currentTimeMillis()))
        if (deleted) dao.deleteDraft(id)
    }
    suspend fun purge(id: String) = database.withTransaction {
        if (dao.findNote(id)?.row?.isDeleted != true) return@withTransaction
        dao.deletePermanently(id); dao.deleteDraft(id); dao.deleteUnusedTags()
    }
    suspend fun merge(backup: Backup): ImportResult = database.withTransaction {
        val local = dao.allNotes().map(JoinedNote::toNote).toMutableList()
        val localDrafts = dao.allDrafts().map(DraftRow::toDraft).toMutableList()
        val remap = mutableMapOf<String, String>()
        var added = 0; var skipped = 0; var conflicts = 0; var draftsAdded = 0
        backup.records.forEach { incoming ->
            val byId = local.find { it.id == incoming.id }
            val same = local.find { it.copy(id = incoming.id) == incoming }
            if (same != null) { remap[incoming.id] = same.id; skipped++ }
            else {
                val n = if (byId == null) incoming else incoming.copy(id = UUID.randomUUID().toString()).also { conflicts++ }
                remap[incoming.id] = n.id; write(n); local.add(n); added++
            }
        }
        backup.drafts.forEach { incoming ->
            val mapped = incoming.copy(id = remap[incoming.id] ?: incoming.id)
            if (localDrafts.none { it.copy(id = mapped.id) == mapped }) {
                // An orphaned imported draft must never attach itself to an unrelated local record.
                val collision = localDrafts.any { it.id == mapped.id } ||
                    (incoming.id !in remap && local.any { it.id == mapped.id })
                val value = if (collision) mapped.copy(id = UUID.randomUUID().toString()) else mapped
                dao.upsertDraft(value.toRow()); localDrafts.add(value); draftsAdded++
            }
        }
        ImportResult(added, skipped, conflicts, draftsAdded)
    }
}
