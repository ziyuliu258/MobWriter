package dev.mobwriter.app.core

import dev.mobwriter.app.core.model.Appearance
import dev.mobwriter.app.core.model.ArticleType
import dev.mobwriter.app.core.model.FontScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppearanceTest {
    @Test
    fun theSliderCentersOnStandardAndReachesSmallerAndLarger() {
        val standard = FontScale(FontScale.STANDARD)
        assertEquals(100, standard.percent)
        assertEquals(1f, standard.scale)
        assertEquals("标准", standard.label)
        assertEquals(FontScale.MIN, FontScale.of(FontScale.MIN - 20).percent)
        assertEquals(FontScale.MAX, FontScale.of(FontScale.MAX + 20).percent)
        assertTrue(FontScale.MIN < FontScale.STANDARD)
        assertTrue(FontScale.MAX > FontScale.STANDARD)
        assertEquals("70%", FontScale.of(FontScale.MIN).label)
        assertEquals("130%", FontScale.of(FontScale.MAX).label)
        assertEquals((FontScale.MIN + FontScale.MAX) / 2, FontScale.STANDARD)
    }

    @Test
    fun standardIsTheComfortableSizeAndTheOldMiddleWasTooBig() {
        val body = ArticleType.scaled(ArticleType.BODY_SP, FontScale(FontScale.STANDARD))
        assertEquals(14.45f, body, 0.001f)
        assertEquals(17f * 0.85f, body, 0.001f)
        assertEquals(23.8f, ArticleType.scaled(ArticleType.TITLE_SP, FontScale(FontScale.STANDARD)), 0.001f)
        assertEquals(20.4f, ArticleType.headingSp(2), 0.001f)
    }

    @Test
    fun aBlankValueUsesStandardAndAnOldStepKeepsItsSize() {
        assertEquals(FontScale.STANDARD, FontScale.fromStored(null).percent)
        assertEquals(FontScale.STANDARD, FontScale.fromStored("").percent)
        assertEquals(FontScale.STANDARD, FontScale.fromStored("   ").percent)
        assertEquals(FontScale.STANDARD, FontScale.fromStored("huge").percent)
        assertEquals(FontScale.STANDARD, FontScale.fromStored("Smaller").percent)
        assertEquals(70, FontScale.fromStored("70").percent)
        assertEquals(70, FontScale.fromStored("40").percent)
        assertEquals(130, FontScale.fromStored("200").percent)
        assertEquals(108, FontScale.fromStored("108").percent)
    }

    @Test
    fun theTwoSizesStayIndependent() {
        val appearance = Appearance(ui = FontScale.of(80), article = FontScale.of(120))
        assertEquals(80, appearance.ui.percent)
        assertEquals(120, appearance.article.percent)
        assertEquals("界面 80% · 文章 120%", appearance.summary())
        assertEquals(FontScale.STANDARD, appearance.copy(article = FontScale(FontScale.STANDARD)).article.percent)
        assertEquals(80, appearance.copy(article = FontScale(FontScale.STANDARD)).ui.percent)
        assertEquals("界面 标准 · 文章 标准", Appearance().summary())
    }

    @Test
    fun headingsAndFormulasKeepTheirRatioToTheBodyAtEveryScale() {
        for (percent in FontScale.MIN..FontScale.MAX) {
            val scale = FontScale.of(percent)
            val body = ArticleType.scaled(ArticleType.BODY_SP, scale)
            assertEquals(ArticleType.TITLE_SP / ArticleType.BODY_SP, ArticleType.scaled(ArticleType.TITLE_SP, scale) / body, 0.0001f)
            for (level in 1..6) {
                val ratio = ArticleType.headingSp(level) / ArticleType.BODY_SP
                assertEquals(ratio, ArticleType.scaled(ArticleType.headingSp(level), scale) / body, 0.0001f)
            }
            assertEquals(ArticleType.FORMULA_EM, ArticleType.formulaSp(scale) / body, 0.0001f)
            assertEquals(1.21f, ArticleType.formulaSp(scale) / body, 0.0001f)
        }
    }
}
