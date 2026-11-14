package dev.mobwriter.app.core.model

/**
 * 应用级外观。字号跟这台手机走，不进账号，也不写入仓库。
 * 「标准」的倍数是 1，对应现在的字号。
 */
enum class FontStep(val label: String, val scale: Float) {
    Smaller("较小", 0.85f),
    Small("小", 0.92f),
    Standard("标准", 1f),
    Large("大", 1.15f),
    ExtraLarge("特大", 1.3f);

    companion object {
        fun fromStored(value: String?): FontStep =
            entries.firstOrNull { it.name == value } ?: Standard
    }
}

data class Appearance(
    val ui: FontStep = FontStep.Standard,
    val article: FontStep = FontStep.Standard,
) {
    fun summary(): String = "界面 ${ui.label} · 文章 ${article.label}"
}

/** 文章字号的基准。标准档用这些磅值，其它档按倍数缩放，标题和标题级别相对正文的比例不变。 */
object ArticleType {
    const val BODY_SP = 17f
    const val BODY_LINE_SP = 28f
    const val TITLE_SP = 28f
    const val TITLE_LINE_SP = 36f

    /** 渲染公式相对正文字号的倍数，和公式引擎里的 1.21 一致。 */
    const val FORMULA_EM = 1.21f

    fun headingSp(level: Int): Float = when (level) {
        1 -> 28f
        2 -> 24f
        3 -> 21f
        4 -> 18f
        5 -> 16f
        else -> 15f
    }

    fun scaled(baseSp: Float, step: FontStep): Float = baseSp * step.scale

    fun formulaSp(step: FontStep): Float = scaled(BODY_SP, step) * FORMULA_EM
}
