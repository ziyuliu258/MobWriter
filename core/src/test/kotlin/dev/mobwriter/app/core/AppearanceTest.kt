package dev.mobwriter.app.core

import dev.mobwriter.app.core.model.Appearance
import dev.mobwriter.app.core.model.ArticleType
import dev.mobwriter.app.core.model.FontStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppearanceTest {
    @Test
    fun stepsRunFromSmallerToExtraLargeAndStandardIsTheCurrentSize() {
        assertEquals(listOf("较小", "小", "标准", "大", "特大"), FontStep.entries.map { it.label })
        assertTrue(FontStep.entries.zipWithNext().all { (left, right) -> left.scale < right.scale })
        assertEquals(1f, FontStep.Standard.scale)
        assertEquals(17f, ArticleType.scaled(ArticleType.BODY_SP, FontStep.Standard))
        assertEquals(28f, ArticleType.scaled(ArticleType.TITLE_SP, FontStep.Standard))
        assertEquals(24f, ArticleType.headingSp(2))
    }

    @Test
    fun unknownStoredStepFallsBackToStandard() {
        assertEquals(FontStep.Standard, FontStep.fromStored(null))
        assertEquals(FontStep.Standard, FontStep.fromStored(""))
        assertEquals(FontStep.Standard, FontStep.fromStored("huge"))
        assertEquals(FontStep.Large, FontStep.fromStored(FontStep.Large.name))
    }

    @Test
    fun theTwoSizesStayIndependent() {
        val appearance = Appearance(ui = FontStep.Small, article = FontStep.ExtraLarge)
        assertEquals(FontStep.Small, appearance.ui)
        assertEquals(FontStep.ExtraLarge, appearance.article)
        assertEquals("界面 小 · 文章 特大", appearance.summary())
        assertEquals(FontStep.Standard, appearance.copy(article = FontStep.Standard).article)
        assertEquals(FontStep.Small, appearance.copy(article = FontStep.Standard).ui)
    }

    @Test
    fun headingsAndFormulasKeepTheirRatioToTheBodyAtEveryStep() {
        for (step in FontStep.entries) {
            val body = ArticleType.scaled(ArticleType.BODY_SP, step)
            assertEquals(ArticleType.TITLE_SP / ArticleType.BODY_SP, ArticleType.scaled(ArticleType.TITLE_SP, step) / body, 0.0001f)
            for (level in 1..6) {
                val ratio = ArticleType.headingSp(level) / ArticleType.BODY_SP
                assertEquals(ratio, ArticleType.scaled(ArticleType.headingSp(level), step) / body, 0.0001f)
            }
            assertEquals(ArticleType.FORMULA_EM, ArticleType.formulaSp(step) / body, 0.0001f)
            assertEquals(1.21f, ArticleType.formulaSp(step) / body, 0.0001f)
        }
    }
}
