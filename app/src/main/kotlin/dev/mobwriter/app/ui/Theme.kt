package dev.mobwriter.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.mobwriter.app.core.model.Appearance
import dev.mobwriter.app.core.model.ArticleType
import dev.mobwriter.app.core.model.FontScale
import dev.mobwriter.app.core.model.PaletteChoice

private val DefaultInk = Color(0xFF1E1A16)
private val DefaultCinnabar = Color(0xFF8E3B32)

internal val LocalAppearance = compositionLocalOf { Appearance() }

/** 页面底色。自定义背景图铺在它上面，换配色时跟着主色变。 */
val Paper: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background

/** 强调色：按钮、选中项、链接、光标。 */
val Cinnabar: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

/** 次强调色：公式、标记。 */
val Moss: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary

val Ink: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onBackground

/** 次要文字和图标。 */
val Muted: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline

/** 公式底色，跟着主色走。 */
val FormulaGround: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant

/**
 * 整套配色都跟着主色走：页面底色、卡片、按钮、选中项、分隔线。
 * 深色下把主色提亮，压在深底上仍看得清；自定义的主色同样处理。
 */
internal fun colorsFor(palette: PaletteChoice, dark: Boolean): ColorScheme {
    val accent = opaque(palette.preset?.accent ?: palette.customAccent ?: (DefaultCinnabar.toArgb() and 0xFFFFFF))
    val primary = if (dark) accent.lightened() else accent
    val onPrimary = if (primary.luminance() > 0.5f) DefaultInk else Color(0xFFFFF8F2)
    val paper = if (dark) Color(0xFF16130F) else accent.tinted(Color(0xFFF7F3EA), 0.90f)
    val surface = if (dark) Color(0xFF221E19) else accent.tinted(Color(0xFFFFFBF5), 0.93f)
    val variant = if (dark) Color(0xFF3A332B) else accent.tinted(Color(0xFFE7E0D4), 0.84f)
    val outline = if (dark) accent.lightened() else accent.tinted(Color(0xFFD3C8B8), 0.72f)
    val secondary = if (dark) accent.lightened() else accent.tinted(Color(0xFF215C45), 0.35f)
    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            background = paper,
            surface = surface,
            surfaceVariant = variant,
            onBackground = Color(0xFFF3EBDD),
            onSurface = Color(0xFFF3EBDD),
            outline = outline,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            background = paper,
            surface = surface,
            surfaceVariant = variant,
            onBackground = DefaultInk,
            onSurface = DefaultInk,
            outline = outline,
        )
    }
}

/** 底色带着主色的一点颜色，换配色时整页都变，不只有按钮变。 */
private fun Color.tinted(base: Color, keep: Float): Color = Color(
    red = base.red * keep + red * (1f - keep),
    green = base.green * keep + green * (1f - keep),
    blue = base.blue * keep + blue * (1f - keep),
)

/** 存的是 RRGGBB，补上不透明，否则主色和色块会画成全透明。 */
internal fun opaque(rgb: Int): Color = Color(0xFF000000.toInt() or (rgb and 0xFFFFFF))

/** 往纸色上靠，深色模式里主色不至于变成一块黑。 */
private fun Color.lightened(): Color = Color(
    red = red + (1f - red) * 0.45f,
    green = green + (1f - green) * 0.45f,
    blue = blue + (1f - blue) * 0.45f,
)

/** 整套排版都从 Material 默认缩到合适的大小，标准档就是这个大小，不再只缩三个样式。 */
private val BaseTypography = Typography().scaledBy(0.85f).let { scaled ->
    scaled.copy(
        headlineMedium = scaled.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleLarge = scaled.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        bodyLarge = scaled.bodyLarge.copy(fontFamily = FontFamily.Serif),
    )
}

internal fun typographyFor(scale: FontScale): Typography = BaseTypography.scaledBy(scale.scale)

internal fun articleBodyStyle(scale: FontScale): TextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = ArticleType.scaled(ArticleType.BODY_SP, scale).sp,
    lineHeight = ArticleType.scaled(ArticleType.BODY_LINE_SP, scale).sp,
)

internal fun articleTitleStyle(scale: FontScale): TextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.SemiBold,
    fontSize = ArticleType.scaled(ArticleType.TITLE_SP, scale).sp,
    lineHeight = ArticleType.scaled(ArticleType.TITLE_LINE_SP, scale).sp,
)

internal fun articleHeadingSize(level: Int, scale: FontScale): TextUnit =
    ArticleType.scaled(ArticleType.headingSp(level), scale).sp

@Composable
fun MobWriterTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    CompositionLocalProvider(LocalAppearance provides appearance) {
        MaterialTheme(
            colorScheme = colorsFor(appearance.palette, dark),
            typography = typographyFor(appearance.ui),
            content = content,
        )
    }
}

private fun Typography.scaledBy(factor: Float): Typography {
    if (factor == 1f) return this
    return copy(
        displayLarge = displayLarge.scaledBy(factor),
        displayMedium = displayMedium.scaledBy(factor),
        displaySmall = displaySmall.scaledBy(factor),
        headlineLarge = headlineLarge.scaledBy(factor),
        headlineMedium = headlineMedium.scaledBy(factor),
        headlineSmall = headlineSmall.scaledBy(factor),
        titleLarge = titleLarge.scaledBy(factor),
        titleMedium = titleMedium.scaledBy(factor),
        titleSmall = titleSmall.scaledBy(factor),
        bodyLarge = bodyLarge.scaledBy(factor),
        bodyMedium = bodyMedium.scaledBy(factor),
        bodySmall = bodySmall.scaledBy(factor),
        labelLarge = labelLarge.scaledBy(factor),
        labelMedium = labelMedium.scaledBy(factor),
        labelSmall = labelSmall.scaledBy(factor),
    )
}

private fun TextStyle.scaledBy(factor: Float): TextStyle = copy(
    fontSize = fontSize.scaledBy(factor),
    lineHeight = lineHeight.scaledBy(factor),
    letterSpacing = letterSpacing.scaledBy(factor),
)

private fun TextUnit.scaledBy(factor: Float): TextUnit =
    if (this == TextUnit.Unspecified) this else this * factor
