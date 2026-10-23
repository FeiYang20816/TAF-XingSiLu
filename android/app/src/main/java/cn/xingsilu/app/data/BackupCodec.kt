package cn.xingsilu.app.data

import com.google.gson.*
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime

data class Backup(val records: List<Note>, val drafts: List<Draft> = emptyList())
object BackupCodec {
    const val MAX_BYTES = 20 * 1024 * 1024
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    fun read(input: InputStream): Backup {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            require(output.size() + count <= MAX_BYTES) { "备份文件超过 20 MB，无法导入。" }
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        return decode(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF"))
    }
    fun encode(backup: Backup, prefs: UserPreferences): String {
        val root = JsonObject().apply {
            addProperty("schemaVersion", 1); addProperty("app", "XingSiLu"); addProperty("exportedAt", Instant.now().toString())
            addProperty("theme", prefs.appearance.name.lowercase()); addProperty("showDates", prefs.showDates)
            addProperty("sort", if (prefs.sort == NoteSort.CREATED) "createdAt" else "updatedAt")
            add("records", JsonArray().apply { backup.records.forEach { n -> add(JsonObject().apply {
                addProperty("id", n.id); addProperty("type", n.kind.wire); addProperty("summary", n.summary); addProperty("content", n.content)
                add("tags", JsonArray().apply { n.tags.forEach { add(it) } }); addProperty("eventDate", n.eventDate)
                addProperty("createdAt", Instant.ofEpochMilli(n.createdAt).toString()); addProperty("updatedAt", Instant.ofEpochMilli(n.updatedAt).toString())
                addProperty("isPinned", n.isPinned); addProperty("isDeleted", n.isDeleted)
            }) } })
            add("drafts", JsonArray().apply { backup.drafts.forEach { d -> add(JsonObject().apply {
                addProperty("id", d.id); addProperty("type", d.kind.wire); addProperty("summary", d.summary); addProperty("content", d.content)
                addProperty("tagsInput", d.tagsInput); addProperty("eventDate", d.eventDate)
                addProperty("createdAt", Instant.ofEpochMilli(d.createdAt).toString()); addProperty("updatedAt", Instant.ofEpochMilli(d.updatedAt).toString())
            }) } })
        }
        return gson.toJson(root).also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份超过 20 MB，请先使用 Markdown 导出记录。" } }
    }
    fun decode(text: String): Backup {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份文件过大。" }
        try {
            val root = JsonParser.parseString(text).asJsonObject
            require(root["schemaVersion"]?.isJsonPrimitive == true && root["schemaVersion"].asJsonPrimitive.isNumber && root["schemaVersion"].asString == "1") { "不支持此备份版本。" }
            val records = root.getAsJsonArray("records") ?: error("缺少记录列表。")
            require(records.size() <= 100000) { "备份中的记录过多。" }
            val notes = records.map { element -> element.asJsonObject.let { r ->
                val kind = NoteKind.parse(r.string("type"))
                val tags = r.getAsJsonArray("tags") ?: error("缺少标签数据。")
                val note = Note(id = r.string("id").also { require(it.isNotBlank()) }, kind = kind,
                    summary = r.string("summary"), content = r.string("content"),
                    tags = tags.map { require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString }.distinct(),
                    eventDate = if (r.has("eventDate")) r.string("eventDate") else "",
                    createdAt = r.time("createdAt"), updatedAt = r.time("updatedAt"), isPinned = r.bool("isPinned"), isDeleted = r.bool("isDeleted"))
                require((if (kind == NoteKind.EXPERIENCE) note.summary else note.content).isNotBlank()) { "备份包含空白记录。" }
                require(note.eventDate.isBlank() || validDate(note.eventDate)) { "备份中的日期无效。" }
                note
            } }
            require(notes.map { it.id }.distinct().size == notes.size) { "备份中存在重复编号。" }
            val drafts = if (!root.has("drafts")) emptyList() else root.getAsJsonArray("drafts").map { e -> e.asJsonObject.let { d ->
                Draft(id = d.string("id").also { require(it.isNotBlank()) }, kind = NoteKind.parse(d.string("type")),
                    summary = d.string("summary"), content = d.string("content"), tagsInput = d.string("tagsInput"),
                    eventDate = d.string("eventDate"), createdAt = d.time("createdAt"), updatedAt = d.time("updatedAt"))
            } }
            require(drafts.map { it.id }.distinct().size == drafts.size) { "备份中存在重复草稿。" }
            val byId = notes.associateBy { it.id }
            require(drafts.all { byId[it.id]?.kind?.let { kind -> kind == it.kind } ?: true }) { "草稿与记录的类型不一致。" }
            return Backup(notes, drafts)
        } catch (e: Exception) {
            throw IllegalArgumentException("无法读取备份，请选择行思录导出的 JSON 文件。${e.message?.take(70).orEmpty()}", e)
        }
    }
    private fun JsonObject.string(key: String): String {
        val v = get(key); require(v != null && v.isJsonPrimitive && v.asJsonPrimitive.isString) { "字段 $key 必须是文本。" }; return v.asString
    }
    private fun JsonObject.bool(key: String): Boolean {
        val v = get(key); require(v != null && v.isJsonPrimitive && v.asJsonPrimitive.isBoolean) { "字段 $key 必须是布尔值。" }; return v.asBoolean
    }
    private fun JsonObject.time(key: String): Long {
        val date = OffsetDateTime.parse(string(key)).toInstant().toEpochMilli()
        require(date >= 0 && date <= 253402300799999L) { "时间超出支持范围。" }; return date
    }
}
object Markdown {
    fun encode(notes: List<Note>, kind: NoteKind): String = buildString {
        append("# 我的${kind.label}记录\n\n")
        notes.filter { it.kind == kind && !it.isDeleted }.sortedBy { it.createdAt }.forEach { n ->
            append("## ").append(if (kind == NoteKind.EXPERIENCE) n.summary.replace(Regex("[\\r\\n]+"), " ") else displayDate(n.createdAt, true)).append("\n\n")
            append("记录时间：${displayDate(n.createdAt, true)}\n")
            if (n.tags.isNotEmpty()) append("标签：${n.tags.joinToString("、")}\n")
            if (n.eventDate.isNotEmpty()) append("相关日期：${n.eventDate}\n")
            append('\n')
            if (kind == NoteKind.EXPERIENCE && n.content.isNotEmpty()) append("### 详情\n\n")
            append(n.content).append("\n\n------\n\n")
        }
    }
}
