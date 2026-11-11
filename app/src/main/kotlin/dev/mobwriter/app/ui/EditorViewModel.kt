package dev.mobwriter.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mobwriter.app.core.blog.BlogService
import dev.mobwriter.app.core.blog.HexoPaths
import dev.mobwriter.app.core.markdown.InlineEdit
import dev.mobwriter.app.core.markdown.parseBlocks
import dev.mobwriter.app.core.markdown.serializeBlocks
import dev.mobwriter.app.core.markdown.unwrapLatex
import dev.mobwriter.app.data.DraftStore
import dev.mobwriter.app.core.model.Block
import dev.mobwriter.app.core.model.PostDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorBlock(val id: Long, val block: Block)

data class Notice(val title: String, val body: String, val finished: Boolean = false)

enum class WriteMode { Wysiwyg, Source, Reading, Typewriter }

class EditorViewModel(
    private val blog: BlogService,
    private val drafts: DraftStore,
    val draftKey: String,
    private val remotePath: String?,
) : ViewModel() {
    var title by mutableStateOf("")
    var date by mutableStateOf("")
    var series by mutableStateOf("")
    var aplayer by mutableStateOf(false)
    var top by mutableStateOf(false)
    var stickyText by mutableStateOf("")
    var abbrlink by mutableStateOf<String?>(null)
    var categoryKey by mutableStateOf<String?>(null)
    var hadTags by mutableStateOf(false)
    var hadCategories by mutableStateOf(false)
    var extras by mutableStateOf(listOf<String>())
    val tags = mutableStateListOf<String>()
    val categories = mutableStateListOf<String>()
    val blocks = mutableStateListOf<EditorBlock>()
    var path by mutableStateOf<String?>(remotePath)
    var sha by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
    var fromLocal by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf<String?>(null)
    var notice by mutableStateOf<Notice?>(null)
    var activeId by mutableStateOf<Long?>(null)
    var activeItem by mutableIntStateOf(0)
    var selectionStart by mutableIntStateOf(0)
    var selectionEnd by mutableIntStateOf(0)
    var revision by mutableIntStateOf(0)
    var focusToken by mutableIntStateOf(0)
    var mode by mutableStateOf(WriteMode.Wysiwyg)
    var sourceText by mutableStateOf("")
    var sourceSelectionStart by mutableIntStateOf(0)
    var sourceSelectionEnd by mutableIntStateOf(0)
    var sourceRevision by mutableIntStateOf(0)
    var switching by mutableStateOf(false)

    private var ids = 1L
    private var saveJob: Job? = null
    private var baselineSnapshot = ""
    private var baselineDocument: PostDocument? = null
    private var openedFromLocal = false
    private val rememberedHeading = mutableMapOf<Long, Int>()

    init {
        viewModelScope.launch {
            val local = drafts.load(draftKey)?.document
            if (local != null) {
                openedFromLocal = true
                bind(local)
                fromLocal = remotePath != null || local.path == null
                loading = false
                return@launch
            }
            if (remotePath == null) {
                openedFromLocal = false
                bind(PostDocument(date = HexoPaths.now(), blocks = listOf(Block.Paragraph(""))))
                loading = false
                return@launch
            }
            try {
                val document = withContext(Dispatchers.IO) { blog.load(remotePath) }
                bind(document)
            } catch (exception: Exception) {
                error = exception.message ?: "打不开这篇文章"
            } finally {
                loading = false
            }
        }
    }

    fun reloadRemote() {
        val target = path ?: remotePath ?: return
        viewModelScope.launch {
            loading = true
            try {
                val document = withContext(Dispatchers.IO) { blog.load(target) }
                openedFromLocal = false
                bind(document)
                fromLocal = false
                drafts.delete(draftKey)
            } catch (exception: Exception) {
                notice = Notice("没有读到线上版本", exception.message ?: "请稍后再试")
            } finally {
                loading = false
            }
        }
    }

    fun onMarkup(id: Long, item: Int, value: androidx.compose.ui.text.input.TextFieldValue) {
        activeId = id
        activeItem = item
        val index = indexOf(id)
        if (index < 0) return
        val current = blocks[index]
        val old = textOf(current.block, item) ?: return
        val proposed = InlineEdit.applyChange(old, value.text)
        val cursor = if (proposed == value.text) value.selection.end else proposed.length
        selectionStart = cursor
        selectionEnd = cursor
        val newline = insertedNewline(old, proposed)
        if (newline != null && (current.block is Block.Paragraph || current.block is Block.Heading)) {
            splitTextBlock(index, proposed, newline)
            return
        }
        if (newline != null && (current.block is Block.BulletList || current.block is Block.OrderedList)) {
            splitListItem(index, item, proposed, newline)
            return
        }
        if (proposed != value.text) revision++
        blocks[index] = current.copy(block = withText(current.block, item, proposed))
        scheduleSave()
    }

    fun onPlain(id: Long, value: String) {
        activeId = id
        val index = indexOf(id)
        if (index < 0) return
        blocks[index] = blocks[index].copy(block = replacePlain(blocks[index].block, value))
        scheduleSave()
    }

    fun focus(id: Long, item: Int, start: Int, end: Int) {
        activeId = id
        activeItem = item
        selectionStart = start
        selectionEnd = end
    }

    fun switchMode(next: WriteMode) {
        if (next == mode || switching) return
        val from = mode
        val blocksSnap = blocks.map { it.block }
        val sourceSnap = sourceText
        mode = next
        switching = true
        viewModelScope.launch {
            try {
                if (from == WriteMode.Source) {
                    val parsed = withContext(Dispatchers.Default) {
                        parseBlocks(sourceSnap).ifEmpty { listOf(Block.Paragraph("")) }
                    }
                    blocks.clear()
                    parsed.forEach { blocks += EditorBlock(nextId(), it) }
                }
                if (next == WriteMode.Source) {
                    sourceText = withContext(Dispatchers.Default) { serializeBlocks(blocksSnap).trimEnd() }
                    sourceSelectionStart = 0
                    sourceSelectionEnd = 0
                    sourceRevision++
                }
            } finally {
                switching = false
            }
        }
    }

    fun onSource(value: androidx.compose.ui.text.input.TextFieldValue) {
        sourceText = value.text
        sourceSelectionStart = value.selection.start
        sourceSelectionEnd = value.selection.end
        scheduleSave()
    }

    fun toggle(marker: String) {
        if (mode == WriteMode.Source) {
            val edited = InlineEdit.toggle(sourceText, sourceSelectionStart, sourceSelectionEnd, marker)
            sourceText = edited.text
            sourceSelectionStart = edited.selectionStart
            sourceSelectionEnd = edited.selectionEnd
            sourceRevision++
            scheduleSave()
            return
        }
        val index = indexOf(activeId ?: return)
        if (index < 0) return
        val text = textOf(blocks[index].block, activeItem) ?: return
        val edited = InlineEdit.toggle(text, selectionStart, selectionEnd, marker)
        blocks[index] = blocks[index].copy(block = withText(blocks[index].block, activeItem, edited.text))
        selectionStart = edited.selectionStart
        selectionEnd = edited.selectionEnd
        revision++
        scheduleSave()
    }

    fun insertLink(label: String, url: String) {
        if (url.isBlank()) return
        if (mode == WriteMode.Source) {
            val edited = InlineEdit.insertLink(sourceText, sourceSelectionStart, sourceSelectionEnd, label, url.trim())
            sourceText = edited.text
            sourceSelectionStart = edited.selectionStart
            sourceSelectionEnd = edited.selectionEnd
            sourceRevision++
            scheduleSave()
            return
        }
        val index = indexOf(activeId ?: return)
        if (index < 0) return
        val text = textOf(blocks[index].block, activeItem) ?: return
        val edited = InlineEdit.insertLink(text, selectionStart, selectionEnd, label, url.trim())
        blocks[index] = blocks[index].copy(block = withText(blocks[index].block, activeItem, edited.text))
        selectionStart = edited.selectionStart
        selectionEnd = edited.selectionEnd
        revision++
        scheduleSave()
    }

    fun insertMath() = if (mode == WriteMode.Source) insertSource("\n$$\n\n$$\n") else insertSpecial { Block.Math("") }

    fun insertCode() = if (mode == WriteMode.Source) insertSource("\n```\n\n```\n") else insertSpecial { Block.Code(code = "") }

    fun insertRule() {
        if (mode == WriteMode.Source) insertSource("\n---\n") else insertAfter(Block.Rule())
    }

    fun insertImage(absolutePath: String) {
        val name = absolutePath.substringAfterLast('/')
        if (mode == WriteMode.Source) {
            insertSource("\n![]($name)\n")
        }
        insertAfter(Block.Image(alt = "", src = name, pendingFile = absolutePath))
    }

    fun applyHeading(level: Int) {
        val safe = level.coerceIn(1, 6)
        if (mode == WriteMode.Source) {
            sourceText = headingOnLine(sourceText, sourceSelectionStart, safe)
            sourceRevision++
            scheduleSave()
            return
        }
        convert("h$safe")
    }

    fun updateImageAlt(id: Long, alt: String) {
        val index = indexOf(id)
        val image = blocks.getOrNull(index)?.block as? Block.Image ?: return
        blocks[index] = blocks[index].copy(block = image.copy(alt = alt))
        scheduleSave()
    }

    fun updateTable(id: Long, header: Boolean, row: Int, column: Int, text: String) {
        val index = indexOf(id)
        val table = blocks.getOrNull(index)?.block as? Block.Table ?: return
        val updated = if (header) {
            table.copy(header = table.header.replaceAt(column, text))
        } else {
            val rows = table.rows.toMutableList()
            val line = rows.getOrElse(row) { emptyList() }.replaceAt(column, text)
            if (row in rows.indices) rows[row] = line else rows += line
            table.copy(rows = rows)
        }
        blocks[index] = blocks[index].copy(block = updated)
        scheduleSave()
    }

    fun convert(type: String) {
        val index = indexOf(activeId ?: blocks.lastOrNull()?.id ?: return)
        if (index < 0) return
        val id = blocks[index].id
        val current = blocks[index].block
        if (current is Block.Heading) rememberedHeading[id] = current.level
        val resolved = if (type == "h") "h${rememberedHeading[id] ?: 2}" else type
        val text = plain(current)
        val replacement = when (resolved) {
            "p" -> Block.Paragraph(text)
            "h1", "h2", "h3", "h4", "h5", "h6" -> Block.Heading(resolved.drop(1).toInt(), text)
            "q" -> Block.Quote(text)
            "ul" -> Block.BulletList(text.lines().ifEmpty { listOf("") })
            "ol" -> Block.OrderedList(text.lines().ifEmpty { listOf("") })
            "code" -> Block.Code(code = text)
            "math" -> Block.Math(unwrapLatex(text))
            else -> return
        }
        blocks[index] = blocks[index].copy(block = replacement)
        revision++
        scheduleSave()
    }

    fun move(id: Long, down: Boolean) {
        val index = indexOf(id)
        val target = index + if (down) 1 else -1
        if (index < 0 || target !in blocks.indices) return
        val item = blocks.removeAt(index)
        blocks.add(target, item)
        scheduleSave()
    }

    fun delete(id: Long) {
        val index = indexOf(id)
        if (index < 0) return
        blocks.removeAt(index)
        if (blocks.isEmpty()) blocks += EditorBlock(nextId(), Block.Paragraph(""))
        activeId = blocks[index.coerceAtMost(blocks.lastIndex)].id
        focusToken++
        scheduleSave()
    }

    fun addTag(value: String) {
        val tag = value.trim()
        if (tag.isNotEmpty() && tag !in tags) tags += tag
        hadTags = true
        scheduleSave()
    }

    fun removeTag(value: String) {
        tags.remove(value)
        scheduleSave()
    }

    fun addCategory(value: String) {
        val category = value.trim()
        if (category.isNotEmpty() && category !in categories) categories += category
        hadCategories = true
        if (categoryKey == null) categoryKey = "categories"
        scheduleSave()
    }

    fun removeCategory(value: String) {
        categories.remove(value)
        scheduleSave()
    }

    fun hasChanges(): Boolean = snapshot() != baselineSnapshot

    fun saveNow() {
        saveJob?.cancel()
        syncSource()
        drafts.save(draftKey, toDocument())
    }

    fun discardSession() {
        saveJob?.cancel()
        val base = baselineDocument
        if (openedFromLocal && base != null) drafts.save(draftKey, base) else drafts.delete(draftKey)
    }

    fun saveDraftCopy() {
        syncSource()
        if (title.isBlank()) {
            notice = Notice("还没有标题", "存成草稿也要有标题，它会写成文件名。")
            return
        }
        viewModelScope.launch {
            busy = "正在存草稿"
            try {
                saveNow()
                val report = withContext(Dispatchers.IO) {
                    blog.publish(toDocument(), asDraft = true, retainOriginal = true)
                }
                val draftsDir = HexoPaths.draftsDirectory(blog.settings.postsPath)
                if (path == null || path?.substringBeforeLast('/', "") == draftsDir) {
                    path = report.path
                    sha = report.sha
                }
                captureBaseline()
                notice = Notice("已存到草稿", "当前修改已复制到草稿。发布仍会单独写到文章里。")
            } catch (exception: Exception) {
                drafts.save(draftKey, toDocument())
                notice = Notice("没有存成草稿", exception.message ?: "请检查网络和令牌")
            } finally {
                busy = null
            }
        }
    }

    fun publish(asDraft: Boolean) {
        syncSource()
        if (title.isBlank()) {
            notice = Notice("还没有标题", "标题会写成文件名，也是文章题目。")
            return
        }
        viewModelScope.launch {
            busy = if (asDraft) "正在存草稿" else "正在发布"
            try {
                val report = withContext(Dispatchers.IO) { blog.publish(toDocument(), asDraft) }
                path = report.path
                sha = report.sha
                clearPendingImages(report.path)
                drafts.delete(draftKey)
                notice = Notice(if (asDraft) "草稿已保存" else "已发布", report.detail, finished = true)
            } catch (exception: Exception) {
                drafts.save(draftKey, toDocument())
                notice = Notice("没有发出去", exception.message ?: "请检查网络和令牌")
            } finally {
                busy = null
            }
        }
    }

    fun canFormatInline(): Boolean {
        if (mode == WriteMode.Source) return true
        val block = blocks.firstOrNull { it.id == activeId }?.block
        return block is Block.Paragraph || block is Block.Heading || block is Block.Quote ||
            block is Block.BulletList || block is Block.OrderedList
    }

    private fun insertSpecial(create: () -> Block) {
        val index = indexOf(activeId)
        val current = blocks.getOrNull(index)
        if (current != null && current.block is Block.Paragraph && current.block.text.isBlank()) {
            blocks[index] = current.copy(block = create())
            activeId = current.id
        } else {
            insertAfter(create())
            return
        }
        focusToken++
        revision++
        scheduleSave()
    }

    private fun insertAfter(block: Block) {
        val created = EditorBlock(nextId(), block)
        val index = indexOf(activeId)
        val at = if (index < 0) blocks.size else index + 1
        blocks.add(at, created)
        activeId = created.id
        focusToken++
        revision++
        scheduleSave()
    }

    private fun splitTextBlock(index: Int, text: String, at: Int) {
        val left = text.substring(0, at)
        val right = text.substring(at + 1)
        val current = blocks[index]
        val heading = current.block as? Block.Heading
        blocks[index] = current.copy(
            block = if (heading != null) heading.copy(text = left) else Block.Paragraph(left),
        )
        val created = EditorBlock(nextId(), Block.Paragraph(right))
        blocks.add(index + 1, created)
        activeId = created.id
        activeItem = 0
        selectionStart = 0
        selectionEnd = 0
        focusToken++
        revision++
        scheduleSave()
    }

    private fun splitListItem(index: Int, item: Int, text: String, at: Int) {
        val current = blocks[index]
        val items = when (val block = current.block) {
            is Block.BulletList -> block.items.toMutableList()
            is Block.OrderedList -> block.items.toMutableList()
            else -> return
        }
        val left = text.substring(0, at)
        val right = text.substring(at + 1)
        if (item in items.indices) items[item] = left else items += left
        items.add(item + 1, right)
        val updated = when (current.block) {
            is Block.BulletList -> Block.BulletList(items)
            else -> Block.OrderedList(items)
        }
        blocks[index] = current.copy(block = updated)
        activeItem = item + 1
        selectionStart = 0
        selectionEnd = 0
        revision++
        focusToken++
        scheduleSave()
    }

    private fun clearPendingImages(markdownPath: String) {
        for (index in blocks.indices) {
            val image = blocks[index].block as? Block.Image ?: continue
            val pending = image.pendingFile ?: continue
            blocks[index] = blocks[index].copy(
                block = image.copy(
                    pendingFile = null,
                    src = HexoPaths.imagePublicSrc(markdownPath, pending.substringAfterLast('/'), blog.settings.imageMode),
                ),
            )
        }
    }

    private fun bind(document: PostDocument) {
        title = document.title
        date = document.date
        series = document.series.orEmpty()
        aplayer = document.aplayer
        top = document.top
        stickyText = document.sticky?.toString().orEmpty()
        abbrlink = document.abbrlink
        categoryKey = document.categoryKey
        hadTags = document.hadTagsKey
        hadCategories = document.hadCategoriesKey
        extras = document.extras
        tags.clear()
        tags.addAll(document.tags)
        categories.clear()
        categories.addAll(document.categories)
        path = document.path ?: remotePath
        sha = document.sha
        blocks.clear()
        val source = document.blocks.ifEmpty { listOf(Block.Paragraph("")) }
        source.forEach { blocks += EditorBlock(nextId(), it) }
        captureBaseline()
    }

    private fun captureBaseline() {
        baselineDocument = toDocument()
        baselineSnapshot = snapshot()
    }

    private fun snapshot(): String = buildString {
        append(title.trim())
        append('\u0000')
        append(series)
        append('\u0000')
        append(aplayer)
        append('\u0000')
        append(top)
        append('\u0000')
        append(stickyText)
        append('\u0000')
        append(tags.joinToString("\u0001"))
        append('\u0000')
        append(categories.joinToString("\u0001"))
        append('\u0000')
        val body = if (mode == WriteMode.Source) sourceText else serializeBlocks(blocks.map { it.block })
        append(body.trimEnd())
    }

    private fun toDocument(): PostDocument = PostDocument(
        path = path,
        sha = sha,
        title = title.trim(),
        date = date.ifBlank { HexoPaths.now() },
        tags = tags.toList(),
        categories = categories.toList(),
        categoryKey = categoryKey,
        hadTagsKey = hadTags || tags.isNotEmpty(),
        hadCategoriesKey = hadCategories || categories.isNotEmpty(),
        series = series.trim().ifBlank { null },
        aplayer = aplayer,
        top = top,
        sticky = stickyText.toIntOrNull(),
        abbrlink = abbrlink,
        extras = extras,
        blocks = blocks.map { it.block },
    )

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            syncSource()
            drafts.save(draftKey, toDocument())
            fromLocal = true
        }
    }

    private fun syncSource() {
        if (mode != WriteMode.Source) return
        val pending = blocks.map { it.block }.filterIsInstance<Block.Image>().filter { !it.pendingFile.isNullOrBlank() }
        val parsed = parseBlocks(sourceText).ifEmpty { listOf(Block.Paragraph("")) }.map { block ->
            if (block is Block.Image) {
                val match = pending.find { it.src.substringAfterLast('/') == block.src.substringAfterLast('/') }
                if (match != null) block.copy(pendingFile = match.pendingFile, alt = block.alt.ifBlank { match.alt }) else block
            } else {
                block
            }
        }
        blocks.clear()
        parsed.forEach { blocks += EditorBlock(nextId(), it) }
    }

    private fun insertSource(snippet: String) {
        val start = sourceSelectionStart.coerceIn(0, sourceText.length)
        val end = sourceSelectionEnd.coerceIn(start, sourceText.length)
        sourceText = sourceText.substring(0, start) + snippet + sourceText.substring(end)
        sourceSelectionStart = start + snippet.length
        sourceSelectionEnd = sourceSelectionStart
        sourceRevision++
        scheduleSave()
    }

    private fun headingOnLine(text: String, cursor: Int, level: Int): String {
        val safeCursor = cursor.coerceIn(0, text.length)
        val start = text.lastIndexOf('\n', (safeCursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val end = text.indexOf('\n', safeCursor).let { if (it < 0) text.length else it }
        val body = text.substring(start, end).replace(Regex("^#{1,6}\\s+"), "")
        return text.substring(0, start) + "#".repeat(level) + " " + body + text.substring(end)
    }

    private fun nextId() = ids++

    private fun indexOf(id: Long?) = blocks.indexOfFirst { it.id == id }

    private fun textOf(block: Block, item: Int): String? = when (block) {
        is Block.Paragraph -> block.text
        is Block.Heading -> block.text
        is Block.Quote -> block.text
        is Block.BulletList -> block.items.getOrElse(item) { "" }
        is Block.OrderedList -> block.items.getOrElse(item) { "" }
        else -> null
    }

    private fun withText(block: Block, item: Int, text: String): Block = when (block) {
        is Block.Paragraph -> block.copy(text = text)
        is Block.Heading -> block.copy(text = text)
        is Block.Quote -> block.copy(text = text)
        is Block.BulletList -> block.copy(items = block.items.replaceAt(item, text))
        is Block.OrderedList -> block.copy(items = block.items.replaceAt(item, text))
        else -> block
    }

    private fun replacePlain(block: Block, text: String): Block = when (block) {
        is Block.Code -> block.copy(code = text)
        is Block.Math -> block.copy(latex = text)
        is Block.Raw -> block.copy(text = text)
        is Block.Quote -> block.copy(text = text)
        else -> block
    }

    private fun plain(block: Block): String = when (block) {
        is Block.Paragraph -> block.text
        is Block.Heading -> block.text
        is Block.Quote -> block.text
        is Block.BulletList -> block.items.joinToString("\n")
        is Block.OrderedList -> block.items.joinToString("\n")
        is Block.Code -> block.code
        is Block.Math -> block.latex
        is Block.Raw -> block.text
        else -> ""
    }

    private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> {
        val copy = toMutableList()
        while (copy.size <= index) {
            @Suppress("UNCHECKED_CAST")
            copy += "" as T
        }
        copy[index] = value
        return copy
    }

    private fun insertedNewline(old: String, proposed: String): Int? {
        if (proposed.length != old.length + 1) return null
        val prefix = old.zip(proposed).takeWhile { it.first == it.second }.count()
        if (prefix >= proposed.length || proposed[prefix] != '\n') return null
        return if (proposed.removeRange(prefix, prefix + 1) == old) prefix else null
    }
}

class EditorViewModelFactory(
    private val blog: BlogService,
    private val drafts: DraftStore,
    private val draftKey: String,
    private val remotePath: String?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        EditorViewModel(blog, drafts, draftKey, remotePath) as T
}
