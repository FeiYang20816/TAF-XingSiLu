package cn.xingsilu.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.xingsilu.app.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RepositoryTest {
    private lateinit var db: NoteDatabase
    private lateinit var repo: NoteRepository
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NoteDatabase::class.java).build(); repo = NoteRepository(db) }
    @After fun close() { db.close() }
    @Test fun experienceRequiresExplicitFirstSaveAndInvalidEditsPreserveSavedRecord() = runBlocking {
        val draft = Draft(summary = "记住这个经验", tagsInput = "生活，沟通")
        assertFalse(repo.saveEditor(draft).saved); assertNull(repo.note(draft.id)); assertEquals(1, repo.snapshot().drafts.size)
        assertTrue(repo.saveEditor(draft, true).saved); assertEquals(listOf("生活", "沟通"), repo.note(draft.id)!!.tags)
        assertTrue(repo.saveEditor(draft.copy(summary = "改过的总结", tagsInput = "思考")).saved)
        assertFalse(repo.saveEditor(draft.copy(summary = "")).saved)
        assertEquals("改过的总结", repo.note(draft.id)!!.summary)
        assertEquals(listOf("思考"), repo.note(draft.id)!!.tags)
    }
    @Test fun ideasAutosaveAndBlankNewRecordsAreNotStored() = runBlocking {
        repo.saveEditor(Draft()); assertEquals(Backup(emptyList()), repo.snapshot())
        val idea = Draft(kind = NoteKind.IDEA, content = "一个念头")
        assertTrue(repo.saveEditor(idea).saved); assertEquals("一个念头", repo.note(idea.id)!!.content)
        repo.clearDraft(idea.id); assertTrue(repo.snapshot().drafts.isEmpty())
    }
    @Test fun trashCanBeRestoredAndOnlyTrashedNotesCanBePermanentlyDeleted() = runBlocking {
        val d = Draft(summary = "要保留的经验", tagsInput = "标签")
        repo.saveEditor(d, true); repo.purge(d.id); assertNotNull(repo.note(d.id))
        repo.trash(d.id, true); assertTrue(repo.note(d.id)!!.isDeleted)
        repo.trash(d.id, false); assertFalse(repo.note(d.id)!!.isDeleted)
        repo.trash(d.id, true); repo.purge(d.id); assertNull(repo.note(d.id))
        assertTrue(repo.snapshot().drafts.isEmpty())
    }
    @Test fun mergeKeepsLocalEditsRemapsConflictsAndRepeatedImportsDoNotDuplicate() = runBlocking {
        val d = Draft(summary = "本机内容")
        repo.saveEditor(d, true); repo.clearDraft(d.id)
        val imported = d.toNote().copy(summary = "备份中的不同内容")
        val data = Backup(listOf(imported), listOf(Draft.from(imported).copy(content = "未完成修改")))
        val first = repo.merge(data)
        assertEquals(1, first.added); assertEquals(1, first.conflicts)
        val snapshot = repo.snapshot()
        assertEquals("本机内容", repo.note(d.id)!!.summary)
        val conflict = snapshot.records.single { it.summary == imported.summary }
        assertNotEquals(d.id, conflict.id); assertEquals(conflict.id, snapshot.drafts.single().id)
        assertEquals(0, repo.merge(data).added); assertEquals(2, repo.snapshot().records.size); assertEquals(1, repo.snapshot().drafts.size)
    }
    @Test fun recordsAndUnfinishedDraftsSurviveDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        val first = NoteDatabase.open(context, name)
        val d = Draft(content = "还没有总结的详情")
        val idea = Draft(kind = NoteKind.IDEA, content = "持久保存的想法")
        NoteRepository(first).saveEditor(d); NoteRepository(first).saveEditor(idea); first.close()
        val second = NoteDatabase.open(context, name)
        try { val snapshot = NoteRepository(second).snapshot(); assertEquals(idea.content, snapshot.records.single().content); assertEquals(2, snapshot.drafts.size) }
        finally { second.close(); context.deleteDatabase(name) }
    }
    @Test fun importedOrphanDraftCannotOverwriteAnUnrelatedLocalRecord() = runBlocking {
        val d = Draft(summary = "本机的经验")
        repo.saveEditor(d, true); repo.clearDraft(d.id)
        repo.merge(Backup(emptyList(), listOf(d.copy(summary = "外部草稿"))))
        val imported = repo.snapshot().drafts.single()
        assertNotEquals(d.id, imported.id)
        repo.saveEditor(imported, true)
        assertEquals("本机的经验", repo.note(d.id)!!.summary)
        assertEquals(2, repo.snapshot().records.size)
    }
    @Test fun preferencesSurviveClosingAndReopeningTheirOwner() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = PreferencesRepository(context)
        try { first.appearance(Appearance.DARK); first.showDates(true); first.sort(NoteSort.CREATED) }
        finally { first.close() }
        val reopened = PreferencesRepository(context)
        try { assertEquals(UserPreferences(Appearance.DARK, true, NoteSort.CREATED), reopened.values.first()) }
        finally { reopened.close() }
    }
}
