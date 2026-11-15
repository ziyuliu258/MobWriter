package dev.mobwriter.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Miuix 原装图标按钮，长按显示说明。tint 不填时沿用当前墨色。
 * 放在顶栏里时（[ProvideBarOutline]），按钮轮廓随正文压到顶栏下面才淡入。
 */
@Composable
internal fun AppIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    icon: @Composable () -> Unit,
) {
    val color = if (tint == Color.Unspecified) LocalContentColor.current else tint
    val outline = LocalBarOutline.current
    val fill = MiuixTheme.colorScheme.surfaceContainer
    val edge = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    TooltipBox(text = label) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = if (outline == null) modifier else modifier.drawBehind {
                // 只在绘制阶段读进度：淡入过程中顶栏不逐帧重组
                val a = outline().coerceIn(0f, 1f)
                if (a > 0.01f) {
                    val r = size.minDimension / 2f
                    drawCircle(fill.copy(alpha = fill.alpha * a), radius = r)
                    drawCircle(edge.copy(alpha = edge.alpha * a), radius = r, style = Stroke(0.5.dp.toPx()))
                }
            },
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (enabled) color else color.copy(alpha = 0.38f),
            ) { icon() }
        }
    }
}

/** 顶栏按钮轮廓的显现程度（0 只有图标，1 完整轮廓）；不在顶栏里时为 null。 */
private val LocalBarOutline = compositionLocalOf<(() -> Float)?> { null }

/** 顶栏的按钮槽位包一层：里面的 [AppIconButton] 按 [outline] 画轮廓。 */
@Composable
internal fun ProvideBarOutline(outline: () -> Float, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBarOutline provides outline, content = content)
}

/**
 * 停在顶部时顶栏按钮只剩图标；正文压到顶栏下面（列表自己滚过 24dp，顶栏折叠不算）后按钮轮廓淡入。
 * 到点触发的渐变，不跟手逐帧变。
 */
@Composable
internal fun rememberBarOutline(list: LazyListState): () -> Float {
    val threshold = with(LocalDensity.current) { ScrolledUnder.toPx() }
    val under by remember(list, threshold) {
        derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > threshold }
    }
    val alpha = animateFloatAsState(
        targetValue = if (under) 1f else 0f,
        animationSpec = tween(OutlineFadeMillis, easing = OutlineEasing),
        label = "barOutline",
    )
    return remember(alpha) { { alpha.value } }
}

private val ScrolledUnder = 24.dp
private const val OutlineFadeMillis = 600
private val OutlineEasing = CubicBezierEasing(0.42f, 0f, 0.36f, 1f)
