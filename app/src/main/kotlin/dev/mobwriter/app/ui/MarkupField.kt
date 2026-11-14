package dev.mobwriter.app.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import dev.mobwriter.app.core.markdown.InlineKind
import dev.mobwriter.app.core.markdown.InlineMarkup

class MarkdownVisualTransformation(
    internal val accent: Color,
    internal val secondary: Color,
    internal val quiet: Color,
    internal val ground: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val presented = InlineMarkup.present(text.text)
        val annotated = buildAnnotatedString {
            append(presented.text)
            presented.spans.forEach { span ->
                addStyle(styleFor(span.kind), span.start, span.end)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                presented.origToDisp[offset.coerceIn(presented.origToDisp.indices)]

            override fun transformedToOriginal(offset: Int): Int =
                presented.dispToOrig[offset.coerceIn(presented.dispToOrig.indices)]
        }
        return TransformedText(annotated, mapping)
    }
}

class SourceMarkupTransformation(
    private val quiet: Color,
    private val styles: MarkdownVisualTransformation,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val presented = InlineMarkup.present(text.text)
        val annotated = buildAnnotatedString {
            append(text.text)
            presented.spans.forEach { span ->
                val start = presented.dispToOrig[span.start.coerceIn(presented.dispToOrig.indices)]
                val end = presented.dispToOrig[span.end.coerceIn(presented.dispToOrig.indices)]
                if (end > start) addStyle(styles.styleFor(span.kind), start, end)
            }
            presented.markers.forEach { marker ->
                if (marker.endExclusive > marker.start) {
                    addStyle(SpanStyle(color = quiet), marker.start, marker.endExclusive.coerceAtMost(text.length))
                }
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}

private fun MarkdownVisualTransformation.styleFor(kind: InlineKind): SpanStyle = when (kind) {
    InlineKind.Bold -> SpanStyle(fontWeight = FontWeight.SemiBold)
    InlineKind.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
    InlineKind.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    InlineKind.Code -> SpanStyle(fontFamily = FontFamily.Monospace, background = ground)
    InlineKind.Link -> SpanStyle(color = accent, textDecoration = TextDecoration.Underline)
    InlineKind.Math -> SpanStyle(
        fontFamily = FontFamily.Monospace,
        color = secondary,
        background = ground,
    )
}

@Composable
fun MarkupField(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    revision: Int,
    active: Boolean,
    onValue: (TextFieldValue) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    var field by remember { mutableStateOf(TextFieldValue(text)) }
    LaunchedEffect(revision) {
        if (active) {
            val start = selectionStart.coerceIn(0, text.length)
            val end = selectionEnd.coerceIn(start, text.length)
            field = TextFieldValue(text, androidx.compose.ui.text.TextRange(start, end))
        }
    }
    if (!active && field.text != text) field = TextFieldValue(text)
    val accent = Cinnabar
    val secondary = Moss
    val quiet = Muted
    val ground = FormulaGround
    BasicTextField(
        value = field,
        onValueChange = {
            field = it
            onValue(it)
        },
        textStyle = textStyle,
        cursorBrush = SolidColor(Cinnabar),
        visualTransformation = remember(accent, secondary) {
            MarkdownVisualTransformation(accent, secondary, quiet, ground)
        },
        modifier = modifier,
    )
}
