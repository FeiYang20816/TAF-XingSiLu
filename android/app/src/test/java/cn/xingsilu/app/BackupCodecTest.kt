package cn.xingsilu.app

import cn.xingsilu.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupCodecTest {
    private val experience = Note(id = "exp", summary = "先理解，再表达", content = "第一段。\n\n第二段。", tags = listOf("沟通", "生活"), eventDate = "2026-10-22", createdAt = 1700000000000L, updatedAt = 1700000001000L, isPinned = true)
    @Test fun backupRoundTripPreservesDeletedRecordsAndRawDrafts() {
        val original = Backup(listOf(experience, Note(kind = NoteKind.IDEA, content = "突然想到的事", isDeleted = true)), listOf(Draft(content = "还没写总结", tagsInput = "未完成， ")))
        assertEquals(original, BackupCodec.read(ByteArrayInputStream(BackupCodec.encode(original, UserPreferences()).toByteArray(Charsets.UTF_8))))
    }
    @Test fun malformedOrDuplicateRecordsAreRejected() {
        val encoded = BackupCodec.encode(Backup(listOf(experience)), UserPreferences())
        listOf("{}", encoded.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
            encoded.replace("\"isPinned\": true", "\"isPinned\": \"true\""),
            BackupCodec.encode(Backup(listOf(experience, experience)), UserPreferences()),
            encoded.replace("2026-10-22", "2026-02-30")).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(invalid) }
        }
    }
    @Test fun markdownKeepsParagraphsAndOmitsTrash() {
        val md = Markdown.encode(listOf(experience, experience.copy(id = "trash", summary = "不应导出", isDeleted = true)), NoteKind.EXPERIENCE)
        assertTrue(md.contains("## 先理解，再表达")); assertTrue(md.contains("第一段。\n\n第二段。")); assertFalse(md.contains("不应导出"))
    }
    @Test fun searchIncludesDetailsAndTagsAndKeepsPinsFirst() {
        val notes = listOf(experience.copy(isPinned = false, updatedAt = 2000), experience.copy(id = "pinned", summary = "别的总结", isPinned = true, updatedAt = 1000), experience.copy(id = "trash", isDeleted = true))
        assertEquals(listOf("pinned", "exp"), selectNotes(notes, NoteKind.EXPERIENCE, "第二段", null, NoteSort.UPDATED).map { it.id })
        assertEquals(2, selectNotes(notes, NoteKind.EXPERIENCE, "沟通", "生活", NoteSort.CREATED).size)
        assertTrue(selectNotes(notes, NoteKind.IDEA, "", null, NoteSort.UPDATED).isEmpty())
    }
    @Test fun validatesRequiredFieldsAndOptionalDates() {
        assertNotNull(Draft(content = "只有详情").error())
        assertNull(Draft(summary = "总结").error())
        assertNull(Draft(kind = NoteKind.IDEA, content = "想法").error())
        assertFalse(validDate("2026-02-30")); assertTrue(validDate("2024-02-29"))
        assertEquals(listOf("甲", "乙"), parseTags("甲，乙,甲\n "))
    }
}
