@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cn.xingsilu.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import cn.xingsilu.app.*
import cn.xingsilu.app.data.*

private val LightColors = lightColorScheme(
    primary = Color(0xFF5C755D), onPrimary = Color.White, primaryContainer = Color(0xFFE2E9DC),
    onPrimaryContainer = Color(0xFF354D38), background = Color(0xFFF5F5EF), onBackground = Color(0xFF30352F),
    secondary = Color(0xFF5C755D), onSecondary = Color.White, secondaryContainer = Color(0xFFE2E9DC), onSecondaryContainer = Color(0xFF354D38),
    surface = Color(0xFFF5F5EF), onSurface = Color(0xFF30352F), surfaceVariant = Color(0xFFECEEE5),
    onSurfaceVariant = Color(0xFF737B70), outline = Color(0xFFBFC7B9), surfaceContainer = Color(0xFFFDFDF8),
    surfaceContainerLow = Color(0xFFF9FAF4), surfaceContainerHigh = Color(0xFFECEEE5), surfaceContainerHighest = Color(0xFFE3E7DC), surfaceTint = Color(0xFF5C755D),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB2C9A8), onPrimary = Color(0xFF1D321F), primaryContainer = Color(0xFF344B36), onPrimaryContainer = Color(0xFFD8E8D1),
    secondary = Color(0xFFB2C9A8), onSecondary = Color(0xFF1D321F), secondaryContainer = Color(0xFF344B36), onSecondaryContainer = Color(0xFFD8E8D1),
    background = Color(0xFF1B211C), onBackground = Color(0xFFE3E7DE), surface = Color(0xFF1B211C),
    onSurface = Color(0xFFE3E7DE), surfaceVariant = Color(0xFF2B342C), onSurfaceVariant = Color(0xFFA6B1A2),
    surfaceContainer = Color(0xFF252E26), outline = Color(0xFF586653),
    surfaceContainerLow = Color(0xFF222A23), surfaceContainerHigh = Color(0xFF2B342C), surfaceContainerHighest = Color(0xFF343E35), surfaceTint = Color(0xFFB2C9A8),
)

@Composable
fun XingSiLuApp(vm: AppViewModel) {
    val dark = when (vm.preferences.appearance) { Appearance.LIGHT -> false; Appearance.DARK -> true; Appearance.SYSTEM -> isSystemInDarkTheme() }
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let {
            WindowCompat.getInsetsController(it, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm) { for (message in vm.messages) snackbar.showSnackbar(message) }
        val markdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown"), vm::export)
        val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), vm::export)
        val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::readImport)
        var deleteTarget by remember { mutableStateOf<Note?>(null) }
        var purgeTarget by remember { mutableStateOf<Note?>(null) }
        BackHandler(vm.screen != Screen.HOME) { if (!vm.busy) { if (vm.screen == Screen.EDITOR) vm.finish() else vm.navigate(Screen.HOME) } }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { if (vm.screen == Screen.HOME && vm.ready) BottomTabs(vm) },
            floatingActionButton = {
                if (vm.screen == Screen.HOME && vm.ready && vm.failure == null) FloatingActionButton(onClick = vm::create,
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Icon(Icons.Outlined.Add, "新建${vm.kind.label}")
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    vm.failure != null -> EmptyState("暂时无法读取记录", vm.failure.orEmpty(), Icons.Outlined.ErrorOutline)
                    !vm.ready -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else -> when (vm.screen) {
                        Screen.HOME -> Home(vm)
                        Screen.EDITOR -> Editor(vm, onDelete = { deleteTarget = it }, onExport = { note ->
                            markdown.launch(vm.prepareExport("md", note.kind, note.id))
                        })
                        Screen.SETTINGS -> Settings(vm,
                            onMarkdown = { markdown.launch(vm.prepareExport("md", it)) },
                            onBackup = { backup.launch(vm.prepareExport("json")) },
                            onRestore = { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
                        Screen.TRASH -> Trash(vm, onPurge = { purgeTarget = it })
                    }
                }
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
        if (vm.leaveDialog) AlertDialog(
            onDismissRequest = { if (!vm.busy) vm.leaveDialog = false }, title = { Text("保留这份草稿？") },
            text = { Text("${vm.editor?.error().orEmpty()}\n未完成的内容可以保留为草稿，稍后继续。之前已保存的记录会保留。") },
            confirmButton = { TextButton(enabled = !vm.busy, onClick = { vm.leaveDraft(false) }) { Text("保留草稿并返回") } },
            dismissButton = { Row { TextButton(enabled = !vm.busy, onClick = { vm.leaveDialog = false }) { Text("继续编辑") }; TextButton(enabled = !vm.busy, onClick = { vm.leaveDraft(true) }) { Text("丢弃草稿") } } },
        )
        deleteTarget?.let { note -> ConfirmDialog("移入回收站？", "这条${note.kind.label}可以在设置中的回收站恢复。", "移入回收站", vm.busy,
            { deleteTarget = null }, { vm.trash(note); deleteTarget = null }) }
        purgeTarget?.let { note -> ConfirmDialog("永久删除？", "删除后无法恢复这条记录。", "永久删除", vm.busy,
            { purgeTarget = null }, { vm.purge(note); purgeTarget = null }) }
        vm.pendingImport?.let { data -> ConfirmDialog("恢复备份", "文件包含 ${data.records.size} 条记录、${data.drafts.size} 条草稿。\n\n现有记录会保留；相同记录会跳过，有冲突的记录会作为新记录导入。界面偏好保持当前设置。", "导入", vm.busy,
            vm::cancelImport, vm::confirmImport) }
    }
}

@Composable private fun BottomTabs(vm: AppViewModel) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        NoteKind.entries.forEach { kind -> NavigationBarItem(selected = vm.kind == kind, onClick = { vm.switchKind(kind) },
            icon = { Icon(if (kind == NoteKind.EXPERIENCE) Icons.AutoMirrored.Outlined.MenuBook else Icons.Outlined.Lightbulb, null) }, label = { Text(kind.label) }) }
    }
}
@Composable private fun Home(vm: AppViewModel) {
    val notes = vm.visibleNotes
    val tags = vm.notes.filter { !it.isDeleted && it.kind == vm.kind }.flatMap { it.tags }.distinct().sorted()
    var sortMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().widthIn(max = 760.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 26.dp, end = 14.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Spa, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text("行思录", Modifier.padding(start = 9.dp).weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { vm.navigate(Screen.SETTINGS) }) { Icon(Icons.Outlined.Settings, "设置") }
        }
        Row(Modifier.padding(horizontal = 26.dp, vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("我的${vm.kind.label}", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text(if (vm.kind == NoteKind.EXPERIENCE) "把经历，慢慢变成自己的经验。" else "留住此刻，一闪而过的念头。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            Box {
                TextButton(onClick = { sortMenu = true }, contentPadding = PaddingValues(start = 8.dp)) { Text(vm.preferences.sort.label, fontSize = 11.sp); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp)) }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    NoteSort.entries.forEach { sort -> DropdownMenuItem(text = { Text(sort.label) }, onClick = { vm.sort(sort); sortMenu = false }) }
                }
            }
        }
        OutlinedTextField(value = vm.query, onValueChange = { vm.query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).testTag("search"),
            placeholder = { Text("搜索${vm.kind.label}、详情或标签", fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(21.dp)) },
            trailingIcon = { if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) { Icon(Icons.Outlined.Close, "清空搜索") } },
            shape = RoundedCornerShape(18.dp), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)))
        if (tags.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(selected = vm.tag == null, onClick = { vm.tag = null }, label = { Text("全部") })
            tags.forEach { tag -> FilterChip(selected = vm.tag == tag, onClick = { vm.tag = if (vm.tag == tag) null else tag }, label = { Text(tag) }) }
        }
        if (vm.unfinished.isNotEmpty()) TextButton(onClick = { vm.open(vm.unfinished.first()) }, modifier = Modifier.padding(horizontal = 18.dp)) {
            Icon(Icons.Outlined.EditNote, null); Text("继续未完成的记录（${vm.unfinished.size}）", Modifier.padding(start = 6.dp), fontSize = 13.sp)
        }
        if (notes.isEmpty()) {
            val filtered = vm.query.isNotBlank() || vm.tag != null
            EmptyState(if (filtered) "没有找到相关记录" else if (vm.kind == NoteKind.EXPERIENCE) "每一点体会，都值得留下" else "想到什么，就写下来",
                if (filtered) "试试其他关键词，或清除筛选。" else "点右下角的 ＋，记下第一条${vm.kind.label}。",
                if (vm.kind == NoteKind.EXPERIENCE) Icons.AutoMirrored.Outlined.MenuBook else Icons.Outlined.Lightbulb, Modifier.weight(1f))
        } else LazyColumn(Modifier.fillMaxSize().testTag("notes"), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(notes, key = { it.id }) { NoteCard(it, vm.preferences.showDates, { vm.edit(it) }) }
        }
    }
}

/** Several translucent concentric fills feather the white surface into its surroundings. */
private fun Modifier.feather(color: Color): Modifier = drawBehind {
    val feather = 9.dp.toPx()
    for (step in 0..15) {
        val inset = feather * step / 15f
        drawRoundRect(color.copy(alpha = 0.15f), topLeft = Offset(inset, inset), size = Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f)), cornerRadius = CornerRadius((24.dp.toPx() - inset).coerceAtLeast(0f)))
    }
}
@Composable private fun NoteCard(note: Note, showDates: Boolean, onClick: () -> Unit) {
    val color = if (MaterialTheme.colorScheme === LightColors) Color.White else MaterialTheme.colorScheme.surfaceContainer
    Column(Modifier.fillMaxWidth().feather(color).clickable(onClick = onClick).padding(horizontal = 23.dp, vertical = 23.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(note.title, Modifier.weight(1f), fontSize = 19.sp, lineHeight = 29.sp, fontWeight = if (note.kind == NoteKind.EXPERIENCE) FontWeight.Medium else FontWeight.Normal,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (note.isPinned) Icon(Icons.Outlined.PushPin, "已置顶", Modifier.padding(start = 8.dp, top = 5.dp).size(14.dp), tint = MaterialTheme.colorScheme.primary)
        }
        if (note.tags.isNotEmpty() || showDates) Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(note.tags.joinToString("   ") { "# $it" }, Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showDates) Text(displayDate(note.updatedAt), Modifier.padding(start = 8.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun Editor(vm: AppViewModel, onDelete: (Note) -> Unit, onExport: (Note) -> Unit) {
    val draft = vm.editor ?: return
    val note = vm.notes.find { it.id == draft.id }
    var metadata by rememberSaveable(draft.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(draft.id) { if (draft.isEmpty) focus.requestFocus() }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::finish, enabled = !vm.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
            Column(Modifier.weight(1f)) {
                Text(if (note == null) "写${draft.kind.label}" else "${draft.kind.label}记录", fontSize = 15.sp)
                if (vm.saveStatus.isNotBlank()) Text(vm.saveStatus, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (note != null) Box {
                IconButton(onClick = { menu = true }, enabled = !vm.busy) { Icon(Icons.Outlined.MoreHoriz, "更多操作") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(if (note.isPinned) "取消置顶" else "置顶") }, onClick = { menu = false; vm.pin(note) })
                    DropdownMenuItem(text = { Text("导出为 Markdown") }, onClick = { menu = false; onExport(note) })
                    DropdownMenuItem(text = { Text("移入回收站") }, onClick = { menu = false; onDelete(note) })
                }
            }
            TextButton(onClick = vm::finish, enabled = !vm.busy, modifier = Modifier.testTag("finish")) { Text(if (!vm.saved && draft.kind == NoteKind.EXPERIENCE) "保存" else "完成") }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp)) {
            if (draft.kind == NoteKind.EXPERIENCE) {
                Text("一句话总结", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 18.dp))
                WritingField(draft.summary, { text -> vm.update { it.copy(summary = text) } }, "这次经历，让你明白了什么？", 28, Modifier.focusRequester(focus).testTag("summary"), enabled = !vm.busy)
                HorizontalDivider(Modifier.padding(vertical = 30.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Text("详情 · 可选", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
                WritingField(draft.content, { text -> vm.update { it.copy(content = text) } }, "事情的经过、当时的感受，或下次想做的改变……", 16, Modifier.heightIn(min = 170.dp).testTag("details"), enabled = !vm.busy)
            } else {
                Text("此刻的想法", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 20.dp))
                WritingField(draft.content, { text -> vm.update { it.copy(content = text) } }, "不必想好标题，\n从脑海中的第一句话开始。", 23, Modifier.heightIn(min = 250.dp).focusRequester(focus).testTag("idea"), enabled = !vm.busy)
            }
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { metadata = !metadata }, contentPadding = PaddingValues(0.dp)) {
                Icon(if (metadata) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                Text("标签与日期 · 可选", Modifier.padding(start = 6.dp), fontSize = 12.sp)
            }
            if (metadata) {
                OutlinedTextField(value = draft.tagsInput, onValueChange = { text -> vm.update { it.copy(tagsInput = text) } }, label = { Text("标签，用逗号分隔") }, modifier = Modifier.fillMaxWidth().testTag("tags"), enabled = !vm.busy, shape = RoundedCornerShape(14.dp))
                if (draft.kind == NoteKind.EXPERIENCE) OutlinedTextField(value = draft.eventDate, onValueChange = { text -> vm.update { it.copy(eventDate = text) } }, label = { Text("相关日期 · YYYY-MM-DD") },
                    isError = draft.eventDate.isNotBlank() && !validDate(draft.eventDate), modifier = Modifier.fillMaxWidth().padding(top = 12.dp), enabled = !vm.busy, singleLine = true, shape = RoundedCornerShape(14.dp))
                Text("创建于 ${displayDate(draft.createdAt, true)}", Modifier.padding(top = 16.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (note != null) Text("更新于 ${displayDate(note.updatedAt, true)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}
@Composable private fun WritingField(value: String, onChange: (String) -> Unit, placeholder: String, size: Int, modifier: Modifier = Modifier, enabled: Boolean) {
    BasicTextField(value = value, onValueChange = onChange, enabled = enabled, modifier = modifier.fillMaxWidth(),
        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = size.sp, lineHeight = (size * 1.6).sp), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = size.sp, lineHeight = (size * 1.6).sp); inner() } })
}

@Composable private fun Settings(vm: AppViewModel, onMarkdown: (NoteKind) -> Unit, onBackup: () -> Unit, onRestore: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageTitle("设置", { vm.navigate(Screen.HOME) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text("按自己的习惯，安静地记录。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 28.dp))
            SectionLabel("阅读与外观")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Appearance.entries.forEach { appearance -> FilterChip(selected = vm.preferences.appearance == appearance, onClick = { vm.appearance(appearance) }, label = { Text(appearance.label) }, enabled = !vm.busy) } }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("列表显示时间", fontSize = 15.sp); Text("默认隐藏，让内容更醒目", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(checked = vm.preferences.showDates, onCheckedChange = vm::dates, enabled = !vm.busy, modifier = Modifier.testTag("showDates"))
            }
            SectionLabel("带走你的记录")
            SettingsRow(Icons.Outlined.FileDownload, "导出全部经验", "Markdown 文本，方便阅读与整理", !vm.busy) { onMarkdown(NoteKind.EXPERIENCE) }
            SettingsRow(Icons.Outlined.FileDownload, "导出全部想法", "Markdown 文本，保留原始段落", !vm.busy) { onMarkdown(NoteKind.IDEA) }
            SettingsRow(Icons.Outlined.Backup, "完整备份", "包含记录、回收站与未完成草稿", !vm.busy, onBackup)
            SettingsRow(Icons.Outlined.Restore, "从备份恢复", "读取行思录 JSON 备份", !vm.busy, onRestore)
            SectionLabel("整理")
            SettingsRow(Icons.Outlined.DeleteOutline, "回收站", "${vm.notes.count { it.isDeleted }} 条记录", !vm.busy) { vm.navigate(Screen.TRASH) }
            HorizontalDivider(Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Text("行思录", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            Text("1.0.0 · 首版预览\n所有记录保存在本机，无需联网或登录。\n卸载前请导出完整备份。", Modifier.padding(top = 8.dp, bottom = 36.dp), fontSize = 12.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun Trash(vm: AppViewModel, onPurge: (Note) -> Unit) {
    val deleted = vm.notes.filter { it.isDeleted }.sortedByDescending { it.updatedAt }
    Column(Modifier.fillMaxSize()) {
        PageTitle("回收站", { vm.navigate(Screen.SETTINGS) })
        if (deleted.isEmpty()) EmptyState("回收站是空的", "移除的记录会暂时保留在这里。", Icons.Outlined.DeleteOutline)
        else LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(deleted, key = { it.id }) { note ->
                Column(Modifier.fillMaxWidth().feather(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp)) {
                    Text(note.title, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(note.kind.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(enabled = !vm.busy, onClick = { vm.trash(note, false) }) { Text("恢复") }
                        TextButton(enabled = !vm.busy, onClick = { onPurge(note) }) { Text("永久删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
@Composable private fun PageTitle(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }; Text(title, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}
@Composable private fun SectionLabel(text: String) { Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 14.dp, bottom = 12.dp)) }
@Composable private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = action).padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text(title, fontSize = 15.sp); Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}
@Composable private fun EmptyState(title: String, hint: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        Text(title, fontSize = 18.sp, modifier = Modifier.padding(top = 22.dp))
        Text(hint, fontSize = 12.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(40.dp))
    }
}
@Composable private fun ConfirmDialog(title: String, message: String, confirm: String, busy: Boolean, dismiss: () -> Unit, action: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = action, enabled = !busy) { Text(confirm) } }, dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("取消") } })
}
