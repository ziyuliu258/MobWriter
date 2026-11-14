package dev.mobwriter.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Superscript
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import dev.mobwriter.app.core.blog.BlogService
import dev.mobwriter.app.core.blog.HexoPaths
import dev.mobwriter.app.core.model.Block
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    blog: BlogService,
    suggestedTags: List<String>,
    suggestedCategories: List<String>,
    onBack: () -> Unit,
    onPickImage: () -> Unit,
) {
    val articleStep = LocalAppearance.current.article
    val body = articleBodyStyle(articleStep).copy(color = MaterialTheme.colorScheme.onBackground)
    val titleStyle = articleTitleStyle(articleStep).copy(color = MaterialTheme.colorScheme.onBackground)
    var meta by remember { mutableStateOf(viewModel.path == null) }
    var visitUrl by remember { mutableStateOf<String?>(null) }
    var linkOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var linkLabel by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("https://") }
    val focusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val formulas = rememberFormulaBoard()
    var confirmLeave by remember { mutableStateOf(false) }
    fun leave() {
        if (viewModel.hasChanges()) confirmLeave = true else onBack()
    }
    BackHandler {
        if (confirmLeave) confirmLeave = false else leave()
    }
    LaunchedEffect(viewModel.focusToken) {
        val id = viewModel.activeId ?: return@LaunchedEffect
        kotlinx.coroutines.delay(40)
        runCatching { focusRequesters[id]?.requestFocus() }
    }
    Box(Modifier.fillMaxSize()) {
    if (viewModel.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Cinnabar) }
        return
    }
    viewModel.error?.let { message ->
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconButton("返回", onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
                Text(message, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
        }
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        AppIconButton("返回", onClick = { leave() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        AppIconButton("存草稿", onClick = { viewModel.saveDraftCopy() }) {
                            Icon(Icons.Outlined.Save, contentDescription = "存草稿")
                        }
                        AppIconButton("发布", onClick = { viewModel.publish(asDraft = false) }, tint = Cinnabar) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "发布")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        bottomBar = {
            if (viewModel.mode != WriteMode.Reading) {
                FormatBar(
                    modifier = Modifier.navigationBarsPadding().imePadding(),
                    inlineEnabled = viewModel.canFormatInline(),
                    onBold = { viewModel.toggle("**") },
                    onItalic = { viewModel.toggle("*") },
                    onStrike = { viewModel.toggle("~~") },
                    onCode = { viewModel.toggle("`") },
                    onLink = { linkOpen = true },
                    onInlineMath = { viewModel.toggle("$") },
                    onHeading = viewModel::applyHeading,
                    onQuote = { viewModel.convert("q") },
                    onList = { viewModel.convert("ul") },
                    onOrdered = { viewModel.convert("ol") },
                    onCodeBlock = { viewModel.insertCode() },
                    onMathBlock = { viewModel.insertMath() },
                    onRule = { viewModel.insertRule() },
                    onImage = onPickImage,
                )
            }
        },
    ) { padding ->
        val listState = rememberLazyListState()
        LaunchedEffect(viewModel.activeId, viewModel.mode, viewModel.switching) {
            if (viewModel.mode != WriteMode.Typewriter || viewModel.switching) return@LaunchedEffect
            val index = viewModel.blocks.indexOfFirst { it.id == viewModel.activeId }
            if (index < 0) return@LaunchedEffect
            var header = 1
            if (viewModel.fromLocal && viewModel.path != null) header++
            if (viewModel.mode != WriteMode.Reading) {
                header += 2
                if (meta) header++
            }
            val viewport = listState.layoutInfo.viewportSize.height
            listState.animateScrollToItem((header + index).coerceAtLeast(0), scrollOffset = -(viewport / 3))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (viewModel.fromLocal && viewModel.path != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("未发布的修改", color = Muted, modifier = Modifier.weight(1f))
                        AppIconButton("改回线上版本", onClick = viewModel::reloadRemote) {
                            Icon(Icons.Outlined.Restore, contentDescription = "改回线上版本")
                        }
                    }
                }
            }
            if (viewModel.mode != WriteMode.Reading) {
                item {
                    BasicTextField(
                        value = viewModel.title,
                        onValueChange = { viewModel.title = it; viewModel.saveNow() },
                        textStyle = titleStyle,
                        cursorBrush = SolidColor(Cinnabar),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (viewModel.title.isEmpty()) Text("标题", style = titleStyle, color = Muted)
                            inner()
                        },
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            viewModel.date.ifBlank { "发布时写入现在的时间" },
                            color = Muted,
                            modifier = Modifier.weight(1f),
                        )
                        AppIconButton("分类、标签和系列", onClick = { meta = !meta }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "分类、标签和系列")
                        }
                    }
                    if (meta) MetaEditor(viewModel, suggestedTags, suggestedCategories)
                }
            }
            item { ModeBar(viewModel.mode, viewModel::switchMode) }
            item { Spacer(Modifier.height(8.dp)) }
            if (viewModel.switching) {
                item {
                    Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Cinnabar)
                    }
                }
            } else when (viewModel.mode) {
                WriteMode.Source -> item { SourceEditor(viewModel, body) }
                WriteMode.Reading -> {
                    item {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(viewModel.title.ifBlank { "无题" }, style = titleStyle)
                        }
                    }
                    items(viewModel.blocks, key = { "read-${it.id}" }) { item ->
                        ReadOnlyBlock(item.block, body, formulas, blog, viewModel.path) { visitUrl = it }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                else -> items(viewModel.blocks, key = { it.id }) { item ->
                    val requester = focusRequesters.getOrPut(item.id) { FocusRequester() }
                    val dim = viewModel.mode == WriteMode.Typewriter &&
                        viewModel.activeId != null &&
                        viewModel.activeId != item.id
                    Box(Modifier.graphicsLayer { alpha = if (dim) 0.38f else 1f }.padding(bottom = if (viewModel.mode == WriteMode.Typewriter) 14.dp else 8.dp)) {
                        BlockCard(
                            item = item,
                            viewModel = viewModel,
                            blog = blog,
                            body = body,
                            requester = requester,
                            showChrome = viewModel.mode == WriteMode.Typewriter,
                                onLink = { visitUrl = it },
                                formulas = formulas,
                        )
                    }
                }
            }
        }
    }
    if (linkOpen) {
        AlertDialog(
            onDismissRequest = { linkOpen = false },
            title = { Text("插入链接") },
            text = {
                Column {
                    OutlinedTextField(linkLabel, { linkLabel = it }, label = { Text("显示的文字") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(linkUrl, { linkUrl = it }, label = { Text("地址") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.insertLink(linkLabel, linkUrl)
                    linkOpen = false
                }) { Text("插入") }
            },
            dismissButton = { TextButton(onClick = { linkOpen = false }) { Text("取消") } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("保存更改？") },
            text = { Text("返回前要不要留下这些修改？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    viewModel.saveNow()
                    onBack()
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmLeave = false }) { Text("取消") }
                    TextButton(onClick = {
                        confirmLeave = false
                        viewModel.discardSession()
                        onBack()
                    }) { Text("不保存") }
                }
            },
        )
    }
    if (visitUrl != null) {
        AlertDialog(
            onDismissRequest = { visitUrl = null },
            title = { Text("要打开这个链接吗？") },
            text = { Text(visitUrl.orEmpty()) },
            confirmButton = {
                TextButton(onClick = {
                    val url = visitUrl
                    visitUrl = null
                    if (!url.isNullOrBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }) { Text("访问") }
            },
            dismissButton = { TextButton(onClick = { visitUrl = null }) { Text("取消") } },
        )
    }
    viewModel.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = { viewModel.notice = null },
            title = { Text(notice.title) },
            text = { Text(notice.body) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.notice = null
                    if (notice.finished) onBack()
                }) { Text("好") }
            },
        )
    }
    if (viewModel.busy != null) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Text(viewModel.busy.orEmpty(), color = Color.White, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaEditor(
    viewModel: EditorViewModel,
    suggestedTags: List<String>,
    suggestedCategories: List<String>,
) {
    var tagDraft by remember { mutableStateOf("") }
    var categoryDraft by remember { mutableStateOf("") }
    Text("分类", color = Muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        viewModel.categories.forEach { category ->
            InputChip(selected = true, onClick = { viewModel.removeCategory(category) }, label = { Text(category) })
        }
        suggestedCategories.filter { it !in viewModel.categories }.take(12).forEach { category ->
            SuggestionChip(onClick = { viewModel.addCategory(category) }, label = { Text(category) })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(categoryDraft, { categoryDraft = it }, label = { Text("新分类") }, modifier = Modifier.weight(1f))
        AppIconButton("添加分类", onClick = { viewModel.addCategory(categoryDraft); categoryDraft = "" }) {
            Icon(Icons.Outlined.Add, contentDescription = "添加分类")
        }
    }
    Text("标签", color = Muted, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        viewModel.tags.forEach { tag ->
            InputChip(selected = true, onClick = { viewModel.removeTag(tag) }, label = { Text(tag) })
        }
        suggestedTags.filter { it !in viewModel.tags }.take(16).forEach { tag ->
            SuggestionChip(onClick = { viewModel.addTag(tag) }, label = { Text(tag) })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(tagDraft, { tagDraft = it }, label = { Text("新标签") }, modifier = Modifier.weight(1f))
        AppIconButton("添加标签", onClick = { viewModel.addTag(tagDraft); tagDraft = "" }) {
            Icon(Icons.Outlined.Add, contentDescription = "添加标签")
        }
    }
    OutlinedTextField(
        viewModel.series,
        { viewModel.series = it },
        label = { Text("系列，可不填") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text("置顶", modifier = Modifier.weight(1f))
        Switch(checked = viewModel.top, onCheckedChange = { viewModel.top = it })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("音乐播放器", modifier = Modifier.weight(1f))
        Switch(checked = viewModel.aplayer, onCheckedChange = { viewModel.aplayer = it })
    }
    if (!viewModel.abbrlink.isNullOrBlank()) {
        Text("固定链接 ${viewModel.abbrlink}", color = Muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlockCard(
    item: EditorBlock,
    viewModel: EditorViewModel,
    blog: BlogService,
    body: TextStyle,
    requester: FocusRequester,
    showChrome: Boolean,
    onLink: (String) -> Unit,
    formulas: FormulaBoard,
) {
    var menu by remember { mutableStateOf(false) }
    val bring = remember { BringIntoViewRequester() }
    val focused = viewModel.activeId == item.id
    LaunchedEffect(focused, viewModel.mode) {
        if (focused && viewModel.mode == WriteMode.Typewriter) bring.bringIntoView()
    }
    Column(Modifier.bringIntoViewRequester(bring)) {
        if (showChrome) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(blockLabel(item.block), color = Muted, fontSize = uiSp(12f), modifier = Modifier.weight(1f))
            Box {
                AppIconButton("更多", onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("上移一段") }, onClick = { menu = false; viewModel.move(item.id, false) }, leadingIcon = { Icon(Icons.Outlined.KeyboardArrowUp, null) })
                    DropdownMenuItem(text = { Text("下移一段") }, onClick = { menu = false; viewModel.move(item.id, true) }, leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, null) })
                    if (item.block is Block.Heading) {
                        DropdownMenuItem(text = { Text("变成正文") }, onClick = { menu = false; viewModel.activeId = item.id; viewModel.convert("p") })
                    } else if (item.block is Block.Paragraph) {
                        DropdownMenuItem(text = { Text("变成标题") }, onClick = { menu = false; viewModel.activeId = item.id; viewModel.convert("h") })
                    } else {
                        DropdownMenuItem(text = { Text("变成正文") }, onClick = { menu = false; viewModel.activeId = item.id; viewModel.convert("p") })
                    }
                    DropdownMenuItem(text = { Text("变成公式") }, onClick = { menu = false; viewModel.activeId = item.id; viewModel.convert("math") })
                    DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; viewModel.delete(item.id) })
                }
            }
        }
        }
        when (val block = item.block) {
            is Block.Paragraph -> RichOrEdit(block.text, item.id, 0, viewModel, body, requester, Modifier.fillMaxWidth(), onLink, formulas)
            is Block.Heading -> RichOrEdit(
                block.text,
                item.id,
                0,
                viewModel,
                body.copy(fontSize = headingSize(block.level), fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
                requester,
                Modifier.fillMaxWidth(),
                onLink,
                formulas,
            )
            is Block.Quote -> QuoteFrame {
                block.alert?.let { alert -> Text(alertLabel(alert), color = Cinnabar, fontSize = articleSp(12f)) }
                RichOrEdit(block.text, item.id, 0, viewModel, body, requester, Modifier.fillMaxWidth(), onLink, formulas)
            }
            is Block.Math -> FormulaImage(block.latex, display = true, board = formulas, style = body, modifier = Modifier.fillMaxWidth())
            is Block.BulletList -> ListBlock(item.id, block.items, ordered = false, viewModel, body, requester)
            is Block.OrderedList -> ListBlock(item.id, block.items, ordered = true, viewModel, body, requester)
            is Block.Code -> PlainBox(
                label = "代码" + if (block.language.isBlank()) "" else " · ${block.language}",
                value = block.code,
                placeholder = "只写代码本身",
                monospace = true,
                ground = if (isSystemInDarkTheme()) Color(0xFF2A241C) else Color(0xFFF3EDE3),
                requester = requester,
                onValue = { viewModel.onPlain(item.id, it) },
            )
            is Block.Math -> FormulaBox(block.latex, requester) { viewModel.onPlain(item.id, it) }
            is Block.Image -> ImageCard(block, viewModel.path, blog) { viewModel.updateImageAlt(item.id, it) }
            is Block.Table -> TableCard(item.id, block, viewModel, body)
            is Block.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
            is Block.Raw -> PlainBox(
                label = "原样保留",
                value = block.text,
                placeholder = "",
                monospace = true,
                ground = MaterialTheme.colorScheme.surfaceVariant,
                requester = requester,
                onValue = { viewModel.onPlain(item.id, it) },
            )
        }
    }
}

@Composable
private fun MarkupBlock(
    id: Long,
    item: Int,
    text: String,
    viewModel: EditorViewModel,
    style: TextStyle,
    requester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    MarkupField(
        text = text,
        selectionStart = viewModel.selectionStart,
        selectionEnd = viewModel.selectionEnd,
        revision = viewModel.revision,
        active = viewModel.activeId == id && viewModel.activeItem == item,
        onValue = { viewModel.onMarkup(id, item, it) },
        textStyle = style,
        modifier = modifier
            .focusRequester(requester)
            .onFocusChanged {
                if (it.isFocused) viewModel.focus(id, item, viewModel.selectionStart, viewModel.selectionEnd)
            },
    )
}

@Composable
private fun ListBlock(
    id: Long,
    items: List<String>,
    ordered: Boolean,
    viewModel: EditorViewModel,
    style: TextStyle,
    requester: FocusRequester,
) {
    val rows = items.ifEmpty { listOf("") }
    Column {
        rows.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    if (ordered) "${index + 1}." else "•",
                    style = style,
                    color = Cinnabar,
                    modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                )
                MarkupBlock(
                    id,
                    index,
                    item,
                    viewModel,
                    style,
                    if (index == 0) requester else remember { FocusRequester() },
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FormulaBox(latex: String, requester: FocusRequester, onValue: (String) -> Unit) {
    var field by remember { mutableStateOf(TextFieldValue(latex)) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(FormulaGround, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text("公式", color = Moss, fontSize = uiSp(13f))
        BasicTextField(
            value = field,
            onValueChange = {
                field = it
                onValue(it.text)
            },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = articleSp(16f), lineHeight = articleSp(24f), color = Ink),
            cursorBrush = SolidColor(Moss),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(top = 8.dp)
                .focusRequester(requester),
            decorationBox = { inner ->
                if (field.text.isEmpty()) {
                    Text("在这里写 LaTeX，例如 \\alpha_t + \\beta", color = Muted, fontFamily = FontFamily.Monospace)
                }
                inner()
            },
        )
    }
}

@Composable
private fun PlainBox(
    label: String,
    value: String,
    placeholder: String,
    monospace: Boolean,
    ground: Color,
    requester: FocusRequester,
    onValue: (String) -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    Column(Modifier.fillMaxWidth().background(ground, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(label, color = Muted, fontSize = uiSp(13f))
        BasicTextField(
            value = field,
            onValueChange = { field = it; onValue(it.text) },
            textStyle = TextStyle(
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Serif,
                fontSize = articleSp(15f),
                lineHeight = articleSp(22f),
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(Cinnabar),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(top = 8.dp).focusRequester(requester),
            decorationBox = { inner ->
                if (field.text.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = Muted)
                inner()
            },
        )
    }
}

@Composable
private fun ImageCard(image: Block.Image, postPath: String?, blog: BlogService, editable: Boolean = true, onAlt: (String) -> Unit) {
    var bitmap by remember(image.src, image.pendingFile) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(image.src, image.pendingFile, postPath) {
        bitmap = withContext(Dispatchers.IO) {
            val pending = image.pendingFile
            val bytes = if (!pending.isNullOrBlank()) {
                runCatching { File(pending).readBytes() }.getOrNull()
            } else if (postPath != null) {
                val remote = HexoPaths.repoPathForImage(blog.settings.postsPath, postPath, image.src)
                remote?.let { runCatching { blog.loadImage(it) }.getOrNull() }
            } else {
                null
            }
            bytes?.let { decodePreview(it) }
        }
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(12.dp)) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap!!,
                contentDescription = image.alt.ifBlank { "图片" },
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(image.alt.ifBlank { "图片" }, modifier = Modifier.padding(vertical = 24.dp))
        }
        if (editable) {
            OutlinedTextField(image.alt, onAlt, label = { Text("图片说明") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
private fun TableCard(id: Long, table: Block.Table, viewModel: EditorViewModel, style: TextStyle) {
    val width = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0, 1)
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        TableRow(table.header, width, style) { column, text -> viewModel.updateTable(id, true, 0, column, text) }
        table.rows.forEachIndexed { row, cells ->
            TableRow(cells, width, style) { column, text -> viewModel.updateTable(id, false, row, column, text) }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, width: Int, style: TextStyle, onCell: (Int, String) -> Unit) {
    Row {
        repeat(width) { column ->
            var text by remember { mutableStateOf(cells.getOrElse(column) { "" }) }
            BasicTextField(
                value = text,
                onValueChange = { text = it; onCell(column, it) },
                textStyle = style.copy(fontSize = articleSp(15f)),
                visualTransformation = remember { MarkdownVisualTransformation() },
                modifier = Modifier.width(140.dp).padding(4.dp),
            )
        }
    }
}

@Composable
private fun FormatBar(
    modifier: Modifier,
    inlineEnabled: Boolean,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onStrike: () -> Unit,
    onCode: () -> Unit,
    onLink: () -> Unit,
    onInlineMath: () -> Unit,
    onHeading: (Int) -> Unit,
    onQuote: () -> Unit,
    onList: () -> Unit,
    onOrdered: () -> Unit,
    onCodeBlock: () -> Unit,
    onMathBlock: () -> Unit,
    onRule: () -> Unit,
    onImage: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolIcon("粗体", inlineEnabled, onBold) { Icon(Icons.Outlined.FormatBold, null) }
        ToolIcon("斜体", inlineEnabled, onItalic) { Icon(Icons.Outlined.FormatItalic, null) }
        ToolIcon("删除线", inlineEnabled, onStrike) { Icon(Icons.Outlined.FormatStrikethrough, null) }
        ToolIcon("行内代码", inlineEnabled, onCode) { Icon(Icons.Outlined.Code, null) }
        ToolIcon("链接", inlineEnabled, onLink) { Icon(Icons.Outlined.Link, null) }
        ToolIcon("行内公式", inlineEnabled, onInlineMath) { Icon(Icons.Outlined.Superscript, null) }
        (1..6).forEach { level ->
            ToolIcon("${level}级标题", true, { onHeading(level) }) { HeadingMark(level) }
        }
        ToolIcon("引用", true, onQuote) { Icon(Icons.Outlined.FormatQuote, null) }
        ToolIcon("无序列表", true, onList) { Icon(Icons.Outlined.FormatListBulleted, null) }
        ToolIcon("有序列表", true, onOrdered) { Icon(Icons.Outlined.FormatListNumbered, null) }
        ToolIcon("代码块", true, onCodeBlock) { Icon(Icons.Outlined.Code, null) }
        ToolIcon("公式块", true, onMathBlock) { Icon(Icons.Outlined.Functions, null) }
        ToolIcon("分割线", true, onRule) { Icon(Icons.Outlined.HorizontalRule, null) }
        ToolIcon("图片", true, onImage) { Icon(Icons.Outlined.Image, null) }
    }
}

@Composable
private fun ToolIcon(label: String, enabled: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    AppIconButton(label = label, onClick = onClick, enabled = enabled, icon = icon)
}

@Composable
internal fun QuoteFrame(content: @Composable () -> Unit) {
    // 行内公式是 SubcomposeLayout，Row 的 IntrinsicSize 会向它要固有高度并直接崩溃。
    // 色条改在内容背后绘制，高度跟正文走，不再做固有测量。
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .drawBehind {
                drawRect(color = Cinnabar, size = Size(3.dp.toPx(), size.height))
            }
            .padding(start = 17.dp),
    ) { content() }
}

@Composable
private fun RichOrEdit(
    text: String,
    id: Long,
    item: Int,
    viewModel: EditorViewModel,
    style: TextStyle,
    requester: FocusRequester,
    modifier: Modifier,
    onLink: (String) -> Unit,
    formulas: FormulaBoard,
) {
    val focused = viewModel.activeId == id
    if (!focused && text.contains('$')) {
        InlineRich(text, style, formulas, onLink, modifier.clickable { viewModel.focus(id, item, 0, 0) })
    } else {
        MarkupBlock(id, item, text, viewModel, style, requester, modifier)
    }
}

@Composable
private fun ReadOnlyBlock(
    block: Block,
    style: TextStyle,
    formulas: FormulaBoard,
    blog: BlogService,
    postPath: String?,
    onLink: (String) -> Unit,
) {
    when (block) {
        is Block.Paragraph -> InlineRich(block.text, style, formulas, onLink, Modifier.fillMaxWidth())
        is Block.Heading -> InlineRich(
            block.text,
            style.copy(fontSize = headingSize(block.level), fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
            formulas,
            onLink,
            Modifier.fillMaxWidth(),
        )
        is Block.Quote -> QuoteFrame {
            block.alert?.let { Text(alertLabel(it), color = Cinnabar, fontSize = articleSp(12f)) }
            InlineRich(block.text, style, formulas, onLink, Modifier.fillMaxWidth())
        }
        is Block.BulletList -> Column {
            block.items.forEach { InlineRich("•  $it", style, formulas, onLink, Modifier.fillMaxWidth()) }
        }
        is Block.OrderedList -> Column {
            block.items.forEachIndexed { index, item ->
                InlineRich("${index + 1}.  $item", style, formulas, onLink, Modifier.fillMaxWidth())
            }
        }
        is Block.Code -> Text(block.code, style = style.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp))
        is Block.Math -> FormulaImage(block.latex, display = true, board = formulas, style = style, modifier = Modifier.fillMaxWidth())
        is Block.Image -> ImageCard(block, postPath, blog, editable = false) {}
        is Block.Table -> Text(block.rows.joinToString("\n") { it.joinToString("  ") }, style = style)
        is Block.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
        is Block.Raw -> Text(block.text, style = style.copy(fontFamily = FontFamily.Monospace))
    }
}

@Composable
private fun TypewriterMark() {
    Canvas(Modifier.size(22.dp)) {
        drawRoundRect(color = Color(0xFF1E1A16), topLeft = Offset(1f, size.height * 0.42f), size = Size(size.width - 2f, size.height * 0.48f), cornerRadius = CornerRadius(3f, 3f))
        drawRect(color = Color(0xFFF6F1E7), topLeft = Offset(size.width * 0.18f, size.height * 0.12f), size = Size(size.width * 0.64f, size.height * 0.38f))
        drawRect(color = Color(0xFF8E3B32), topLeft = Offset(size.width * 0.22f, size.height * 0.55f), size = Size(size.width * 0.12f, size.height * 0.12f))
    }
}

@Composable
private fun HeadingMark(level: Int) {
    Text("H$level", fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = uiSp(13f), color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun ModeBar(mode: WriteMode, onSelect: (WriteMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ModeIcon(WriteMode.Wysiwyg, mode, onSelect, "所见即所得") { Icon(Icons.Outlined.Edit, null) }
        ModeIcon(WriteMode.Source, mode, onSelect, "源代码") { Icon(Icons.Outlined.Code, null) }
        ModeIcon(WriteMode.Reading, mode, onSelect, "阅读") { Icon(Icons.Outlined.MenuBook, null) }
        ModeIcon(WriteMode.Typewriter, mode, onSelect, "打字机") { TypewriterMark() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeIcon(target: WriteMode, current: WriteMode, onSelect: (WriteMode) -> Unit, label: String, icon: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = { onSelect(target) }) {
            Box(
                Modifier
                    .background(if (target == current) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(10.dp))
                    .padding(4.dp),
            ) { icon() }
        }
    }
}

@Composable
private fun SourceEditor(viewModel: EditorViewModel, body: TextStyle) {
    var field by remember { mutableStateOf(TextFieldValue(viewModel.sourceText)) }
    LaunchedEffect(viewModel.sourceRevision) {
        val start = viewModel.sourceSelectionStart.coerceIn(0, viewModel.sourceText.length)
        val end = viewModel.sourceSelectionEnd.coerceIn(start, viewModel.sourceText.length)
        field = TextFieldValue(viewModel.sourceText, androidx.compose.ui.text.TextRange(start, end))
    }
    BasicTextField(
        value = field,
        onValueChange = {
            field = it
            viewModel.onSource(it)
        },
        textStyle = body.copy(fontFamily = FontFamily.Serif, fontSize = articleSp(16f), lineHeight = articleSp(26f)),
        cursorBrush = SolidColor(Cinnabar),
        visualTransformation = remember { SourceMarkupTransformation() },
        modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp),
    )
}

@Composable
private fun ReadBlock(block: Block, postPath: String?, blog: BlogService, body: TextStyle) {
    when (block) {
        is Block.Paragraph -> StyledReadText(block.text, body)
        is Block.Heading -> StyledReadText(block.text, body.copy(fontSize = headingSize(block.level), fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold))
        is Block.Quote -> Column(Modifier.border(3.dp, MaterialTheme.colorScheme.surfaceVariant).padding(start = 12.dp)) {
            block.alert?.let { Text(alertLabel(it), color = Cinnabar, fontSize = articleSp(12f)) }
            StyledReadText(block.text, body)
        }
        is Block.BulletList -> Column { block.items.forEach { StyledReadText("•  $it", body) } }
        is Block.OrderedList -> Column { block.items.forEachIndexed { index, item -> StyledReadText("${index + 1}.  $item", body) } }
        is Block.Code -> Text(block.code, style = body.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp))
        is Block.Math -> Text(block.latex, style = body.copy(fontFamily = FontFamily.Monospace, color = Moss), modifier = Modifier.fillMaxWidth().background(FormulaGround, RoundedCornerShape(12.dp)).padding(12.dp))
        is Block.Image -> ImageCard(block, postPath, blog, editable = false) {}
        is Block.Table -> Text(block.header.joinToString("  ") + "\n" + block.rows.joinToString("\n") { it.joinToString("  ") }, style = body)
        is Block.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
        is Block.Raw -> Text(block.text, style = body.copy(fontFamily = FontFamily.Monospace))
    }
}

@Composable
private fun StyledReadText(markdown: String, style: TextStyle) {
    val presented = dev.mobwriter.app.core.markdown.InlineMarkup.present(markdown)
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        append(presented.text)
        presented.spans.forEach { span ->
            val spanStyle = when (span.kind) {
                dev.mobwriter.app.core.markdown.InlineKind.Bold -> androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold)
                dev.mobwriter.app.core.markdown.InlineKind.Italic -> androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                dev.mobwriter.app.core.markdown.InlineKind.Strike -> androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                dev.mobwriter.app.core.markdown.InlineKind.Code -> androidx.compose.ui.text.SpanStyle(fontFamily = FontFamily.Monospace)
                dev.mobwriter.app.core.markdown.InlineKind.Link -> androidx.compose.ui.text.SpanStyle(color = Cinnabar, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                dev.mobwriter.app.core.markdown.InlineKind.Math -> androidx.compose.ui.text.SpanStyle(fontFamily = FontFamily.Monospace, color = Moss)
            }
            addStyle(spanStyle, span.start, span.end)
        }
    }
    SelectionContainer { Text(annotated, style = style) }
}

private fun blockLabel(block: Block): String = when (block) {
    is Block.Paragraph -> "正文"
    is Block.Heading -> when (block.level) {
        1 -> "一级标题"
        2 -> "二级标题"
        3 -> "三级标题"
        4 -> "四级标题"
        5 -> "五级标题"
        else -> "六级标题"
    }
    is Block.Quote -> "引用"
    is Block.BulletList -> "列表"
    is Block.OrderedList -> "编号"
    is Block.Code -> "代码"
    is Block.Math -> "公式"
    is Block.Image -> "图片"
    is Block.Table -> "表格"
    is Block.Rule -> "分割线"
    is Block.Raw -> "原样"
}

@Composable
private fun headingSize(level: Int): TextUnit = articleHeadingSize(level, LocalAppearance.current.article)

@Composable
private fun articleSp(base: Float): TextUnit = (base * LocalAppearance.current.article.scale).sp

@Composable
private fun uiSp(base: Float): TextUnit = (base * LocalAppearance.current.ui.scale).sp

private fun decodePreview(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

private fun alertLabel(alert: String): String = when (alert) {
    "NOTE" -> "注意"
    "TIP" -> "提示"
    "IMPORTANT" -> "重要"
    "WARNING" -> "警告"
    "CAUTION" -> "小心"
    else -> alert
}
