package dev.mobwriter.app.core.markdown

enum class InlineKind { Bold, Italic, Strike, Code, Link, Math }

data class InlineSpan(val kind: InlineKind, val start: Int, val end: Int, val href: String? = null)

data class Marker(val start: Int, val endExclusive: Int, val group: Int)

data class Presented(
    val text: String,
    val spans: List<InlineSpan>,
    val origToDisp: IntArray,
    val dispToOrig: IntArray,
    val markers: List<Marker>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Presented) return false
        return text == other.text && spans == other.spans && markers == other.markers &&
            origToDisp.contentEquals(other.origToDisp) && dispToOrig.contentEquals(other.dispToOrig)
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + spans.hashCode()
        result = 31 * result + markers.hashCode()
        result = 31 * result + origToDisp.contentHashCode()
        result = 31 * result + dispToOrig.contentHashCode()
        return result
    }
}

object InlineMarkup {
    private val breakTag = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

    fun present(source: String): Presented {
        val builder = Builder()
        parse(source, 0, source.length, builder)
        return builder.build(source)
    }

    private fun parse(source: String, start: Int, end: Int, builder: Builder) {
        var index = start
        while (index < end) {
            val next = token(source, index, end, builder)
            if (next > index) {
                index = next
            } else {
                builder.visible(index, source[index])
                index++
            }
        }
    }

    private fun token(source: String, index: Int, end: Int, builder: Builder): Int {
        if (index >= end) return index
        if (source[index] == '\\' && index + 1 < end && source[index + 1] in "\\`*_{}[]()#+-.!\$~<>") {
            val group = builder.newGroup()
            builder.marker(index, index + 1, group)
            builder.visible(index + 1, source[index + 1])
            return index + 2
        }
        if (source.startsWith("![", index)) {
            val mid = find(source, index + 2, end, "](")
            val close = if (mid >= 0) find(source, mid + 2, end, ")") else -1
            if (mid >= 0 && close >= 0) {
                val group = builder.newGroup()
                builder.marker(index, index + 2, group)
                val from = builder.length
                parse(source, index + 2, mid, builder)
                if (builder.length == from) {
                    builder.synthetic('图', index)
                    builder.synthetic('片', index)
                }
                builder.span(InlineKind.Link, from, builder.length, source.substring(mid + 2, close))
                builder.marker(mid, close + 1, group)
                return close + 1
            }
        }
        if (source.startsWith("[", index)) {
            val mid = find(source, index + 1, end, "](")
            val close = if (mid >= 0) find(source, mid + 2, end, ")") else -1
            if (mid >= 0 && close >= 0) {
                val group = builder.newGroup()
                builder.marker(index, index + 1, group)
                val from = builder.length
                parse(source, index + 1, mid, builder)
                builder.span(InlineKind.Link, from, builder.length, source.substring(mid + 2, close))
                builder.marker(mid, close + 1, group)
                return close + 1
            }
        }
        if (source.startsWith("***", index)) {
            val close = find(source, index + 3, end, "***")
            if (close > index + 3) return wrap(source, index, close, 3, builder, listOf(InlineKind.Bold, InlineKind.Italic), recurse = true)
        }
        if (source.startsWith("**", index)) {
            val close = find(source, index + 2, end, "**")
            if (close > index + 2) return wrap(source, index, close, 2, builder, listOf(InlineKind.Bold), recurse = true)
        }
        if (source.startsWith("~~", index)) {
            val close = find(source, index + 2, end, "~~")
            if (close > index + 2) return wrap(source, index, close, 2, builder, listOf(InlineKind.Strike), recurse = true)
        }
        if (index < end && source[index] == '`') {
            var count = 0
            while (index + count < end && source[index + count] == '`') count++
            val close = findRun(source, index + count, end, '`', count)
            if (close > index + count) {
                val group = builder.newGroup()
                builder.marker(index, index + count, group)
                val from = builder.length
                var cursor = index + count
                while (cursor < close) {
                    builder.visible(cursor, source[cursor])
                    cursor++
                }
                builder.span(InlineKind.Code, from, builder.length)
                builder.marker(close, close + count, group)
                return close + count
            }
        }
        if (index < end && source[index] == '$' && (index + 1 >= end || source[index + 1] != '$')) {
            val close = findDollar(source, index + 1, end)
            if (close > index + 1) {
                val group = builder.newGroup()
                builder.marker(index, index + 1, group)
                val from = builder.length
                var cursor = index + 1
                while (cursor < close) {
                    builder.visible(cursor, source[cursor])
                    cursor++
                }
                builder.span(InlineKind.Math, from, builder.length)
                builder.marker(close, close + 1, group)
                return close + 1
            }
        }
        if (index < end && source[index] == '*' && (index + 1 >= end || source[index + 1] != '*')) {
            val lineEnd = limit(source, index + 1, end)
            if (index + 1 < lineEnd && !source[index + 1].isWhitespace()) {
                val close = findStar(source, index + 1, lineEnd)
                if (close > index + 1) return wrap(source, index, close, 1, builder, listOf(InlineKind.Italic), recurse = true)
            }
        }
        breakTag.find(source, index)?.takeIf { it.range.first == index }?.let { match ->
            val group = builder.newGroup()
            val last = match.range.last
            builder.marker(match.range.first, last + 1, group)
            builder.synthetic('\n', last)
            return last + 1
        }
        if (source.startsWith("<code>", index, ignoreCase = true)) {
            val close = indexOfIgnore(source, "</code>", index + 6, end)
            if (close >= 0) {
                val group = builder.newGroup()
                builder.marker(index, index + 6, group)
                val from = builder.length
                var cursor = index + 6
                while (cursor < close) {
                    builder.visible(cursor, source[cursor])
                    cursor++
                }
                builder.span(InlineKind.Code, from, builder.length)
                builder.marker(close, close + 7, group)
                return close + 7
            }
        }
        if (source.startsWith("<http://", index) || source.startsWith("<https://", index)) {
            val close = find(source, index + 1, end, ">")
            if (close > index + 1) {
                val group = builder.newGroup()
                builder.marker(index, index + 1, group)
                val from = builder.length
                var cursor = index + 1
                while (cursor < close) {
                    builder.visible(cursor, source[cursor])
                    cursor++
                }
                builder.span(InlineKind.Link, from, builder.length, source.substring(index + 1, close))
                builder.marker(close, close + 1, group)
                return close + 1
            }
        }
        return index
    }

    private fun wrap(
        source: String,
        open: Int,
        close: Int,
        width: Int,
        builder: Builder,
        kinds: List<InlineKind>,
        recurse: Boolean,
    ): Int {
        val group = builder.newGroup()
        builder.marker(open, open + width, group)
        val from = builder.length
        if (recurse) parse(source, open + width, close, builder) else {
            var cursor = open + width
            while (cursor < close) {
                builder.visible(cursor, source[cursor])
                cursor++
            }
        }
        kinds.forEach { builder.span(it, from, builder.length) }
        builder.marker(close, close + width, group)
        return close + width
    }

    private fun limit(source: String, from: Int, end: Int): Int {
        val line = source.indexOf('\n', from)
        return if (line < 0) end else minOf(end, line)
    }

    private fun find(source: String, from: Int, end: Int, marker: String): Int {
        val stop = limit(source, from, end)
        var index = from
        while (index + marker.length <= stop) {
            if (source.startsWith(marker, index)) return index
            index++
        }
        return -1
    }

    private fun findRun(source: String, from: Int, end: Int, char: Char, count: Int): Int {
        val stop = limit(source, from, end)
        var index = from
        while (index + count <= stop) {
            var same = true
            for (offset in 0 until count) {
                if (source[index + offset] != char) {
                    same = false
                    break
                }
            }
            if (same && (index + count >= stop || source[index + count] != char)) return index
            index++
        }
        return -1
    }

    private fun findDollar(source: String, from: Int, end: Int): Int {
        val stop = limit(source, from, end)
        var index = from
        while (index < stop) {
            if (source[index] == '$' && (index + 1 >= stop || source[index + 1] != '$') && source[index - 1] != '\\') {
                return index
            }
            index++
        }
        return -1
    }

    private fun findStar(source: String, from: Int, end: Int): Int {
        var index = from
        while (index < end) {
            if (source[index] == '*' &&
                (index + 1 >= end || source[index + 1] != '*') &&
                source[index - 1] != '*' &&
                !source[index - 1].isWhitespace()
            ) {
                return index
            }
            index++
        }
        return -1
    }

    private fun indexOfIgnore(source: String, needle: String, from: Int, end: Int): Int {
        val stop = limit(source, from, end)
        val lower = source.lowercase()
        val found = lower.indexOf(needle.lowercase(), from)
        return if (found >= 0 && found + needle.length <= stop) found else -1
    }

    private class Builder {
        private val chars = StringBuilder()
        private val origins = ArrayList<Int>()
        private val spans = mutableListOf<InlineSpan>()
        private val markers = mutableListOf<Marker>()
        private var groups = 1
        val length: Int get() = chars.length

        fun newGroup() = groups++

        fun visible(origin: Int, char: Char) {
            chars.append(char)
            origins += origin
        }

        fun synthetic(char: Char, origin: Int) {
            chars.append(char)
            origins += origin
        }

        fun marker(start: Int, endExclusive: Int, group: Int) {
            if (endExclusive > start) markers += Marker(start, endExclusive, group)
        }

        fun span(kind: InlineKind, start: Int, end: Int, href: String? = null) {
            if (end > start) spans += InlineSpan(kind, start, end, href)
        }

        fun build(source: String): Presented {
            val text = chars.toString()
            val origToDisp = IntArray(source.length + 1)
            var display = 0
            for (origin in 0..source.length) {
                while (display < origins.size && origins[display] < origin) display++
                origToDisp[origin] = display
            }
            val dispToOrig = IntArray(text.length + 1)
            origins.forEachIndexed { index, origin -> dispToOrig[index] = origin }
            dispToOrig[text.length] = source.length
            return Presented(text, spans.toList(), origToDisp, dispToOrig, markers.toList())
        }
    }
}

object InlineEdit {
    data class Result(val text: String, val selectionStart: Int, val selectionEnd: Int)

    fun applyChange(old: String, proposed: String): String {
        if (old == proposed) return old
        val prefix = commonPrefix(old, proposed)
        val suffix = commonSuffix(old, proposed, prefix)
        val inserted = proposed.substring(prefix, proposed.length - suffix)
        val deleteStart = prefix
        val deleteEnd = old.length - suffix
        if (inserted.isEmpty() && deleteEnd > deleteStart) {
            val markers = InlineMarkup.present(old).markers
            val groups = markers.filter { it.start < deleteEnd && it.endExclusive > deleteStart }.map { it.group }.toSet()
            val ranges = markers.filter { it.group in groups }.map { it.start to it.endExclusive } + (deleteStart to deleteEnd)
            return cleanup(remove(old, ranges))
        }
        return cleanup(proposed)
    }

    fun toggle(text: String, start: Int, end: Int, marker: String): Result {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        val width = marker.length
        if (from == to) {
            val placeholder = when (marker) {
                "`" -> "代码"
                "$" -> "公式"
                else -> "文字"
            }
            val inserted = marker + placeholder + marker
            val updated = text.substring(0, from) + inserted + text.substring(from)
            return Result(updated, from + width, from + width + placeholder.length)
        }
        val wrapped = from >= width && to + width <= text.length &&
            text.substring(from - width, from) == marker &&
            text.substring(to, to + width) == marker
        if (wrapped) {
            val updated = text.removeRange(to, to + width).removeRange(from - width, from)
            return Result(updated, from - width, to - width)
        }
        val updated = text.substring(0, from) + marker + text.substring(from, to) + marker + text.substring(to)
        return Result(updated, from + width, to + width)
    }

    fun insertLink(text: String, start: Int, end: Int, label: String, url: String): Result {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        val visible = label.ifBlank { text.substring(from, to).ifBlank { "链接" } }
        val snippet = "[$visible]($url)"
        val updated = text.substring(0, from) + snippet + text.substring(to)
        return Result(updated, from + 1, from + 1 + visible.length)
    }

    fun cleanup(text: String): String = text.replace("****", "").replace("~~~~", "")

    private fun commonPrefix(left: String, right: String): Int {
        val max = minOf(left.length, right.length)
        var index = 0
        while (index < max && left[index] == right[index]) index++
        return index
    }

    private fun commonSuffix(left: String, right: String, prefix: Int): Int {
        var suffix = 0
        while (suffix < left.length - prefix && suffix < right.length - prefix &&
            left[left.length - 1 - suffix] == right[right.length - 1 - suffix]
        ) {
            suffix++
        }
        return suffix
    }

    private fun remove(text: String, ranges: List<Pair<Int, Int>>): String {
        val merged = mutableListOf<Pair<Int, Int>>()
        ranges.map { (start, end) -> start.coerceIn(0, text.length) to end.coerceIn(0, text.length) }
            .filter { it.second > it.first }
            .sortedBy { it.first }
            .forEach { range ->
                if (merged.isEmpty() || range.first > merged.last().second) merged += range
                else merged[merged.lastIndex] = merged.last().first to maxOf(merged.last().second, range.second)
            }
        val builder = StringBuilder()
        var cursor = 0
        merged.forEach { (start, end) ->
            builder.append(text, cursor, start)
            cursor = end
        }
        builder.append(text, cursor, text.length)
        return builder.toString()
    }
}
