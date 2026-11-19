package dev.mobwriter.app.core

import dev.mobwriter.app.core.blog.BlogService
import dev.mobwriter.app.core.github.GitHubClient
import dev.mobwriter.app.core.github.HttpRequest
import dev.mobwriter.app.core.github.HttpResponse
import dev.mobwriter.app.core.github.HttpTransport
import dev.mobwriter.app.core.model.BlogSettings
import dev.mobwriter.app.core.model.BuildStatus
import dev.mobwriter.app.core.model.PostDocument
import dev.mobwriter.app.core.model.SiteEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 任何请求都不响应，用于断言产物模式不联网读取站点配置。 */
private object NoNetworkTransport : HttpTransport {
    override fun execute(request: HttpRequest): HttpResponse = throw AssertionError("不应发起网络请求：$request")
}

/** 记录所有请求，GET 返回 404，PUT 返回 200 并带 sha，POST /dispatches 返回 404。 */
private class CapturingTransport : HttpTransport {
    val seen = mutableListOf<HttpRequest>()
    override fun execute(request: HttpRequest): HttpResponse {
        seen += request
        return when {
            request.method == "GET" -> HttpResponse(404, byteArrayOf())
            request.method == "PUT" -> HttpResponse(200, """{"content":{"sha":"abc123"}}""".toByteArray())
            request.url.contains("/dispatches") -> HttpResponse(404, byteArrayOf())
            else -> HttpResponse(200, byteArrayOf())
        }
    }

    fun hasDispatch() = seen.any { it.url.contains("/dispatches") }
}

class PublishToProductTest {

    @Test
    fun settingsDefaultsToNotPublishingToProduct() {
        assertFalse(BlogSettings().publishToProduct)
        assertTrue(BlogSettings(publishToProduct = true).publishToProduct)
    }

    @Test
    fun loadProfileReportsAvatarMissingWithoutTouchingNetwork() {
        val transport = NoNetworkTransport
        val service = BlogService(
            github = GitHubClient(transport, "token"),
            settings = BlogSettings(
                owner = "example",
                repo = "blog",
                branch = "main",
                postsPath = "blog",
                engine = SiteEngine.Hexo,
                publishToProduct = true,
                token = "token",
            ),
        )
        val profile = service.loadProfile()
        assertTrue(profile.avatarMissing)
    }

    @Test
    fun publishSkipsAutoBuildAndDoesNotDispatchWorkflow() {
        val transport = CapturingTransport()
        val service = BlogService(
            github = GitHubClient(transport, "token"),
            settings = BlogSettings(
                owner = "example",
                repo = "blog",
                branch = "main",
                postsPath = "blog",
                engine = SiteEngine.Hexo,
                publishToProduct = true,
                autoBuild = true,
                workflow = "publish.yml",
                token = "token",
            ),
            readLocal = { byteArrayOf() },
        )
        val document = PostDocument(title = "新文章", blocks = emptyList())
        val report = service.publish(document)

        assertEquals(BuildStatus.Skipped, report.build)
        assertTrue(report.detail.contains("产物目录"))
        // 写入文章会调用 PUT，但不应触发 workflow_dispatch（POST .../dispatches）
        assertTrue(transport.seen.any { it.method == "PUT" }, "应写入产物目录中的文章")
        assertFalse(transport.seen.any { it.url.contains("/dispatches") }, "产物模式不应派发构建工作流：$transport")
    }

    @Test
    fun publishWithoutProductFlagStillDispatchesWhenWorkflowMissing() {
        // 反向对照：不开产物对齐且仓库没有 workflow 时，应返回 WorkflowMissing 而非产物提示。
        val transport = CapturingTransport()
        val service = BlogService(
            github = GitHubClient(transport, "token"),
            settings = BlogSettings(
                owner = "example",
                repo = "blog",
                branch = "main",
                postsPath = "source/_posts",
                engine = SiteEngine.Hexo,
                publishToProduct = false,
                autoBuild = true,
                workflow = "publish.yml",
                token = "token",
            ),
            readLocal = { byteArrayOf() },
        )
        val document = PostDocument(title = "新文章", blocks = emptyList())
        val report = service.publish(document)

        assertEquals(BuildStatus.WorkflowMissing, report.build)
        assertTrue(transport.hasDispatch())
    }
}
