package dev.mobwriter.app.core.model

/**
 * 应用级外观。字号跟这台手机走，不进账号，也不写入仓库。
 *
 * 倍数 1 是「标准」，也是滑块的正中间，对应界面上合适的字号。
 * 滑块两端是标准的 70% 和 130%，可以比标准更小，也可以更大。
 */
data class FontScale(val percent: Int) {
    val scale: Float get() = percent / 100f

    /** 正中间显示「标准」，其余显示相对标准的百分比。 */
    val label: String get() = if (percent == STANDARD) "标准" else "$percent%"

    companion object {
        const val MIN = 70
        const val MAX = 130
        const val STANDARD = 100

        fun of(percent: Int): FontScale = FontScale(percent.coerceIn(MIN, MAX))

        /** 读存储的百分比。空的、读不懂的都当没调过，用标准。 */
        fun fromStored(value: String?): FontScale {
            val raw = value?.trim()?.toIntOrNull() ?: return FontScale(STANDARD)
            return of(raw)
        }
    }
}

/**
 * 一套配色。`key` 是预设的名字；自定义时是 `custom#RRGGBB`，带着自己选的主色。
 * 读不懂的值回到「墨哺」，也就是现在这套。
 */
data class PaletteChoice(val key: String) {
    val preset: Palette? get() = Palette.entries.firstOrNull { it.key == key }

    val customAccent: Int? get() = CUSTOM.matchEntire(key)?.groupValues?.get(1)?.toIntOrNull(16)

    val label: String get() = preset?.label ?: if (customAccent != null) "自定义" else Palette.Cinnabar.label

    fun summary(): String = preset?.label ?: customAccent?.let { "#%06X".format(it) } ?: Palette.Cinnabar.label

    companion object {
        private val CUSTOM = Regex("custom#([0-9A-Fa-f]{6})")

        fun preset(palette: Palette): PaletteChoice = PaletteChoice(palette.key)

        fun custom(rgb: Int): PaletteChoice = PaletteChoice("custom#%06X".format(rgb and 0xFFFFFF))

        fun fromStored(value: String?): PaletteChoice {
            if (value.isNullOrBlank()) return preset(Palette.Cinnabar)
            val choice = PaletteChoice(value.trim())
            return if (choice.preset != null || choice.customAccent != null) choice else preset(Palette.Cinnabar)
        }
    }
}

/** 预设配色。`accent` 是浅色下的主色，背景和文字沿用现在这套纸色。 */
enum class Palette(val key: String, val label: String, val accent: Int) {
    Cinnabar("cinnabar", "墨哺", 0x8E3B32),
    Claude("claude", "Claude", 0xC96442),
    Indigo("indigo", "靛蓝", 0x3F51B5),
    Moss("moss", "青绿", 0x215C45),
    Violet("violet", "紫色", 0x6B4C9A),
    Amber("amber", "琥珀", 0xB5751A),
    Slate("slate", "石板", 0x4A5562),
}

/**
 * 背景。`none` 是现在的纸色，一张图用 `image:<名字>` 记在应用自己的目录里，不进仓库。
 * 名字只允许字母、数字、点和横线，读不懂的值回到纸色。
 */
data class BackgroundChoice(val key: String) {
    val isPlain: Boolean get() = key == PLAIN

    val imageName: String? get() = key.removePrefix(IMAGE_PREFIX).takeIf { key.startsWith(IMAGE_PREFIX) && IMAGE_NAME.matches(it) }

    val label: String get() = if (isPlain) "默认" else if (imageName != null) "自定义图片" else "默认"

    companion object {
        const val PLAIN = "none"
        private const val IMAGE_PREFIX = "image:"
        private val IMAGE_NAME = Regex("[A-Za-z0-9._-]{1,80}")

        fun plain(): BackgroundChoice = BackgroundChoice(PLAIN)

        fun image(name: String): BackgroundChoice? =
            name.takeIf { IMAGE_NAME.matches(it) }?.let { BackgroundChoice(IMAGE_PREFIX + it) }

        fun fromStored(value: String?): BackgroundChoice {
            if (value.isNullOrBlank() || value == PLAIN) return plain()
            val choice = BackgroundChoice(value.trim())
            return if (choice.imageName != null) choice else plain()
        }
    }
}

data class Appearance(
    val ui: FontScale = FontScale(FontScale.STANDARD),
    val article: FontScale = FontScale(FontScale.STANDARD),
    val palette: PaletteChoice = PaletteChoice.preset(Palette.Cinnabar),
    val background: BackgroundChoice = BackgroundChoice.plain(),
) {
    fun summary(): String = "界面 ${ui.label} · 文章 ${article.label} · ${palette.summary()}"
}

/**
 * 文章字号的基准。标准档用这些磅值，其它档按倍数缩放，标题和标题级别相对正文的比例不变。
 * 基准本身就是界面上合适的字号，比旧版五档里的「标准」小一档。
 */
object ArticleType {
    const val BODY_SP = 14.45f
    const val BODY_LINE_SP = 23.8f
    const val TITLE_SP = 23.8f
    const val TITLE_LINE_SP = 30.6f

    /** 渲染公式相对正文字号的倍数，和公式引擎里的 1.21 一致。 */
    const val FORMULA_EM = 1.21f

    fun headingSp(level: Int): Float = when (level) {
        1 -> 23.8f
        2 -> 20.4f
        3 -> 17.85f
        4 -> 15.3f
        5 -> 13.6f
        else -> 12.75f
    }

    fun scaled(baseSp: Float, scale: FontScale): Float = baseSp * scale.scale

    fun formulaSp(scale: FontScale): Float = scaled(BODY_SP, scale) * FORMULA_EM
}
