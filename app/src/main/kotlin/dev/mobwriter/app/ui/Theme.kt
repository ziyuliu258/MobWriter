package dev.mobwriter.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.mobwriter.app.core.model.Appearance
import dev.mobwriter.app.core.model.ArticleType
import dev.mobwriter.app.core.model.FontScale
import dev.mobwriter.app.core.model.PaletteChoice

val Paper = Color(0xFFF7F3EA)
val Ink = Color(0xFF1E1A16)
val Cinnabar = Color(0xFF8E3B32)
val Moss = Color(0xFF215C45)
val FormulaGround = Color(0xFFE5F0EA)
val Muted = Color(0xFF8A8175)

internal val LocalAppearance = compositionLocalOf { Appearance() }

/**
 * 配色只换主色，纸色和文字保持现在这套。
 * 深色下把主色提亮，压在深底上仍看得清；自定义的主色同样处理。
 */
internal fun colorsFor(palette: PaletteChoice, dark: Boolean): ColorScheme {
    val accent = Color(palette.preset?.accent ?: palette.customAccent ?: Cinnabar.value.toInt())
    val primary = if (dark) accent.lightened() else accent
    val onPrimary = if (primary.luminance() > 0.5f) Ink else Color(0xFFFFF8F2)
    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = Moss,
            background = Color(0xFF16130F),
            surface = Color(0xFF221E19),
            surfaceVariant = Color(0xFF3A332B),
            onBackground = Color(0xFFF3EBDD),
            onSurface = Color(0xFFF3EBDD),
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = Moss,
            background = Paper,
            surface = Color(0xFFFFFBF5),
            surfaceVariant = Color(0xFFE7E0D4),
            onBackground = Ink,
            onSurface = Ink,
            outline = Color(0xFFD3C8B8),
        )
    }
}

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
