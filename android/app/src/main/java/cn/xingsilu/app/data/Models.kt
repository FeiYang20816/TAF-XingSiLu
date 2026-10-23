package cn.xingsilu.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class NoteKind(val wire: String, val label: String) {
    EXPERIENCE("experience", "经验"), IDEA("idea", "想法");
    companion object { fun parse(value: String) = entries.firstOrNull { it.wire == value } ?: error("未知记录类型") }
}
enum class Appearance(val label: String) { LIGHT("浅色"), DARK("深色"), SYSTEM("跟随系统") }
enum class NoteSort(val label: String) { UPDATED("最近更新"), CREATED("最近创建") }
data class UserPreferences(val appearance: Appearance = Appearance.LIGHT, val showDates: Boolean = false, val sort: NoteSort = NoteSort.UPDATED)
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val kind: NoteKind = NoteKind.EXPERIENCE,
    val summary: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val eventDate: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
) {
    val title get() = if (kind == NoteKind.EXPERIENCE) summary else content
}
data class Draft(
    val id: String = UUID.randomUUID().toString(),
    val kind: NoteKind = NoteKind.EXPERIENCE,
    val summary: String = "",
    val content: String = "",
    val tagsInput: String = "",
    val eventDate: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
) {
    val isEmpty get() = summary.isBlank() && content.isBlank() && tagsInput.isBlank() && eventDate.isBlank()
    fun error(): String? = when {
        kind == NoteKind.EXPERIENCE && summary.isBlank() -> "写一句话总结，就可以保存这条经验。"
        kind == NoteKind.IDEA && content.isBlank() -> "写下一点想法，再保存吧。"
        eventDate.isNotBlank() && !validDate(eventDate) -> "相关日期请填写有效的 YYYY-MM-DD 日期，或留空。"
        else -> null
    }
    fun toNote(previous: Note? = null): Note = Note(id, kind, summary.trim(), content, parseTags(tagsInput),
        if (kind == NoteKind.EXPERIENCE) eventDate.trim() else "", createdAt, updatedAt,
        previous?.isPinned ?: false, previous?.isDeleted ?: false)
    companion object { fun from(note: Note) = Draft(note.id, note.kind, note.summary, note.content, note.tags.joinToString("，"), note.eventDate, note.createdAt, note.updatedAt) }
}
fun validDate(value: String): Boolean = runCatching { LocalDate.parse(value.trim()); true }.getOrDefault(false)
fun parseTags(value: String): List<String> = value.split(Regex("[,，、\\n]")).map(String::trim).filter(String::isNotEmpty).distinct()
fun displayDate(at: Long, withTime: Boolean = false): String = DateTimeFormatter.ofPattern(if (withTime) "yyyy/MM/dd HH:mm" else "yyyy/MM/dd")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))
fun fileDate(): String = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
fun selectNotes(notes: List<Note>, kind: NoteKind, query: String, tag: String?, sort: NoteSort): List<Note> {
    val key = query.trim()
    return notes.filter { !it.isDeleted && it.kind == kind && (tag == null || tag in it.tags) &&
        (key.isEmpty() || listOf(it.summary, it.content, it.tags.joinToString(" ")).any { text -> text.contains(key, ignoreCase = true) }) }
        .sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { if (sort == NoteSort.CREATED) it.createdAt else it.updatedAt }.thenBy { it.id })
}
