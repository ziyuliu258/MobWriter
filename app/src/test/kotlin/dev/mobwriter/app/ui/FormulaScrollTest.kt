package dev.mobwriter.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import dev.mobwriter.app.core.blog.BlogService
import dev.mobwriter.app.core.github.GitHubClient
import dev.mobwriter.app.core.github.HttpRequest
import dev.mobwriter.app.core.github.HttpResponse
import dev.mobwriter.app.core.github.HttpTransport
import dev.mobwriter.app.core.model.Block
import dev.mobwriter.app.core.model.BlogSettings
import dev.mobwriter.app.core.model.PostDocument
import dev.mobwriter.app.data.DraftStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h640dp-xxhdpi")
class FormulaScrollTest {
    @get:Rule
    val rule = createComposeRule()

    private val style = TextStyle(fontSize = 17.sp)
    private val shortLatex = "a+b"
    private val longLatex = (1..48).joinToString("+") { "a" }

    @Test
    fun safetyCapKeepsOrdinaryFormulasAndShrinksOnlyExtremes() {
        assertEquals(1f, formulaBitmapScale(1600, 120), 0.0001f)
        assertEquals(1f, formulaBitmapScale(5000, 180), 0.0001f)
        val wide = formulaBitmapScale(20_000, 160)
        assertTrue(wide < 1f)
        assertTrue(20_000 * wide <= FormulaBitmapMaxSide + 1f)
        assertTrue(20_000 * wide > 1600f)
        val dense = formulaBitmapScale(10_000, 2_000)
        assertTrue(dense < 1f)
        assertTrue(10_000 * dense <= FormulaBitmapMaxSide + 1f)
        assertTrue(2_000 * dense <= FormulaBitmapMaxSide + 1f)
        val pixels = (10_000 * dense) * (2_000 * dense)
        assertTrue(pixels <= FormulaBitmapMaxPixels * 1.05)
    }

    @Test
    fun longBlockStaysReadableAndScrollsFromTheLeft() {
        rule.setContent {
            MobWriterTheme {
                Column(Modifier.fillMaxSize()) {
                    FormulaImage(shortLatex, true, rememberFormulaBoard(), style, Modifier.fillMaxWidth().testTag("short"))
                    FormulaImage(longLatex, true, rememberFormulaBoard(), style, Modifier.fillMaxWidth().testTag("long"))
                }
            }
        }
        waitForFormula(shortLatex)
        waitForFormula(longLatex)

        val shortBox = rule.onNodeWithTag("short").fetchSemanticsNode().boundsInRoot
        val shortImage = image(shortLatex)
        assertTrue(shortImage.size.width < shortBox.width)
        assertTrue(shortImage.size.height > 8)
        assertTrue(abs(shortImage.positionInRoot.x + shortImage.size.width / 2f - shortBox.center.x) < 3f)
        assertTrue(scrollNodes().none { it.boundsInRoot.overlaps(shortBox) && scrollMax(it) > 1f })

        val longBox = rule.onNodeWithTag("long").fetchSemanticsNode().boundsInRoot
        val longImage = image(longLatex)
        val longScroll = scrollNodes().first { it.boundsInRoot.overlaps(longBox) && scrollMax(it) > 1f }
        assertTrue(
            "layout=${longImage.size.width} box=${longBox.width}",
            longImage.size.width > longBox.width,
        )
        assertTrue("layout=${longImage.size.width}", longImage.size.width > 1600)
        assertTrue(longImage.size.height > shortImage.size.height * 0.7f)
        assertTrue(abs(longImage.positionInRoot.x - longBox.left) < 3f)
        val before = scrollValue(longScroll)
        rule.onAllNodes(canPan(), useUnmergedTree = true)[0].performTouchInput { swipeLeft() }
        rule.waitForIdle()
        val after = scrollNodes().first { it.boundsInRoot.overlaps(longBox) && scrollMax(it) > 1f }
        assertTrue(scrollValue(after) > before)
    }

    @Test
    fun overflowScrollStopsAtBothEnds() {
        rule.setContent {
            MobWriterTheme {
                FormulaImage(longLatex, true, rememberFormulaBoard(), style, Modifier.fillMaxWidth().testTag("long"))
            }
        }
        waitForFormula(longLatex)
        val node = rule.onNode(hasHorizontalScroll(), useUnmergedTree = true)
        node.performSemanticsAction(SemanticsActions.ScrollBy) { it(1_000_000f, 0f) }
        rule.waitForIdle()
        val atEnd = rule.onNode(hasHorizontalScroll(), useUnmergedTree = true).fetchSemanticsNode()
        val max = atEnd.config[SemanticsProperties.HorizontalScrollAxisRange].maxValue()
        val value = atEnd.config[SemanticsProperties.HorizontalScrollAxisRange].value()
        assertTrue(max > 100f)
        assertEquals(max, value, 1f)
        node.performSemanticsAction(SemanticsActions.ScrollBy) { it(400f, 0f) }
        rule.waitForIdle()
        val stillEnd = rule.onNode(hasHorizontalScroll(), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.HorizontalScrollAxisRange].value()
        assertEquals(max, stillEnd, 1f)
        node.performSemanticsAction(SemanticsActions.ScrollBy) { it(-1_000_000f, 0f) }
        rule.waitForIdle()
        val atStart = rule.onNode(hasHorizontalScroll(), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.HorizontalScrollAxisRange].value()
        assertEquals(0f, atStart, 1f)
        node.performTouchInput { swipeRight() }
        rule.waitForIdle()
        val held = rule.onNode(hasHorizontalScroll(), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.HorizontalScrollAxisRange].value()
        assertEquals(0f, held, 1f)
    }

    @Test
    fun longInlineFormulaTakesItsOwnLineAndDoesNotDragTheParagraph() {
        rule.setContent {
            MobWriterTheme {
                Column(Modifier.fillMaxWidth().testTag("paragraph")) {
                    InlineRich("前文 \$$longLatex\$ 后文", style, rememberFormulaBoard(), {})
                }
            }
        }
        waitForFormula(longLatex)
        val lead = rule.onNodeWithText("前文", substring = true).fetchSemanticsNode().boundsInRoot
        val formula = image(longLatex).boundsInRoot
        val trail = rule.onNodeWithText("后文", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue(formula.top >= lead.bottom - 2f)
        assertTrue(trail.top >= formula.bottom - 2f)
        assertTrue(formula.width > lead.width)
        val leadX = lead.left
        val trailX = trail.left
        val before = scrollValue(scrollNodes().first { scrollMax(it) > 1f })
        rule.onNode(canPan(), useUnmergedTree = true).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        val leadAfter = rule.onNodeWithText("前文", substring = true).fetchSemanticsNode().boundsInRoot
        val trailAfter = rule.onNodeWithText("后文", substring = true).fetchSemanticsNode().boundsInRoot
        assertEquals(leadX, leadAfter.left, 1f)
        assertEquals(trailX, trailAfter.left, 1f)
        assertEquals(lead.top, leadAfter.top, 1f)
        assertTrue(scrollValue(scrollNodes().first { scrollMax(it) > 1f }) > before)
        assertTrue(image(longLatex).size.width > 1600)
    }

    @Test
    fun shortInlineFormulaStaysOnTheLineWithoutAScroller() {
        rule.setContent {
            MobWriterTheme {
                InlineRich("前文 \$$shortLatex\$ 后文", style, rememberFormulaBoard(), {})
            }
        }
        waitForFormula(shortLatex)
        val lead = rule.onNodeWithText("前文", substring = true).fetchSemanticsNode().boundsInRoot
        val formula = image(shortLatex).boundsInRoot
        val trail = rule.onNodeWithText("后文", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue(verticalOverlap(lead, formula) > 0f)
        assertTrue(verticalOverlap(formula, trail) > 0f)
        assertTrue(formula.width < 360f * 3f)
        assertTrue(scrollNodes().isEmpty())
    }

    @Test
    fun quoteListAndHeadingScrollOnlyWhenTheFormulaOverflows() {
        rule.setContent {
            MobWriterTheme {
                Column(Modifier.fillMaxWidth()) {
                    QuoteFrame {
                        InlineRich("引：\$\\pi_0\$ 然后 \$$longLatex\$", style, rememberFormulaBoard(), {})
                    }
                    InlineRich("•  \$$longLatex\$", style, rememberFormulaBoard(), {})
                    InlineRich(
                        "\$$shortLatex\$",
                        style.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                        rememberFormulaBoard(),
                        {},
                    )
                }
            }
        }
        waitForFormula("\\pi_0")
        waitForFormula(longLatex)
        waitForFormula(shortLatex)
        rule.onNodeWithContentDescription("\\pi_0", useUnmergedTree = true).assertIsDisplayed()
        val pi = image("\\pi_0").boundsInRoot
        val longImages = rule.onAllNodesWithContentDescription(longLatex, useUnmergedTree = true).fetchSemanticsNodes()
        val quoteFormula = longImages.first().boundsInRoot
        assertTrue(quoteFormula.top >= pi.bottom - 2f)
        assertTrue(longImages.all { it.size.width > 1600 })
        assertTrue(scrollNodes().count { scrollMax(it) > 1f } >= 2)
        val heading = image(shortLatex)
        assertTrue(heading.size.width < 360 * 3)
        assertTrue(scrollNodes().none { scrollMax(it) > 1f && it.boundsInRoot.overlaps(heading.boundsInRoot) })
    }

    @Test
    fun verticalSwipeOnAnOverflowFormulaStillScrollsTheArticle() {
        val listState = LazyListState()
        rule.setContent {
            MobWriterTheme {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("article")) {
                    item { Text("顶部", modifier = Modifier.testTag("top")) }
                    item {
                        FormulaImage(shortLatex, true, rememberFormulaBoard(), style, Modifier.fillMaxWidth())
                    }
                    item {
                        FormulaImage(longLatex, true, rememberFormulaBoard(), style, Modifier.fillMaxWidth().testTag("long"))
                    }
                    item { Spacer(Modifier.height(900.dp)) }
                    item { Text("页尾") }
                }
            }
        }
        waitForFormula(longLatex)
        val topBefore = rule.onNodeWithTag("top").fetchSemanticsNode().positionInRoot.y
        val scrollBefore = scrollValue(scrollNodes().first { scrollMax(it) > 1f })
        val formula = rule.onNode(canPan(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val article = rule.onNodeWithTag("article").fetchSemanticsNode().boundsInRoot
        val start = Offset(formula.center.x - article.left, formula.center.y - article.top)
        rule.onNodeWithTag("article").performTouchInput {
            swipe(start, Offset(start.x, (start.y - 700f).coerceAtLeast(1f)), durationMillis = 300)
        }
        rule.waitForIdle()
        assertTrue(
            "index=${listState.firstVisibleItemIndex} offset=${listState.firstVisibleItemScrollOffset}",
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20,
        )
        val pans = scrollNodes().filter { scrollMax(it) > 1f }
        if (pans.isNotEmpty()) assertEquals(scrollBefore, scrollValue(pans.first()), 1f)
        val topAfter = rule.onAllNodes(androidx.compose.ui.test.hasTestTag("top"), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.positionInRoot
            ?.y
        assertTrue(topAfter == null || topAfter < topBefore)
    }

    @Test
    fun editorSwipeDoesNotOpenFormulaSourceButTapDoes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drafts = DraftStore(context)
        val key = "n-formula-scroll"
        drafts.save(
            key,
            PostDocument(
                title = "滚动",
                blocks = listOf(
                    Block.Math(longLatex),
                    Block.Paragraph("前文 \$$longLatex\$ 后文"),
                ),
            ),
        )
        val blog = sampleBlog()
        val viewModel = EditorViewModel(blog, drafts, key, remotePath = null)
        rule.setContent {
            MobWriterTheme {
                EditorScreen(viewModel, blog, emptyList(), emptyList(), {}, {})
            }
        }
        waitForFormula(longLatex)
        rule.onNodeWithText("公式").assertDoesNotExist()
        val before = scrollValue(scrollNodes().first { scrollMax(it) > 1f })
        rule.onAllNodes(canPan(), useUnmergedTree = true)[0].performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("公式").assertDoesNotExist()
        val after = scrollValue(scrollNodes().first { scrollMax(it) > 1f })
        assertTrue("scroll $before -> $after", after > before)
        rule.onAllNodes(canPan(), useUnmergedTree = true)[0].performTouchInput { click() }
        rule.waitForIdle()
        rule.onNodeWithText("公式").assertIsDisplayed()
    }

    @Test
    fun readingModePansOverflowFormulaWithoutEnteringSource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drafts = DraftStore(context)
        val key = "n-formula-read"
        drafts.save(key, PostDocument(title = "阅读", blocks = listOf(Block.Math(longLatex), Block.Quote("引：\$\\pi_0\$"))))
        val blog = sampleBlog()
        val viewModel = EditorViewModel(blog, drafts, key, remotePath = null)
        viewModel.mode = WriteMode.Reading
        rule.setContent {
            MobWriterTheme {
                EditorScreen(viewModel, blog, emptyList(), emptyList(), {}, {})
            }
        }
        waitForFormula(longLatex)
        waitForFormula("\\pi_0")
        rule.onNode(canPan(), useUnmergedTree = true).performTouchInput { click() }
        rule.waitForIdle()
        rule.onNodeWithText("公式").assertDoesNotExist()
        rule.onNodeWithContentDescription(longLatex, useUnmergedTree = true).assertIsDisplayed()
        val before = scrollValue(scrollNodes().first { scrollMax(it) > 1f })
        rule.onNode(canPan(), useUnmergedTree = true).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertTrue(scrollValue(scrollNodes().first { scrollMax(it) > 1f }) > before)
        assertTrue(image(longLatex).size.width > 1600)
    }

    private fun sampleBlog(): BlogService = BlogService(
        GitHubClient(object : HttpTransport {
            override fun execute(request: HttpRequest): HttpResponse = HttpResponse(200, "[]".toByteArray())
        }, "token"),
        BlogSettings(owner = "example-owner", repo = "example-app", token = "token"),
    )

    private fun waitForFormula(latex: String) {
        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodesWithContentDescription(latex, useUnmergedTree = true).fetchSemanticsNodes().any {
                it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f
            }
        }
    }

    private fun image(latex: String) =
        rule.onNodeWithContentDescription(latex, useUnmergedTree = true).fetchSemanticsNode()

    private fun scrollNodes() =
        rule.onAllNodes(hasHorizontalScroll(), useUnmergedTree = true).fetchSemanticsNodes()

    private fun hasHorizontalScroll(): SemanticsMatcher =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)

    private fun canPan(): SemanticsMatcher = SemanticsMatcher("formula can pan") { node ->
        node.config.contains(SemanticsProperties.HorizontalScrollAxisRange) && scrollMax(node) > 1f
    }

    private fun scrollValue(node: SemanticsNode): Float =
        node.config[SemanticsProperties.HorizontalScrollAxisRange].value()

    private fun scrollMax(node: SemanticsNode): Float =
        node.config[SemanticsProperties.HorizontalScrollAxisRange].maxValue()

    private fun verticalOverlap(a: androidx.compose.ui.geometry.Rect, b: androidx.compose.ui.geometry.Rect): Float =
        minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
}
