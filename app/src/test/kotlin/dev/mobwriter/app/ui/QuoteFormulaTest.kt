package dev.mobwriter.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class QuoteFormulaTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun intrinsicRowRejectsInlineFormula() {
        val failure = runCatching {
            rule.setContent {
                Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(Color.Red))
                    InlineRich("引：\$\\pi_0\$ 将两部分接在一起", TextStyle(fontSize = 17.sp), FormulaBoard(), {})
                }
            }
        }.exceptionOrNull() ?: error("intrinsic measurement should reject a formula quote")
        val text = generateSequence(failure) { it.cause }.joinToString("\n") { it.message.orEmpty() }
        assertTrue(text, text.contains("intrinsic measurements of SubcomposeLayout"))
    }

    @Test
    fun quoteWithInlineFormulaOpensAndScrolls() {
        rule.setContent {
            val board = remember { FormulaBoard() }
            val style = TextStyle(fontSize = 17.sp)
            LazyColumn(Modifier.fillMaxSize().testTag("article")) {
                item {
                    QuoteFrame {
                        InlineRich("引：\$\\pi_0\$ 将两部分接在一起", style, board, {})
                    }
                }
                items(8) { index ->
                    InlineRich("观察 \$\\tau=$index\$ 之后继续读下一段。", style, board, {})
                }
                item {
                    FormulaImage(
                        "\\frac{d\\mathbf{A}}{d\\tau}=\\mathbf{A}-\\epsilon",
                        display = true,
                        board = board,
                        style = style,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithContentDescription("\\pi_0").fetchSemanticsNodes().any { node ->
                node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f
            }
        }
        rule.onNodeWithContentDescription("\\pi_0").assertIsDisplayed()
        rule.onNodeWithTag("article").performTouchInput { swipeUp() }
        rule.waitForIdle()
    }
}
