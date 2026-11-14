package dev.mobwriter.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.mobwriter.app.core.model.Appearance
import dev.mobwriter.app.core.model.ArticleType
import dev.mobwriter.app.core.model.FontStep

val Paper = Color(0xFFF7F3EA)
val Ink = Color(0xFF1E1A16)
val Cinnabar = Color(0xFF8E3B32)
val Moss = Color(0xFF215C45)
val FormulaGround = Color(0xFFE5F0EA)
val Muted = Color(0xFF8A8175)

internal val LocalAppearance = compositionLocalOf { Appearance() }

private val LightColors = lightColorScheme(
    primary = Cinnabar,
    onPrimary = Color(0xFFFFF8F2),
    secondary = Moss,
    background = Paper,
    surface = Color(0xFFFFFBF5),
    surfaceVariant = Color(0xFFE7E0D4),
    onBackground = Ink,
    onSurface = Ink,
    outline = Color(0xFFD3C8B8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE7B2A8),
    onPrimary = Color(0xFF3A1612),
    secondary = Color(0xFF9DCCB4),
    background = Color(0xFF16130F),
    surface = Color(0xFF221E19),
    surfaceVariant = Color(0xFF3A332B),
    onBackground = Color(0xFFF3EBDD),
    onSurface = Color(0xFFF3EBDD),
)

private val BaseTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 28.sp),
)

internal fun typographyFor(step: FontStep): Typography = BaseTypography.scaledBy(step.scale)

internal fun articleBodyStyle(step: FontStep): TextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = ArticleType.scaled(ArticleType.BODY_SP, step).sp,
    lineHeight = ArticleType.scaled(ArticleType.BODY_LINE_SP, step).sp,
)

internal fun articleTitleStyle(step: FontStep): TextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.SemiBold,
    fontSize = ArticleType.scaled(ArticleType.TITLE_SP, step).sp,
    lineHeight = ArticleType.scaled(ArticleType.TITLE_LINE_SP, step).sp,
)

internal fun articleHeadingSize(level: Int, step: FontStep): TextUnit =
    ArticleType.scaled(ArticleType.headingSp(level), step).sp

@Composable
fun MobWriterTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    CompositionLocalProvider(LocalAppearance provides appearance) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
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
