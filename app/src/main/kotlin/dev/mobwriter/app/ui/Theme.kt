package dev.mobwriter.app.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.mobwriter.app.R
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.mobwriter.app.core.model.Appearance
import dev.mobwriter.app.core.model.ArticleType
import dev.mobwriter.app.core.model.FontFace
import dev.mobwriter.app.core.model.FontScale
import dev.mobwriter.app.core.model.PaletteChoice
import top.yukonga.miuix.kmp.theme.Colors as MiuixColors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles as MiuixTextStyles
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDark
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLight

private val DefaultInk = Color(0xFF1F1E1B)
private val DefaultCinnabar = Color(0xFF8E3B32)

internal val LocalAppearance = compositionLocalOf { Appearance() }

/** 页面底色。自定义背景图铺在它上面，换配色时跟着主色变。 */
val Paper: Color
    @Composable @ReadOnlyComposable get() = MiuixTheme.colorScheme.background

/** 强调色：按钮、选中项、链接、光标。 */
val Cinnabar: Color
    @Composable @ReadOnlyComposable get() = MiuixTheme.colorScheme.primary

/** 次强调色：公式、标记。 */
val Moss: Color
    @Composable @ReadOnlyComposable get() = LocalMoss.current

private val LocalMoss = staticCompositionLocalOf { Color(0xFF215C45) }

val Ink: Color
    @Composable @ReadOnlyComposable get() = MiuixTheme.colorScheme.onBackground

/** 次要文字和图标：淡墨。 */
val Muted: Color
    @Composable @ReadOnlyComposable get() = MiuixTheme.colorScheme.onSurfaceVariantSummary

/** 公式底色，跟着主色走。 */
val FormulaGround: Color
    @Composable @ReadOnlyComposable get() = MiuixTheme.colorScheme.secondaryContainer

/**
 * 整套配色都跟着主色走：页面底色、卡片、按钮、选中项、分隔线。
 * 深色下把主色提亮，压在深底上仍看得清；自定义的主色同样处理。
 */
internal fun colorsFor(palette: PaletteChoice, dark: Boolean): InkScheme {
    val accent = opaque(palette.preset?.accent ?: palette.customAccent ?: (DefaultCinnabar.toArgb() and 0xFFFFFF))
    val primary = if (dark) accent.lightened() else accent
    val onPrimary = if (primary.luminance() > 0.5f) DefaultInk else Color(0xFFFFF8F2)
    // 纸面取 Claude 那种中性暖白，只沾一点主色；卡片是白笺，凹槽是淡米色。
    return InkScheme(
        primary = primary,
        onPrimary = onPrimary,
        secondary = if (dark) accent.lightened() else accent.tinted(Color(0xFF215C45), 0.35f),
        paper = if (dark) accent.tinted(Color(0xFF262624), 0.97f) else accent.tinted(Color(0xFFF8F7F3), 0.975f),
        card = if (dark) accent.tinted(Color(0xFF30302E), 0.97f) else Color(0xFFFFFFFF),
        well = if (dark) accent.tinted(Color(0xFF3B3A37), 0.96f) else accent.tinted(Color(0xFFEFEDE7), 0.95f),
        ink = if (dark) Color(0xFFF5F4EE) else DefaultInk,
    )
}

/** 纸、笺、墨和主色。整套界面的颜色都从这几样推出来。 */
@Immutable
internal data class InkScheme(
    val primary: Color,
    val onPrimary: Color,
    /** 公式和标记的次强调色。 */
    val secondary: Color,
    val paper: Color,
    val card: Color,
    val well: Color,
    val ink: Color,
)

/**
 * 同一套纸墨配色喂给 Miuix。页面是纸，分组卡片是略亮的笺，分隔线是一道淡墨。
 * 次要文字用墨色降透明度，不用 Miuix 默认的蓝灰。
 */
internal fun miuixColorsFor(ink: InkScheme, dark: Boolean): MiuixColors {
    val material = ink
    val paper = ink.paper
    val card = ink.card
    val well = ink.well
    val accent = ink.primary
    val text = ink.ink
    val faintAccent = accent.over(paper, if (dark) 0.22f else 0.12f)
    val hairline = text.copy(alpha = if (dark) 0.16f else 0.10f)
    val disabledAccent = accent.over(paper, 0.38f)
    return if (dark) {
        miuixDark(
            primary = accent,
            onPrimary = material.onPrimary,
            primaryVariant = accent,
            onPrimaryVariant = faintAccent,
            disabledPrimary = disabledAccent,
            disabledPrimaryButton = disabledAccent,
            disabledPrimarySlider = disabledAccent,
            primaryContainer = accent,
            onPrimaryContainer = material.onPrimary,
            secondary = well,
            secondaryVariant = well,
            onSecondaryVariant = text,
            secondaryContainer = well,
            onSecondaryContainer = text.copy(alpha = 0.6f),
            secondaryContainerVariant = well,
            tertiaryContainer = faintAccent,
            onTertiaryContainer = accent,
            tertiaryContainerVariant = faintAccent,
            background = paper,
            onBackground = text,
            onBackgroundVariant = text.copy(alpha = 0.55f),
            surface = paper,
            onSurface = text,
            surfaceVariant = card,
            onSurfaceSecondary = text.copy(alpha = 0.8f),
            onSurfaceVariantSummary = text.copy(alpha = 0.6f),
            onSurfaceVariantActions = text.copy(alpha = 0.4f),
            surfaceContainer = card,
            onSurfaceContainer = text,
            onSurfaceContainerVariant = text.copy(alpha = 0.55f),
            surfaceContainerHigh = well,
            onSurfaceContainerHigh = text.copy(alpha = 0.55f),
            surfaceContainerHighest = well,
            onSurfaceContainerHighest = text,
            outline = hairline,
            dividerLine = hairline,
            sliderKeyPointForeground = accent,
            sliderBackground = text.copy(alpha = 0.10f),
        )
    } else {
        miuixLight(
            primary = accent,
            onPrimary = material.onPrimary,
            primaryVariant = accent,
            onPrimaryVariant = faintAccent,
            disabledPrimary = disabledAccent,
            disabledPrimaryButton = disabledAccent,
            disabledPrimarySlider = disabledAccent,
            primaryContainer = accent,
            onPrimaryContainer = material.onPrimary,
            secondary = well,
            secondaryVariant = well,
            onSecondaryVariant = text,
            secondaryContainer = well,
            onSecondaryContainer = text.copy(alpha = 0.6f),
            secondaryContainerVariant = well,
            tertiaryContainer = faintAccent,
            onTertiaryContainer = accent,
            tertiaryContainerVariant = faintAccent,
            background = paper,
            onBackground = text,
            onBackgroundVariant = text.copy(alpha = 0.55f),
            surface = paper,
            onSurface = text,
            surfaceVariant = card,
            onSurfaceSecondary = text.copy(alpha = 0.8f),
            onSurfaceVariantSummary = text.copy(alpha = 0.6f),
            onSurfaceVariantActions = text.copy(alpha = 0.4f),
            surfaceContainer = card,
            onSurfaceContainer = text,
            onSurfaceContainerVariant = text.copy(alpha = 0.55f),
            surfaceContainerHigh = well,
            onSurfaceContainerHigh = text.copy(alpha = 0.55f),
            surfaceContainerHighest = well,
            onSurfaceContainerHighest = text,
            outline = hairline,
            dividerLine = hairline,
            sliderKeyPointForeground = accent,
            sliderBackground = text.copy(alpha = 0.08f),
        )
    }
}

/**
 * 拉丁用 Source Serif 4，中文用思源宋体，都是打包进去的常规与半粗。
 * 半粗只给真正的强调；标题保持常规，避免再合成一档假粗体。
 */
internal val BookSerif = FontFamily(
    Font(R.font.book_serif_regular, FontWeight.Normal),
    Font(R.font.book_serif_semibold, FontWeight.Medium),
    Font(R.font.book_serif_semibold, FontWeight.SemiBold),
    Font(R.font.book_serif_semibold, FontWeight.Bold),
)

/**
 * Miuix 的字号整体收小一档。大标题和成段文字用衬线，
 * 列表标签、按钮、说明保持无衬线：内容有书卷气，界面利落。
 */
internal fun miuixTextStylesFor(scale: FontScale, uiFont: FontFace = FontFace.Sans): MiuixTextStyles {
    val base = defaultTextStyles()
    val factor = 0.9f * scale.scale
    fun TextStyle.plain() = scaledBy(factor).let {
        if (uiFont == FontFace.Serif) it.copy(fontFamily = BookSerif) else it
    }
    fun TextStyle.serif() = scaledBy(factor).copy(
        fontFamily = BookSerif,
        fontWeight = FontWeight.Normal,
    )
    return defaultTextStyles(
        main = base.main.plain(),
        paragraph = base.paragraph.serif(),
        body1 = base.body1.plain(),
        body2 = base.body2.plain(),
        button = base.button.plain(),
        footnote1 = base.footnote1.plain(),
        footnote2 = base.footnote2.plain(),
        headline1 = base.headline1.plain(),
        headline2 = base.headline2.plain(),
        subtitle = base.subtitle.plain(),
        title1 = base.title1.serif(),
        title2 = base.title2.serif(),
        title3 = base.title3.serif(),
        title4 = base.title4.serif(),
    )
}

/** 列表里的文章标题：跟文章正文同一副字，保持常规字重。 */
internal val ArticleListTitle: TextStyle
    @Composable @ReadOnlyComposable get() = MiuixTheme.textStyles.title4.copy(
        fontFamily = LocalAppearance.current.articleFont.family,
        fontSize = MiuixTheme.textStyles.headline1.fontSize,
        lineHeight = MiuixTheme.textStyles.headline1.fontSize * 1.4f,
    )

internal val FontFace.family: FontFamily
    get() = if (this == FontFace.Serif) BookSerif else FontFamily.SansSerif

/** 把颜色按比例叠到底色上，得到不透明的淡色。 */
private fun Color.over(base: Color, amount: Float): Color = Color(
    red = base.red + (red - base.red) * amount,
    green = base.green + (green - base.green) * amount,
    blue = base.blue + (blue - base.blue) * amount,
)

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

internal fun articleBodyStyle(scale: FontScale, face: FontFace = FontFace.Serif): TextStyle = TextStyle(
    fontFamily = face.family,
    fontSize = ArticleType.scaled(ArticleType.BODY_SP, scale).sp,
    lineHeight = ArticleType.scaled(ArticleType.BODY_LINE_SP, scale).sp,
)

internal fun articleTitleStyle(scale: FontScale, face: FontFace = FontFace.Serif): TextStyle = TextStyle(
    fontFamily = face.family,
    fontWeight = FontWeight.Normal,
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
    val scheme = colorsFor(appearance.palette, dark)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
            val controller = WindowCompat.getInsetsController(window, view)
            val lightBars = scheme.paper.luminance() > 0.5f
            controller.isAppearanceLightStatusBars = lightBars
            controller.isAppearanceLightNavigationBars = lightBars
        }
    }
    MiuixTheme(
        colors = miuixColorsFor(scheme, dark),
        textStyles = miuixTextStylesFor(appearance.ui, appearance.uiFont),
    ) {
        CompositionLocalProvider(
            LocalAppearance provides appearance,
            LocalMoss provides scheme.secondary,
            top.yukonga.miuix.kmp.theme.LocalContentColor provides scheme.ink,
            content = content,
        )
    }
}

private fun TextStyle.scaledBy(factor: Float): TextStyle = copy(
    fontSize = fontSize.scaledBy(factor),
    lineHeight = lineHeight.scaledBy(factor),
    letterSpacing = letterSpacing.scaledBy(factor),
)

private fun TextUnit.scaledBy(factor: Float): TextUnit =
    if (this == TextUnit.Unspecified) this else this * factor
