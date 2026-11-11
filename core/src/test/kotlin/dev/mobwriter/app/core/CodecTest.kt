package dev.mobwriter.app.core

import dev.mobwriter.app.core.blog.HexoPaths
import dev.mobwriter.app.core.markdown.InlineEdit
import dev.mobwriter.app.core.markdown.InlineKind
import dev.mobwriter.app.core.markdown.InlineMarkup
import dev.mobwriter.app.core.markdown.PostCodec
import dev.mobwriter.app.core.model.Align
import dev.mobwriter.app.core.model.Block
import dev.mobwriter.app.core.model.ImageMode
import java.io.File
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodecTest {
    @Test
    fun formulaBlockKeepsLatexWithoutDollars() {
        val source = """
            ---
            title: 公式
            date: 2026-11-09 12:00:00
            ---

            一行里的 ${'$'}x^2${'$'}。

            ${'$'}${'$'}
            \begin{aligned}
            a&=b\\
            \end{aligned}
            \tag{6}
            ${'$'}${'$'}
        """.trimIndent() + "\n"
        val document = PostCodec.parse(source)
        val math = document.blocks.filterIsInstance<Block.Math>().single()
        assertEquals("\\begin{aligned}\na&=b\\\\\n\\end{aligned}\n\\tag{6}", math.latex)
        assertTrue(!math.latex.contains("$$"))
        val again = PostCodec.parse(PostCodec.render(document))
        assertEquals(math, again.blocks.filterIsInstance<Block.Math>().single())
        val shown = InlineMarkup.present("一行里的 \$x^2\$。")
        assertEquals("一行里的 x^2。", shown.text)
        assertEquals(InlineKind.Math, shown.spans.single().kind)
    }

    @Test
    fun inlineFormattingHidesMarkers() {
        val shown = InlineMarkup.present("这是**粗体**和*斜体*，还有`代码`。")
        assertEquals("这是粗体和斜体，还有代码。", shown.text)
        assertTrue(shown.spans.any { it.kind == InlineKind.Bold })
        val link = InlineMarkup.present("看[切片](https://example.com)吧")
        assertEquals("看切片吧", link.text)
    }

    @Test
    fun deletingPartOfMarkerRemovesWholeMarker() {
        val source = "a<br>b"
        val broken = source.removeRange(4, 5)
        assertEquals("ab", InlineEdit.applyChange(source, broken))
        val bold = "**字**"
        val deleted = bold.removeRange(0, 1)
        assertEquals("字", InlineEdit.applyChange(bold, deleted))
    }

    @Test
    fun toggleWrapsSelection() {
        val result = InlineEdit.toggle("你好世界", 2, 4, "**")
        assertEquals("你好**世界**", result.text)
        val off = InlineEdit.toggle(result.text, result.selectionStart, result.selectionEnd, "**")
        assertEquals("你好世界", off.text)
        val math = InlineEdit.toggle("前后", 1, 1, "$")
        assertEquals("前\$公式\$后", math.text)
    }

    @Test
    fun pathsStayRelativeToThePost() {
        assertEquals("source/_drafts", HexoPaths.draftsDirectory("source/_posts"))
        assertEquals("笔-记.md", HexoPaths.fileNameForTitle("笔 记"))
        val remote = HexoPaths.imageRemotePath("source/_posts", "source/_posts/标题.md", "a.jpg", ImageMode.PostFolder)
        assertEquals("source/_posts/标题/a.jpg", remote)
        assertEquals("./标题/a.jpg", HexoPaths.imagePublicSrc("source/_posts/标题.md", "a.jpg", ImageMode.PostFolder))
        assertEquals("/images/a.jpg", HexoPaths.imagePublicSrc("source/_posts/标题.md", "a.jpg", ImageMode.SourceImages))
        assertNull(HexoPaths.repoPathForImage("source/_posts", "source/_posts/标题.md", "https://example.com/a.jpg"))
    }

    @Test
    fun preservesFrontMatterCommentsAndFoldedTitle() {
        val source = """
            ---
            title: >-
              示例标题：Writing and Formatting Content for a
              Generic Hexo Blog
            tags:
              - Example Tag
            abbrlink: sample123
            date: 2026-01-01 12:00:00
            # password: hidden
            ---

            正文
        """.trimIndent() + "\n"
        val document = PostCodec.parse(source)
        assertEquals(
            "示例标题：Writing and Formatting Content for a Generic Hexo Blog",
            document.title,
        )
        assertEquals(listOf("Example Tag"), document.tags)
        assertEquals("sample123", document.abbrlink)
        assertEquals(listOf("# password: hidden"), document.extras)
        val again = PostCodec.parse(PostCodec.render(document))
        assertEquals(document.title, again.title)
        assertEquals(document.extras, again.extras)
        assertEquals(document.abbrlink, again.abbrlink)
    }

    @Test
    fun roundTripsMixedBlocks() {
        val source = """
            ---
            title: '示例: 一篇'
            date: 2026-01-01 08:00:00
            tags:
              - 技术
            categories:
              - 技术
            abbrlink: abc
            ---

            ## 标题

            普通**段落**。

            > [!WARNING]
            > 注意这里

            - 一
            - 二

            ```kotlin
            val a = 1
            ```

            | 名 | 值 |
            | --- | ---: |
            | 速度 | 1 |

            ---

            ![](./示例/a.png)
        """.trimIndent() + "\n"
        val first = PostCodec.parse(source)
        val second = PostCodec.parse(PostCodec.render(first))
        assertEquals(first.copy(path = null, sha = null), second.copy(path = null, sha = null))
        assertIs<Block.Quote>(first.blocks[2])
        assertEquals("WARNING", (first.blocks[2] as Block.Quote).alert)
        assertEquals(Align.Right, (first.blocks[5] as Block.Table).aligns[1])
    }
}

class RealPostsRoundTripTest {
    @Test
    fun existingPostsSurviveEditingRoundTrip() {
        val configuredPath = System.getProperty("mobwriter.test.postsDirectory")
        assumeTrue(!configuredPath.isNullOrBlank(), "Local post fixtures are opt-in")
        val directory = File(requireNotNull(configuredPath))
        require(directory.isDirectory) { "Configured local post directory is unavailable" }
        val failures = mutableListOf<String>()
        directory.listFiles { file -> file.extension == "md" }?.sortedBy { it.name }?.forEach { file ->
            val original = PostCodec.parse(file.readText())
            val rendered = PostCodec.render(original)
            val again = PostCodec.parse(rendered)
            if (original.title != again.title || original.date != again.date || original.tags != again.tags ||
                original.categories != again.categories || original.categoryKey != again.categoryKey ||
                original.series != again.series || original.aplayer != again.aplayer ||
                original.top != again.top || original.sticky != again.sticky ||
                original.abbrlink != again.abbrlink || original.extras != again.extras ||
                original.blocks != again.blocks
            ) {
                failures += file.name + "\n" + diff(original.blocks, again.blocks)
            }
        }
        assertTrue(failures.isEmpty(), failures.take(4).joinToString("\n----\n"))
    }

    private fun diff(left: List<Block>, right: List<Block>): String {
        val max = maxOf(left.size, right.size)
        return buildString {
            append("blocks ${left.size} -> ${right.size}\n")
            for (index in 0 until max) {
                val a = left.getOrNull(index)
                val b = right.getOrNull(index)
                if (a != b) {
                    append("[$index]\n< ${a?.let { snippet(it) }}\n> ${b?.let { snippet(it) }}\n")
                    if (length > 1500) break
                }
            }
        }
    }

    private fun snippet(block: Block): String = when (block) {
        is Block.Paragraph -> "p:" + block.text.take(80)
        is Block.Heading -> "h${block.level}:" + block.text.take(60)
        is Block.Quote -> "q${block.alert}:" + block.text.take(60)
        is Block.BulletList -> "ul:" + block.items.take(3)
        is Block.OrderedList -> "ol:" + block.items.take(3)
        is Block.Code -> "code ${block.language}:" + block.code.take(40)
        is Block.Math -> "math:" + block.latex.take(60)
        is Block.Image -> "img:" + block.src
        is Block.Table -> "table ${block.header} / ${block.rows.size}"
        is Block.Rule -> "rule ${block.mark}"
        is Block.Raw -> "raw:" + block.text.take(40)
    }
}
