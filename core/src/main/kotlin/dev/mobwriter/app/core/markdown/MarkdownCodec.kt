package dev.mobwriter.app.core.markdown

import dev.mobwriter.app.core.model.Align
import dev.mobwriter.app.core.model.Block
import dev.mobwriter.app.core.model.PostDocument

object PostCodec {
    fun parse(markdown: String, path: String? = null, sha: String? = null): PostDocument {
        val (meta, body) = FrontMatterCodec.parse(markdown)
        return meta.copy(path = path, sha = sha, blocks = parseBlocks(body))
    }

    fun render(document: PostDocument): String {
        val body = serializeBlocks(document.blocks)
        return FrontMatterCodec.render(document, body)
    }
}

fun parseBlocks(body: String): List<Block> {
    val lines = body.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<Block>()
    val paragraph = mutableListOf<String>()
    var index = 0

    fun flush() {
        if (paragraph.isEmpty()) return
        val text = paragraph.joinToString("\n")
        if (text.isNotBlank()) blocks += Block.Paragraph(text)
        paragraph.clear()
    }

    while (index < lines.size) {
        val line = lines[index]
        if (line.isBlank()) {
            flush()
            index++
            continue
        }
        val fence = fenceOf(line)
        if (fence != null) {
            flush()
            val language = line.trimEnd().removePrefix(fence).trim()
            val content = mutableListOf<String>()
            index++
            while (index < lines.size && !closesFence(lines[index], fence)) {
                content += lines[index]
                index++
            }
            if (index < lines.size) index++
            blocks += Block.Code(language, content.joinToString("\n"))
            continue
        }
        if (line.trim() == "$$") {
            flush()
            val content = mutableListOf<String>()
            index++
            while (index < lines.size && lines[index].trim() != "$$") {
                content += lines[index]
                index++
            }
            if (index < lines.size) index++
            blocks += Block.Math(content.joinToString("\n"))
            continue
        }
        val inlineMath = singleMath(line)
        if (inlineMath != null) {
            flush()
            blocks += Block.Math(inlineMath)
            index++
            continue
        }
        val heading = headingOf(line)
        if (heading != null) {
            flush()
            blocks += heading
            index++
            continue
        }
        if (line.startsWith(">")) {
            flush()
            val quoted = mutableListOf<String>()
            while (index < lines.size && lines[index].startsWith(">")) {
                quoted += stripQuote(lines[index])
                index++
            }
            blocks += quoteBlock(quoted)
            continue
        }
        if (isRule(line)) {
            flush()
            blocks += Block.Rule(line.trim())
            index++
            continue
        }
        if (line.trimStart().startsWith("|")) {
            val start = index
            val gathered = mutableListOf<String>()
            while (index < lines.size && lines[index].trimStart().startsWith("|")) {
                gathered += lines[index]
                index++
            }
            val table = parseTable(gathered)
            if (table != null) {
                flush()
                blocks += table
            } else {
                index = start
                paragraph += lines[index]
                index++
            }
            continue
        }
        val image = imageOf(line)
        if (image != null) {
            flush()
            blocks += image
            index++
            continue
        }
        if (isListItem(line)) {
            flush()
            val ordered = isOrdered(line)
            val items = mutableListOf<String>()
            while (index < lines.size) {
                val current = lines[index]
                when {
                    current.isBlank() -> break
                    isListItem(current) && isOrdered(current) == ordered -> {
                        items += listItemText(current)
                        index++
                    }
                    current.startsWith(" ") || current.startsWith("\t") -> {
                        if (items.isEmpty()) items += current
                        else items[items.lastIndex] = items.last() + "\n" + current
                        index++
                    }
                    else -> break
                }
            }
            blocks += if (ordered) Block.OrderedList(items) else Block.BulletList(items)
            continue
        }
        paragraph += line
        index++
    }
    flush()
    return blocks
}

fun serializeBlocks(blocks: List<Block>): String {
    val parts = blocks.mapNotNull { serializeBlock(it) }
    if (parts.isEmpty()) return ""
    return parts.joinToString("\n\n") + "\n"
}

private fun serializeBlock(block: Block): String? = when (block) {
    is Block.Paragraph -> block.text.takeIf { it.isNotBlank() }
    is Block.Heading -> {
        if (block.text.isBlank()) null
        else "${"#".repeat(block.level.coerceIn(1, 6))} ${block.text}"
    }
    is Block.Quote -> serializeQuote(block)
    is Block.BulletList -> serializeList(block.items, ordered = false)
    is Block.OrderedList -> serializeList(block.items, ordered = true)
    is Block.Code -> {
        val lang = block.language.trim()
        "```$lang\n${block.code}\n```"
    }
    is Block.Math -> "$$\n${block.latex}\n$$"
    is Block.Image -> "![${block.alt}](${block.src})"
    is Block.Table -> serializeTable(block)
    is Block.Rule -> if (block.mark in setOf("---", "***", "___")) block.mark else "---"
    is Block.Raw -> block.text
}

private fun serializeQuote(quote: Block.Quote): String? {
    if (quote.text.isBlank() && quote.alert.isNullOrBlank()) return null
    val lines = mutableListOf<String>()
    if (!quote.alert.isNullOrBlank()) lines += "[!${quote.alert}]"
    if (quote.text.isNotEmpty()) lines += quote.text.split('\n')
    return lines.joinToString("\n") { "> $it" }
}

private fun serializeList(items: List<String>, ordered: Boolean): String? {
    val kept = items.filter { it.isNotBlank() }
    if (kept.isEmpty()) return null
    return kept.mapIndexed { index, item ->
        val marker = if (ordered) "${index + 1}. " else "- "
        item.split('\n').mapIndexed { lineIndex, line ->
            when {
                lineIndex == 0 -> marker + line
                line.startsWith(" ") || line.startsWith("\t") -> line
                else -> "  $line"
            }
        }.joinToString("\n")
    }.joinToString("\n")
}

private fun serializeTable(table: Block.Table): String {
    val width = maxOf(
        table.header.size,
        table.rows.maxOfOrNull { it.size } ?: 0,
        table.aligns.size,
    ).coerceAtLeast(1)
    fun row(cells: List<String>) = "| " + (0 until width).joinToString(" | ") { cells.getOrElse(it) { "" } } + " |"
    val separator = "| " + (0 until width).joinToString(" | ") { index ->
        when (table.aligns.getOrElse(index) { Align.Left }) {
            Align.Left -> "---"
            Align.Center -> ":---:"
            Align.Right -> "---:"
        }
    } + " |"
    return (listOf(row(table.header), separator) + table.rows.map { row(it) }).joinToString("\n")
}

private fun fenceOf(line: String): String? {
    val trimmed = line.trimEnd()
    if (trimmed.isEmpty()) return null
    val char = trimmed[0]
    if (char != '`' && char != '~') return null
    var count = 0
    while (count < trimmed.length && trimmed[count] == char) count++
    if (count < 3) return null
    return trimmed.substring(0, count)
}

private fun closesFence(line: String, fence: String): Boolean {
    val trimmed = line.trim()
    return trimmed == fence || (trimmed.startsWith(fence) && trimmed.removePrefix(fence).isBlank())
}

private fun singleMath(line: String): String? {
    val trimmed = line.trim()
    if (trimmed.length > 4 && trimmed.startsWith("$$") && trimmed.endsWith("$$")) {
        return trimmed.removePrefix("$$").removeSuffix("$$").trim()
    }
    return null
}

private fun headingOf(line: String): Block.Heading? {
    val match = Regex("""^(#{1,6})\s+(\S.*)$""").matchEntire(line.trimEnd()) ?: return null
    val text = match.groupValues[2].replace(Regex("""\s+#+\s*$"""), "")
    return Block.Heading(match.groupValues[1].length, text)
}

private fun stripQuote(line: String): String {
    val rest = line.removePrefix(">")
    return if (rest.startsWith(" ")) rest.drop(1) else rest
}

private fun quoteBlock(lines: List<String>): Block.Quote {
    if (lines.isEmpty()) return Block.Quote("")
    val alert = Regex("""^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]$""").matchEntire(lines.first().trim())
    return if (alert != null) {
        Block.Quote(lines.drop(1).joinToString("\n"), alert.groupValues[1])
    } else {
        Block.Quote(lines.joinToString("\n"))
    }
}

private fun isRule(line: String): Boolean = line.trim() in setOf("---", "***", "___")

private val imagePattern = Regex("""^!\[([^]]*)]\(([^)]+)\)\s*$""")

private fun imageOf(line: String): Block.Image? {
    val match = imagePattern.matchEntire(line.trim()) ?: return null
    val src = match.groupValues[2].trim().substringBefore(" \"").substringBefore(" '")
    return Block.Image(match.groupValues[1], src)
}

private val bulletPattern = Regex("""^([-*+])\s+(.*)$""")
private val orderedPattern = Regex("""^(\d+[.)])\s+(.*)$""")

private fun isListItem(line: String): Boolean =
    bulletPattern.matches(line) || orderedPattern.matches(line)

private fun isOrdered(line: String): Boolean = orderedPattern.matches(line)

private fun listItemText(line: String): String =
    orderedPattern.matchEntire(line)?.groupValues?.get(2)
        ?: bulletPattern.matchEntire(line)?.groupValues?.get(2)
        ?: line

private fun parseTable(lines: List<String>): Block.Table? {
    if (lines.size < 2) return null
    val header = splitRow(lines[0])
    if (!isSeparator(lines[1], header.size)) return null
    val aligns = splitRow(lines[1]).map { alignOf(it) }
    val rows = lines.drop(2).map { splitRow(it) }
    return Block.Table(header, aligns, rows)
}

private fun splitRow(line: String): List<String> {
    var text = line.trim()
    if (text.startsWith("|")) text = text.drop(1)
    if (text.endsWith("|")) text = text.dropLast(1)
    return text.split('|').map { it.trim() }
}

private fun isSeparator(line: String, columns: Int): Boolean {
    val cells = splitRow(line)
    if (cells.isEmpty() || cells.size < columns) return false
    return cells.all { it.matches(Regex(""":?-{3,}:?""")) }
}

private fun alignOf(cell: String): Align {
    val left = cell.trim().startsWith(":")
    val right = cell.trim().endsWith(":")
    return when {
        left && right -> Align.Center
        right -> Align.Right
        else -> Align.Left
    }
}

fun unwrapLatex(text: String): String {
    val trimmed = text.trim()
    return when {
        trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length >= 4 ->
            trimmed.removePrefix("$$").removeSuffix("$$").trim('\n', ' ')
        trimmed.startsWith("$") && trimmed.endsWith("$") && trimmed.length >= 2 ->
            trimmed.removePrefix("$").removeSuffix("$").trim()
        else -> text
    }
}
