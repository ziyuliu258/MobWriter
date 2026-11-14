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

data class Appearance(
    val ui: FontScale = FontScale(FontScale.STANDARD),
    val article: FontScale = FontScale(FontScale.STANDARD),
) {
    fun summary(): String = "界面 ${ui.label} · 文章 ${article.label}"
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
