package dev.mobwriter.app.core

import dev.mobwriter.app.core.blog.BlogService
import dev.mobwriter.app.core.github.GitHubClient
import dev.mobwriter.app.core.github.HttpRequest
import dev.mobwriter.app.core.github.HttpResponse
import dev.mobwriter.app.core.github.HttpTransport
import dev.mobwriter.app.core.model.BlogSettings
import dev.mobwriter.app.core.model.PostSummary
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IncrementalListingTest {
    @Test
    fun onlyChangedPostsAreDownloadedAgain() {
        val transport = RecordingTransport { request ->
            when {
                request.url.contains("/contents/source/_posts/a.md") -> file("---\ntitle: 新标题\ndate: 2026-01-02\n---\n正文")
                request.url.contains("/contents/source/_posts/b.md") -> file("---\ntitle: 不该被读\ndate: 2026-01-01\n---\n")
                request.url.contains("/contents/source/_posts") -> HttpResponse(
                    200,
                    """[{"name":"a.md","path":"source/_posts/a.md","sha":"new","type":"file"},
                        {"name":"b.md","path":"source/_posts/b.md","sha":"same","type":"file"}]""".toByteArray(),
                )
                else -> HttpResponse(404, """{"message":"Not Found"}""".toByteArray())
            }
        }
        val blog = BlogService(
            GitHubClient(transport, "token"),
            BlogSettings(owner = "example-owner", repo = "example-blog", branch = "main", token = "token"),
        )
        val known = listOf(
            PostSummary("source/_posts/a.md", "old", "旧标题", "2026-01-02", emptyList(), emptyList()),
            PostSummary("source/_posts/b.md", "same", "沿用的标题", "2026-01-01", listOf("缓存"), emptyList()),
        )

        val posts = blog.listPosts(known)

        assertEquals(listOf("新标题", "沿用的标题"), posts.map { it.title })
        assertEquals(listOf("缓存"), posts.first { it.sha == "same" }.tags)
        assertTrue(transport.seen.none { it.url.contains("b.md") }, "sha 没变的文章不该再下载")
        assertTrue(transport.seen.any { it.url.contains("a.md") })
    }

    private fun file(text: String): HttpResponse {
        val encoded = Base64.getEncoder().encodeToString(text.toByteArray())
        return HttpResponse(200, """{"sha":"new","encoding":"base64","content":"$encoded"}""".toByteArray())
    }
}

private class RecordingTransport(private val handler: (HttpRequest) -> HttpResponse) : HttpTransport {
    val seen = mutableListOf<HttpRequest>()
    override fun execute(request: HttpRequest): HttpResponse {
        seen += request
        return handler(request)
    }
}
