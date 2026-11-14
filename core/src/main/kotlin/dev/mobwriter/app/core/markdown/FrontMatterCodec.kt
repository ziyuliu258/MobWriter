package dev.mobwriter.app.core.markdown

import dev.mobwriter.app.core.model.PostDocument

object FrontMatterCodec {
    private val keyPattern = Regex("""^([A-Za-z_][A-Za-z0-9_-]*):\s*(.*)$""")
    private val known = setOf(
        "title", "date", "author", "uuid", "tags", "categories", "catagories", "series", "series_index",
        "aplayer", "top", "pinned", "sticky", "abbrlink",
    )

    /** 「其他字段」编辑框里的原文，按顶层条目拆开。 */
    fun extrasFromText(yaml: String): List<String> {
        val lines = yaml.replace("\r\n", "\n").split('\n')
        val extras = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            if (lines[index].isBlank()) {
                index++
                continue
            }
            val (raw, next) = rawBlock(lines, index)
            if (raw.isNotBlank()) extras += raw
            index = next
        }
        return extras
    }

    fun topKey(raw: String): String? =
        keyPattern.matchEntire(raw.lineSequence().first())?.groupValues?.get(1)

    fun parse(markdown: String): Pair<PostDocument, String> {
        val text = markdown.removePrefix("\uFEFF").replace("\r\n", "\n")
        if (!text.startsWith("---\n")) {
            return PostDocument() to text
        }
        val end = text.indexOf("\n---", startIndex = 3)
        if (end < 0) return PostDocument() to text
        val after = end + 4
        val bodyStart = when {
            text.startsWith("\n", after) -> after + 1
            text.startsWith("---\n", end + 1) -> after + 1
            else -> after
        }
        val yaml = text.substring(4, end)
        val body = if (bodyStart <= text.length) text.substring(bodyStart) else ""
        return readYaml(yaml) to body
    }

    fun render(document: PostDocument, body: String): String {
        val yaml = writeYaml(document)
        val content = if (body.isEmpty()) "" else if (body.endsWith("\n")) body else "$body\n"
        return "---\n$yaml\n---\n$content"
    }

    private fun readYaml(yaml: String): PostDocument {
        val lines = yaml.split('\n')
        var title = ""
        var date = ""
        val authors = mutableListOf<String>()
        var uuid = ""
        var tags = emptyList<String>()
        var categories = emptyList<String>()
        var categoryKey: String? = null
        var hadTags = false
        var hadCategories = false
        var series: String? = null
        var seriesIndex: Int? = null
        var aplayer = false
        var top = false
        var pinned = false
        var sticky: Int? = null
        var abbrlink: String? = null
        val extras = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                i++
                continue
            }
            if (line.trimStart().startsWith("#")) {
                extras += line
                i++
                continue
            }
            val match = keyPattern.matchEntire(line)
            if (match == null) {
                val (raw, next) = rawBlock(lines, i)
                extras += raw
                i = next
                continue
            }
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            if (key !in known) {
                val (raw, next) = rawBlock(lines, i)
                extras += raw
                i = next
                continue
            }
            when (key) {
                "title" -> {
                    val read = readScalar(value, lines, i + 1)
                    title = read.first
                    i = read.second
                }
                "date" -> {
                    val read = readScalar(value, lines, i + 1)
                    date = read.first
                    i = read.second
                }
                "author" -> {
                    val read = readList(value, lines, i + 1)
                    authors.clear()
                    authors += read.first
                    i = read.second
                }
                "uuid" -> {
                    uuid = unquote(value.trim())
                    i++
                }
                "series" -> {
                    val read = readScalar(value, lines, i + 1)
                    series = read.first.ifBlank { null }
                    i = read.second
                }
                "series_index" -> {
                    seriesIndex = value.trim().trim('\'', '"').toIntOrNull()
                    i++
                }
                "abbrlink" -> {
                    val read = readScalar(value, lines, i + 1)
                    abbrlink = read.first.ifBlank { null }
                    i = read.second
                }
                "aplayer" -> {
                    aplayer = truthy(value)
                    i++
                }
                "top" -> {
                    top = truthy(value)
                    i++
                }
                "pinned" -> {
                    pinned = truthy(value)
                    i++
                }
                "sticky" -> {
                    sticky = value.trim().toIntOrNull()
                    i++
                }
                "tags" -> {
                    hadTags = true
                    val read = readList(value, lines, i + 1)
                    tags = read.first
                    i = read.second
                }
                "categories", "catagories" -> {
                    hadCategories = true
                    categoryKey = key
                    val read = readList(value, lines, i + 1)
                    categories = read.first
                    i = read.second
                }
                else -> i++
            }
        }
        return PostDocument(
            title = title,
            date = date,
            authors = authors.toList(),
            uuid = uuid,
            tags = tags,
            categories = categories,
            categoryKey = categoryKey,
            hadTagsKey = hadTags,
            hadCategoriesKey = hadCategories,
            series = series,
            seriesIndex = seriesIndex,
            aplayer = aplayer,
            top = top,
            pinned = pinned,
            sticky = sticky,
            abbrlink = abbrlink,
            extras = extras,
        )
    }

    private fun readScalar(value: String, lines: List<String>, next: Int): Pair<String, Int> {
        val marker = value.trim()
        if (marker == ">-" || marker == ">" || marker == "|-" || marker == "|") {
            val parts = mutableListOf<String>()
            var i = next
            while (i < lines.size && (lines[i].startsWith(" ") || lines[i].startsWith("\t"))) {
                parts += lines[i].trim()
                i++
            }
            val text = if (marker.startsWith(">")) parts.joinToString(" ") else parts.joinToString("\n")
            return text to i
        }
        return unquote(marker) to (next - 1 + 1)
    }

    private fun readList(value: String, lines: List<String>, next: Int): Pair<List<String>, Int> {
        if (value.isNotBlank() && value.trim() != "[]" && value.trim() != ">" && value.trim() != "|") {
            if (value.trim() == ">-" || value.trim() == "|-") {
                val scalar = readScalar(value, lines, next)
                return listOf(scalar.first).filter { it.isNotBlank() } to scalar.second
            }
            return listOf(unquote(value.trim())) to next
        }
        if (value.trim() == "[]") return emptyList<String>() to next
        val items = mutableListOf<String>()
        var i = next
        while (i < lines.size && lines[i].matches(Regex("""\s*-\s+.*"""))) {
            items += unquote(lines[i].substringAfter('-').trim())
            i++
        }
        return items to i
    }

    private fun rawBlock(lines: List<String>, start: Int): Pair<String, Int> {
        val buffer = StringBuilder(lines[start])
        var i = start + 1
        while (i < lines.size && (lines[i].startsWith(" ") || lines[i].startsWith("\t"))) {
            buffer.append('\n').append(lines[i])
            i++
        }
        return buffer.toString() to i
    }

    private fun writeYaml(document: PostDocument): String {
        val lines = mutableListOf<String>()
        if (document.uuid.isNotBlank()) lines += "uuid: ${document.uuid}"
        lines += "title: ${quote(document.title)}"
        if (document.date.isNotBlank()) lines += "date: ${document.date}"
        when (document.authors.size) {
            0 -> Unit
            1 -> lines += "author: ${quote(document.authors[0])}"
            else -> {
                lines += "author:"
                document.authors.forEach { lines += "  - ${quote(it)}" }
            }
        }
        if (document.tags.isNotEmpty() || document.hadTagsKey) {
            lines += "tags:"
            document.tags.forEach { lines += "  - ${quote(it)}" }
        }
        if (document.categories.isNotEmpty() || document.hadCategoriesKey) {
            lines += "${document.categoryKey ?: "categories"}:"
            document.categories.forEach { lines += "  - ${quote(it)}" }
        }
        if (!document.series.isNullOrBlank()) lines += "series: ${quote(document.series)}"
        if (document.seriesIndex != null) lines += "series_index: ${document.seriesIndex}"
        if (document.aplayer) lines += "aplayer: true"
        if (document.top) lines += "top: true"
        if (document.pinned) lines += "pinned: true"
        if (document.sticky != null) lines += "sticky: ${document.sticky}"
        if (!document.abbrlink.isNullOrBlank()) lines += "abbrlink: ${document.abbrlink}"
        document.extras.forEach { raw ->
            val key = topKey(raw)
            if (key == null || key !in known) lines += raw
        }
        return lines.joinToString("\n")
    }

    private fun truthy(value: String): Boolean {
        val text = value.trim().lowercase()
        return text == "true" || text == "yes" || text == "on"
    }

    fun unquote(value: String): String {
        val text = value.trim()
        if (text.length >= 2 && text.startsWith("'") && text.endsWith("'")) {
            return text.substring(1, text.length - 1).replace("''", "'")
        }
        if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        return text
    }

    fun quote(value: String): String {
        if (value.isEmpty()) return "''"
        val plain = value.none {
            it.code < 32 || it in ":#{}[]&*!|>%@`'\""
        } && !value.first().isWhitespace() && !value.last().isWhitespace()
        if (plain) return value
        return "'" + value.replace("'", "''") + "'"
    }
}
