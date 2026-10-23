package cn.xingsilu.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes")
data class NoteRow(@PrimaryKey val id: String, val kind: String, val summary: String, val content: String,
    val eventDate: String, val createdAt: Long, val updatedAt: Long, val isPinned: Boolean, val isDeleted: Boolean)
@Entity(tableName = "tags")
data class TagRow(@PrimaryKey val name: String)
@Entity(tableName = "note_tags", primaryKeys = ["noteId", "tagName"],
    foreignKeys = [ForeignKey(entity = NoteRow::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagRow::class, parentColumns = ["name"], childColumns = ["tagName"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tagName")])
data class NoteTagRow(val noteId: String, val tagName: String, val position: Int)
data class JoinedNote(
    @Embedded val row: NoteRow,
    @Relation(parentColumn = "id", entityColumn = "noteId") val links: List<NoteTagRow>,
) {
    fun toNote() = Note(row.id, NoteKind.parse(row.kind), row.summary, row.content,
        links.sortedBy { it.position }.map { it.tagName }, row.eventDate, row.createdAt, row.updatedAt, row.isPinned, row.isDeleted)
}
@Entity(tableName = "drafts")
data class DraftRow(@PrimaryKey val id: String, val kind: String, val summary: String, val content: String,
    val tagsInput: String, val eventDate: String, val createdAt: Long, val updatedAt: Long) {
    fun toDraft() = Draft(id, NoteKind.parse(kind), summary, content, tagsInput, eventDate, createdAt, updatedAt)
}
fun Note.toRow() = NoteRow(id, kind.wire, summary, content, eventDate, createdAt, updatedAt, isPinned, isDeleted)
fun Draft.toRow() = DraftRow(id, kind.wire, summary, content, tagsInput, eventDate, createdAt, updatedAt)

@Dao
interface NoteDao {
    @Transaction @Query("SELECT * FROM notes") fun observeNotes(): Flow<List<JoinedNote>>
    @Transaction @Query("SELECT * FROM notes") suspend fun allNotes(): List<JoinedNote>
    @Transaction @Query("SELECT * FROM notes WHERE id = :id") suspend fun findNote(id: String): JoinedNote?
    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC") fun observeDrafts(): Flow<List<DraftRow>>
    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC") suspend fun allDrafts(): List<DraftRow>
    @Upsert suspend fun upsertNote(row: NoteRow)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTags(tags: List<TagRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLinks(links: List<NoteTagRow>)
    @Query("DELETE FROM note_tags WHERE noteId = :id") suspend fun deleteLinks(id: String)
    @Upsert suspend fun upsertDraft(row: DraftRow)
    @Query("DELETE FROM drafts WHERE id = :id") suspend fun deleteDraft(id: String)
    @Query("DELETE FROM notes WHERE id = :id AND isDeleted = 1") suspend fun deletePermanently(id: String)
    @Query("DELETE FROM tags WHERE name NOT IN (SELECT tagName FROM note_tags)") suspend fun deleteUnusedTags()
}
@Database(entities = [NoteRow::class, TagRow::class, NoteTagRow::class, DraftRow::class], version = 1, exportSchema = true)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun dao(): NoteDao
    companion object {
        fun open(context: Context, name: String = "xingsilu.db") = Room.databaseBuilder(context.applicationContext, NoteDatabase::class.java, name)
            // Never use destructive migration: future upgrades must provide a migration.
            .build()
    }
}
