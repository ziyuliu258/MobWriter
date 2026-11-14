package dev.mobwriter.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mobwriter.app.core.markdown.InlineKind
import dev.mobwriter.app.core.markdown.InlineMarkup
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.awt.AndroidGraphics2D
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * KaTeX 的默认字号是周围文字的 1.21 倍。Typora 和 MarkText 都沿用这个比例，
 * 让数学字体的 x 高和正文字母对齐，而不是另定一个像素值。
 */
private const val MathEmScale = 1.21f

private const val FormulaCacheBytes = 3 * 1024 * 1024
private const val FormulaCacheEntries = 24

/** 只挡住极端长公式，避免一张位图吃掉过多内存。正常长公式按原尺寸绘制，不再裁成 1600px。 */
internal const val FormulaBitmapMaxSide = 8192
internal const val FormulaBitmapMaxPixels = 4_000_000

internal fun formulaBitmapScale(rawWidth: Int, rawHeight: Int): Float {
    if (rawWidth <= 0 || rawHeight <= 0) return 1f
    val side = min(
        1f,
        min(
            FormulaBitmapMaxSide.toFloat() / rawWidth,
            FormulaBitmapMaxSide.toFloat() / rawHeight,
        ),
    )
    val pixels = rawWidth.toDouble() * rawHeight.toDouble()
    val area = if (pixels > FormulaBitmapMaxPixels) {
        sqrt(FormulaBitmapMaxPixels / pixels).toFloat()
    } else {
        1f
    }
    return min(side, area).coerceAtMost(1f)
}

private val mathDispatcher = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "formula").apply { priority = Thread.NORM_PRIORITY - 1 }
}.asCoroutineDispatcher()

private val mathLock = Any()
@Volatile private var mathReady = false

/** 字体表在 TeXFormula 的类初始化里加载。没先 init 时失败是 Error，公式线程会把进程带走。 */
internal fun ensureMathEngine(context: Context) {
    if (mathReady) return
    synchronized(mathLock) {
        if (mathReady) return
        JLatexMathAndroid.init(context.applicationContext)
        mathReady = true
    }
}

class FormulaBoard {
    private val pictures = object : LinkedHashMap<String, FormulaPicture>(16, 0.75f, true) {}
    private var bytes = 0

    internal fun cached(key: String): FormulaPicture? = synchronized(this) { pictures[key] }

    internal fun store(key: String, picture: FormulaPicture) = synchronized(this) {
        val previous = pictures.put(key, picture)
        if (previous != null) bytes -= previous.bytes
        bytes += picture.bytes
        val order = pictures.entries.iterator()
        while (order.hasNext() && (bytes > FormulaCacheBytes || pictures.size > FormulaCacheEntries)) {
            val eldest = order.next()
            bytes -= eldest.value.bytes
            order.remove()
        }
    }
}

internal class FormulaPicture(
    val bitmap: ImageBitmap,
    val widthPx: Int,
    val heightPx: Int,
    val baselinePx: Int,
    val bytes: Int,
)

private fun renderFormula(
    latex: String,
    emPx: Float,
    display: Boolean,
    color: Int,
    board: FormulaBoard,
    context: Context,
): FormulaPicture? {
    val source = latex.trim()
    if (source.isEmpty()) return null
    ensureMathEngine(context)
    val key = (if (display) "D" else "T") + ":" + emPx.roundToInt() + ":" + color + ":" + source
    board.cached(key)?.let { return it }
    val picture = try {
        val icon = fittedIcon(source, emPx, display, color)
        val pad = 1
        val width = (icon.iconWidth + pad * 2).coerceAtLeast(1)
        val height = (icon.iconHeight + pad * 2).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.density = Bitmap.DENSITY_NONE
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        canvas.translate(pad.toFloat(), pad.toFloat())
        val graphics = AndroidGraphics2D()
        graphics.setCanvas(canvas)
        icon.paintIcon(null, graphics, 0, 0)
        val baseline = (pad + icon.iconHeight - icon.iconDepth).coerceIn(0, height)
        FormulaPicture(bitmap.asImageBitmap(), width, height, baseline, width * height * 4)
    } catch (_: Throwable) {
        null
    }
    if (picture != null) board.store(key, picture)
    return picture
}

private fun texIcon(source: String, emPx: Float, display: Boolean, color: Int) =
    TeXFormula(source).TeXIconBuilder()
        .setStyle(if (display) TeXConstants.STYLE_DISPLAY else TeXConstants.STYLE_TEXT)
        .setSize(emPx.coerceAtLeast(1f))
        .setTrueValues(true)
        .setFGColor(ru.noties.jlatexmath.awt.Color(color))
        .build()

/** 超过内存上限时按比例缩小字号重绘，整条公式都在位图里，而不是从右侧裁掉。 */
private fun fittedIcon(source: String, emPx: Float, display: Boolean, color: Int): org.scilab.forge.jlatexmath.TeXIcon {
    var size = emPx.coerceAtLeast(1f)
    var icon = texIcon(source, size, display, color)
    var guard = 0
    while (guard++ < 4 && formulaBitmapScale(icon.iconWidth + 2, icon.iconHeight + 2) < 0.999f) {
        val fit = formulaBitmapScale(icon.iconWidth + 2, icon.iconHeight + 2)
        size = (size * fit * 0.98f).coerceAtLeast(1f)
        icon = texIcon(source, size, display, color)
    }
    return icon
}

private fun mathEmPx(fontPx: Float): Float = fontPx * MathEmScale

@Composable
private fun rememberMathEm(style: TextStyle): Float {
    val density = LocalDensity.current
    val font = if (style.fontSize == TextUnit.Unspecified) 17.sp else style.fontSize
    return mathEmPx(with(density) { font.toPx() })
}

private fun formulaColor(style: TextStyle): Int =
    (if (style.color == Color.Unspecified) Ink else style.color).toArgb()

private fun Color.toArgb(): Int {
    return ((alpha * 255f).roundToInt().coerceIn(0, 255) shl 24) or
        ((red * 255f).roundToInt().coerceIn(0, 255) shl 16) or
        ((green * 255f).roundToInt().coerceIn(0, 255) shl 8) or
        (blue * 255f).roundToInt().coerceIn(0, 255)
}

@Composable
fun rememberFormulaBoard(): FormulaBoard = remember { FormulaBoard() }

@Composable
fun FormulaImage(
    latex: String,
    display: Boolean,
    board: FormulaBoard,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val emPx = rememberMathEm(style)
    val color = formulaColor(style)
    val key = latex.trim() + ":" + emPx.roundToInt() + ":" + color + ":" + display
    var picture by remember(key) { mutableStateOf(board.cached((if (display) "D" else "T") + ":" + emPx.roundToInt() + ":" + color + ":" + latex.trim())) }
    LaunchedEffect(key) {
        picture = withContext(mathDispatcher) { renderFormula(latex, emPx, display, color, board, context) }
    }
    val image = picture
    BoxWithConstraints(
        modifier.padding(vertical = if (display) 8.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            val maxPx = with(density) { maxWidth.toPx() }
            val overflows = maxPx.isFinite() && maxPx > 0f && image.widthPx > maxPx + 1f
            if (overflows) {
                OverflowFormula(image, latex, Modifier.fillMaxWidth())
            } else {
                Image(
                    bitmap = image.bitmap,
                    contentDescription = latex,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.size(
                        with(density) { image.widthPx.toDp() },
                        with(density) { image.heightPx.toDp() },
                    ),
                )
            }
        } else if (latex.isNotBlank()) {
            androidx.compose.material3.Text(
                latex,
                style = style.copy(fontFamily = FontFamily.Monospace, color = Moss),
            )
        }
    }
}

/**
 * 只有公式本身横向滚动，停在左右两端。垂直方向不消费，文章仍可上下滑。
 * 右侧淡出表示后面还有内容；滑到尽头后淡出消失。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OverflowFormula(
    picture: FormulaPicture,
    description: String,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val background = MaterialTheme.colorScheme.background
    val moreOnRight = scroll.maxValue > 0 && scroll.value < scroll.maxValue
    val holdHorizontal = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                Offset(available.x, 0f)
        }
    }
    Box(
        modifier
            .clipToBounds()
            .nestedScroll(holdHorizontal)
            .drawWithContent {
                drawContent()
                if (!moreOnRight) return@drawWithContent
                val fade = 22.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(background.copy(alpha = 0f), background.copy(alpha = 0.62f)),
                        startX = size.width - fade,
                        endX = size.width,
                    ),
                    topLeft = Offset(size.width - fade, 0f),
                    size = Size(fade, size.height),
                )
            },
    ) {
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null,
            LocalLayoutDirection provides LayoutDirection.Ltr,
        ) {
            Box(Modifier.horizontalScroll(scroll)) {
                Image(
                    bitmap = picture.bitmap,
                    contentDescription = description,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.size(
                        with(density) { picture.widthPx.toDp() },
                        with(density) { picture.heightPx.toDp() },
                    ),
                )
            }
        }
    }
}

/** 轻点进入编辑。横向或纵向滑过触摸阈值都不当作点击，避免滑动公式时误进源码。 */
internal fun Modifier.tapToEdit(onTap: () -> Unit): Modifier = pointerInput(onTap) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = down.position
        val pointer = down.id
        val slop = viewConfiguration.touchSlop
        var tap = true
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointer } ?: return@awaitEachGesture
            if ((change.position - start).getDistance() > slop || change.isConsumed) tap = false
            if (change.changedToUp()) {
                if (tap) onTap()
                return@awaitEachGesture
            }
        }
    }
}

@Composable
fun InlineRich(
    markdown: String,
    style: TextStyle,
    board: FormulaBoard,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parts = remember(markdown) { splitInline(markdown) }
    if (parts.none { it is InlinePiece.Math }) {
        LinkedText(markdown, style, onLink, modifier)
        return
    }
    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val emPx = rememberMathEm(style)
    val color = formulaColor(style)
    val formulas = remember(parts) { parts.mapNotNull { (it as? InlinePiece.Math)?.latex?.trim() }.filter { it.isNotEmpty() }.distinct() }
    val cacheKey = formulas.joinToString("\u0001") + ":" + emPx.roundToInt() + ":" + color
    var pictures by remember(cacheKey) {
        mutableStateOf(
            formulas.mapNotNull { latex ->
                board.cached("T:" + emPx.roundToInt() + ":" + color + ":" + latex)?.let { latex to it }
            }.toMap(),
        )
    }
    LaunchedEffect(cacheKey) {
        pictures = withContext(mathDispatcher) {
            formulas.mapNotNull { latex -> renderFormula(latex, emPx, display = false, color, board, context)?.let { latex to it } }.toMap()
        }
    }
    val measurer = rememberTextMeasurer()
    val flowStyle = style.copy(
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Proportional,
            trim = LineHeightStyle.Trim.None,
        ),
    )
    val hits = remember { HitList() }
    SubcomposeLayout(
        modifier.pointerInput(onLink) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                val href = hits.find(down.position.x, down.position.y)
                if (href != null) {
                    down.consume()
                    up.consume()
                    onLink(href)
                }
            }
        },
    ) { constraints ->
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else with(density) { 360.dp.roundToPx() }
        val probe = measurer.measure(AnnotatedString("国"), style = flowStyle)
        val refAscent = probe.firstBaseline.roundToInt().coerceAtLeast(1)
        val refDescent = (probe.size.height - probe.firstBaseline).roundToInt().coerceAtLeast(1)
        val slots = mutableListOf<Slot>()
        hits.clear()
        var x = 0
        var y = 0
        val line = mutableListOf<Slot>()

        fun closeLine() {
            if (line.isEmpty()) return
            val ascent = maxOf(refAscent, line.maxOf { it.ascent })
            val descent = maxOf(refDescent, line.maxOf { it.descent })
            val baseline = y + ascent
            line.forEach { slot ->
                slots += slot.copy(top = baseline - slot.ascent)
            }
            y += ascent + descent
            x = 0
            line.clear()
        }

        fun addSlot(slot: Slot) {
            if (x > 0 && x + slot.width > maxWidth) closeLine()
            line += slot.copy(left = x)
            x += slot.width
        }

        fun takeText(annotated: AnnotatedString) {
            var rest = annotated
            var guard = 0
            while (rest.isNotEmpty() && guard++ < rest.length + 2) {
                if (x > 0 && maxWidth - x < refAscent) closeLine()
                val room = (maxWidth - x).coerceAtLeast(1)
                val layout = measurer.measure(
                    rest,
                    style = flowStyle,
                    overflow = TextOverflow.Clip,
                    softWrap = true,
                    constraints = Constraints(maxWidth = room),
                )
                if (layout.lineCount <= 1) {
                    val width = layout.size.width.coerceAtLeast(0)
                    addSlot(textSlot(rest, layout, width))
                    break
                }
                val visible = layout.getLineEnd(0, true).coerceIn(0, rest.length)
                val rawConsumed = layout.getLineEnd(0, false).coerceIn(0, rest.length)
                val consumed = if (rawConsumed > visible) rawConsumed else visible.coerceAtLeast(1)
                if (visible == 0) {
                    closeLine()
                    rest = rest.subSequence(consumed.coerceAtMost(rest.length), rest.length)
                    continue
                }
                val slice = rest.subSequence(0, visible)
                val sliceLayout = measurer.measure(
                    slice,
                    style = flowStyle,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = room + 8),
                )
                addSlot(textSlot(slice, sliceLayout, sliceLayout.size.width.coerceAtMost(room + 8)))
                closeLine()
                rest = rest.subSequence(consumed.coerceAtMost(rest.length), rest.length)
            }
        }

        parts.forEach { part ->
            when (part) {
                is InlinePiece.Text -> {
                    val annotated = presentText(part.markdown)
                    if (annotated.isNotEmpty()) takeText(annotated)
                }
                is InlinePiece.Math -> {
                    val source = part.latex.trim()
                    val picture = pictures[source]
                    if (picture == null) {
                        if (source.isNotEmpty()) {
                            val box = emPx.roundToInt().coerceAtLeast(refAscent)
                            addSlot(Slot(width = box, ascent = (box * 0.75f).roundToInt(), descent = (box * 0.25f).roundToInt()))
                        }
                    } else if (picture.widthPx > maxWidth && maxWidth > 0) {
                        if (x > 0) closeLine()
                        val descent = (picture.heightPx - picture.baselinePx).coerceAtLeast(0)
                        addSlot(
                            Slot(
                                width = maxWidth,
                                ascent = picture.baselinePx,
                                descent = descent,
                                picture = picture,
                                pictureWidth = picture.widthPx,
                                pictureHeight = picture.heightPx,
                                label = source,
                                scrolls = true,
                            ),
                        )
                        closeLine()
                    } else {
                        val descent = (picture.heightPx - picture.baselinePx).coerceAtLeast(0)
                        addSlot(
                            Slot(
                                width = picture.widthPx,
                                ascent = picture.baselinePx,
                                descent = descent,
                                picture = picture,
                                pictureWidth = picture.widthPx,
                                pictureHeight = picture.heightPx,
                                label = source,
                            ),
                        )
                    }
                }
            }
        }
        closeLine()
        val height = y.coerceAtLeast(refAscent + refDescent)
        val width = maxWidth.coerceAtLeast(constraints.minWidth)
        val placeables = slots.mapIndexed { index, slot ->
            val placeable = subcompose(index) {
                if (slot.picture != null && slot.scrolls) {
                    OverflowFormula(
                        slot.picture,
                        slot.label.orEmpty(),
                        Modifier.fillMaxWidth(),
                    )
                } else if (slot.picture != null) {
                    Image(
                        bitmap = slot.picture.bitmap,
                        contentDescription = slot.label,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.size(
                            with(density) { slot.pictureWidth.toDp() },
                            with(density) { slot.pictureHeight.toDp() },
                        ),
                    )
                } else if (slot.text != null) {
                    BasicText(
                        text = slot.text,
                        style = flowStyle,
                        overflow = TextOverflow.Clip,
                        softWrap = false,
                        maxLines = 1,
                    )
                }
            }.firstOrNull()?.measure(
                Constraints(
                    maxWidth = slot.width.coerceAtLeast(1) + if (slot.picture == null) 8 else 0,
                    maxHeight = if (slot.scrolls) {
                        Constraints.Infinity
                    } else {
                        (slot.ascent + slot.descent).coerceAtLeast(1)
                    },
                ),
            )
            placeable to slot
        }
        layout(width, height) {
            placeables.forEach { (placeable, slot) ->
                if (placeable == null) return@forEach
                val top = slot.top
                placeable.place(slot.left, top)
                val text = slot.text
                val layout = slot.layout
                if (text != null && layout != null) {
                    recordLinks(hits, text, layout, slot.left.toFloat(), top.toFloat())
                }
            }
        }
    }
}

private fun textSlot(text: AnnotatedString, layout: TextLayoutResult, width: Int): Slot {
    val ascent = layout.firstBaseline.roundToInt().coerceAtLeast(0)
    val descent = (layout.size.height - layout.firstBaseline).roundToInt().coerceAtLeast(0)
    return Slot(width = width.coerceAtLeast(1), ascent = ascent, descent = descent, text = text, layout = layout)
}

private class Slot(
    val width: Int,
    val ascent: Int,
    val descent: Int,
    val left: Int = 0,
    val top: Int = 0,
    val text: AnnotatedString? = null,
    val layout: TextLayoutResult? = null,
    val picture: FormulaPicture? = null,
    val pictureWidth: Int = 0,
    val pictureHeight: Int = 0,
    val label: String? = null,
    val scrolls: Boolean = false,
) {
    fun copy(left: Int = this.left, top: Int = this.top) = Slot(
        width, ascent, descent, left, top, text, layout, picture, pictureWidth, pictureHeight, label, scrolls,
    )
}

private class Hit(val left: Float, val top: Float, val right: Float, val bottom: Float, val href: String)

private class HitList {
    private val hits = mutableListOf<Hit>()
    fun clear() = hits.clear()
    fun add(hit: Hit) { hits += hit }
    fun find(x: Float, y: Float): String? = hits.firstOrNull { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }?.href
}

private fun recordLinks(hits: HitList, text: AnnotatedString, layout: TextLayoutResult, x: Float, y: Float) {
    text.getStringAnnotations("link", 0, text.length).forEach { ann ->
        if (ann.end <= ann.start) return@forEach
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        var index = ann.start
        while (index < ann.end && index < text.length) {
            val box = layout.getBoundingBox(index)
            left = min(left, box.left)
            top = min(top, box.top)
            right = maxOf(right, box.right)
            bottom = maxOf(bottom, box.bottom)
            index++
        }
        if (left < right) hits.add(Hit(x + left, y + top, x + right, y + bottom, ann.item))
    }
}

private fun presentText(markdown: String): AnnotatedString {
    val presented = InlineMarkup.present(markdown)
    return buildAnnotatedString {
        append(presented.text)
        presented.spans.forEach { span ->
            if (!span.href.isNullOrBlank()) {
                val href = span.href
                if (href != null) addStringAnnotation("link", href, span.start, span.end)
            }
            addStyle(linkStyle(span.kind), span.start, span.end)
        }
    }
}

@Composable
private fun LinkedText(
    markdown: String,
    style: TextStyle,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(markdown) { presentText(markdown) }
    ClickableText(annotated, modifier = modifier, style = style, onClick = { offset ->
        annotated.getStringAnnotations("link", offset, offset).firstOrNull()?.let { onLink(it.item) }
    })
}

private fun linkStyle(kind: InlineKind): SpanStyle = when (kind) {
    InlineKind.Bold -> SpanStyle(fontWeight = FontWeight.SemiBold)
    InlineKind.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
    InlineKind.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    InlineKind.Code -> SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFF3EDE3))
    InlineKind.Link -> SpanStyle(color = Cinnabar, textDecoration = TextDecoration.Underline)
    InlineKind.Math -> SpanStyle(fontFamily = FontFamily.Monospace, color = Moss)
}

private sealed interface InlinePiece {
    data class Text(val markdown: String) : InlinePiece
    data class Math(val latex: String) : InlinePiece
}

private fun splitInline(raw: String): List<InlinePiece> {
    val parts = mutableListOf<InlinePiece>()
    val buf = StringBuilder()
    var index = 0
    fun flush() {
        if (buf.isNotEmpty()) {
            parts += InlinePiece.Text(buf.toString())
            buf.clear()
        }
    }
    while (index < raw.length) {
        if (raw.startsWith("$$", index)) {
            val close = raw.indexOf("$$", index + 2)
            if (close > index + 2) {
                flush()
                parts += InlinePiece.Math(raw.substring(index + 2, close))
                index = close + 2
                continue
            }
        }
        if (raw[index] == '$' && !raw.startsWith("$$", index)) {
            val close = raw.indexOf('$', index + 1)
            if (close > index + 1 && !raw.substring(index + 1, close).contains('\n')) {
                flush()
                parts += InlinePiece.Math(raw.substring(index + 1, close))
                index = close + 1
                continue
            }
        }
        buf.append(raw[index])
        index++
    }
    flush()
    return parts
}
