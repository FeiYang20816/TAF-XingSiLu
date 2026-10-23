package cn.xingsilu.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import cn.xingsilu.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AppUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app: XingSiLuApplication get() = ApplicationProvider.getApplicationContext()
    private val vm get() = ViewModelProvider(compose.activity)[AppViewModel::class.java]
    @Before fun ready() {
        compose.waitUntil(15000) { vm.ready }; assertNull(vm.failure)
        // Keep visual checks deterministic, independently of execution order.
        runBlocking { app.preferences.appearance(Appearance.LIGHT); app.preferences.showDates(false); app.preferences.sort(NoteSort.UPDATED) }
        compose.waitUntil(10000) { vm.preferences == UserPreferences() }
    }
    @After fun release() { compose.activityRule.scenario.close(); runBlocking { app.preferences.close() }; app.database.close() }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val file = File(System.getProperty("xingsilu.screenshots"), "$name.png").apply { parentFile!!.mkdirs() }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap)); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Test fun createSearchEditTrashRestoreAndDatePreference() {
        assertTrue(vm.notes.isEmpty()); screenshot("01-empty")
        compose.onNodeWithContentDescription("新建经验").performClick()
        compose.onNodeWithTag("summary").performTextInput("先把问题听完，再给出建议。")
        compose.onNodeWithTag("details").performTextInput("一次沟通中，我发现先听对方说完能减少误解。")
        screenshot("02-experience-editor")
        compose.onNodeWithTag("finish").performClick()
        compose.waitUntil(10000) { vm.screen == Screen.HOME && vm.notes.size == 1 }
        compose.onNodeWithText("一次沟通中，我发现先听对方说完能减少误解。").assertDoesNotExist()
        compose.onNodeWithTag("search").performTextInput("减少误解")
        compose.onNodeWithText("先把问题听完，再给出建议。").assertIsDisplayed()
        compose.onNodeWithContentDescription("清空搜索").performClick()
        screenshot("03-experiences")
        compose.onNodeWithText("先把问题听完，再给出建议。").performClick()
        compose.onNodeWithTag("summary").performTextReplacement("先理解对方，再表达自己。")
        compose.waitUntil(10000) { vm.notes.single().summary == "先理解对方，再表达自己。" }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("summary").assertTextEquals("先理解对方，再表达自己。")
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("移入回收站").performClick()
        compose.onNodeWithText("移入回收站").performClick()
        compose.waitUntil(10000) { vm.screen == Screen.HOME && vm.notes.single().isDeleted }
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("回收站").performScrollTo().performClick()
        compose.onNodeWithText("恢复").performClick()
        compose.waitUntil(10000) { !vm.notes.single().isDeleted }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("showDates").performScrollTo().performClick()
        compose.waitUntil(10000) { vm.preferences.showDates }
        screenshot("04-settings")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText(displayDate(vm.notes.single().updatedAt)).assertIsDisplayed()
    }
    @Test fun ideaAutosavesTwoLinePreviewAndBackupRoundTrip() {
        compose.onNodeWithText("想法").performClick()
        compose.onNodeWithContentDescription("新建想法").performClick()
        val body = "把散步时突然冒出来的想法记在这里。\n也许可以从一个很小的尝试开始。\n第三行只在详情里完整阅读。"
        compose.onNodeWithTag("idea").performTextInput(body)
        compose.waitUntil(10000) { vm.notes.singleOrNull()?.content == body }
        screenshot("05-idea-editor")
        compose.onNodeWithTag("finish").performClick()
        compose.waitUntil(10000) { vm.screen == Screen.HOME && !vm.busy }
        screenshot("06-ideas")
        // Check text layout rather than only the stored text: list is clamped to two lines.
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(body).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(2, layouts.single().lineCount)
        val backup = File(app.cacheDir, "roundtrip.json")
        compose.runOnIdle { vm.prepareExport("json"); vm.export(Uri.fromFile(backup)) }
        compose.waitUntil(10000) { !vm.busy && backup.exists() }
        val decoded = BackupCodec.decode(backup.readText())
        assertEquals(body, decoded.records.single().content)
        compose.runOnIdle { vm.readImport(Uri.fromFile(backup)) }
        compose.waitUntil(10000) { vm.pendingImport != null && !vm.busy }
        compose.runOnIdle { vm.confirmImport() }
        compose.waitUntil(10000) { vm.pendingImport == null && !vm.busy }
        assertEquals(1, vm.notes.size)
        compose.runOnIdle { vm.appearance(Appearance.DARK) }
        compose.waitUntil(10000) { vm.preferences.appearance == Appearance.DARK }
        screenshot("07-dark")
    }
    @Test fun unfinishedExperienceSurvivesLeavingAndCanBeCompleted() {
        compose.onNodeWithContentDescription("新建经验").performClick()
        compose.onNodeWithTag("details").performTextInput("先记下经过，晚点再总结。")
        compose.onNodeWithTag("finish").performClick()
        compose.onNodeWithText("保留草稿并返回").performClick()
        compose.waitUntil(10000) { vm.screen == Screen.HOME && !vm.busy }
        assertTrue(vm.notes.isEmpty())
        compose.onNodeWithText("继续未完成的记录（1）").performClick()
        compose.onNodeWithTag("details").assertTextEquals("先记下经过，晚点再总结。")
        compose.onNodeWithTag("summary").performTextInput("给自己一点整理的时间。")
        compose.onNodeWithTag("finish").performClick()
        compose.waitUntil(10000) { vm.screen == Screen.HOME && vm.notes.size == 1 && vm.drafts.isEmpty() }
        assertEquals("先记下经过，晚点再总结。", vm.notes.single().content)
    }
}
