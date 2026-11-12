package dev.mobwriter.app.core

import dev.mobwriter.app.core.blog.BlogService
import dev.mobwriter.app.core.github.GitHubClient
import dev.mobwriter.app.core.github.HttpRequest
import dev.mobwriter.app.core.github.HttpResponse
import dev.mobwriter.app.core.github.HttpTransport
import dev.mobwriter.app.core.model.BlogSettings
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlogAvatarLoadTest {
    private val site = """
        author: Example Author
        url: https://example.com/blog
        root: /blog/
        theme: liquid
    """.trimIndent()
    private val override = """
        avatar:
          img: /images/avatar.jpg
          effect: false
    """.trimIndent()
    private val theme = """
        avatar:
          img: /img/liquid-icon.svg
        aside:
          card_newest_comments:
            avatar: true
    """.trimIndent()
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)

    @Test
    fun loadsTheThemeAvatarFromTheRepositorySource() {
        val transport = RouteTransport { request ->
            when {
                request.url.contains("/contents/_config.liquid.yml") -> file(override)
                request.url.contains("/contents/themes/liquid/_config.yml") -> file(theme)
                request.url.contains("/contents/_config.yml") -> file(site)
                request.url.contains("/contents/source/images/avatar.jpg") -> file(jpeg)
                else -> missing()
            }
        }
        val profile = blog(transport).loadProfile()
        assertEquals("Example Author", profile.author)
        assertEquals(false, profile.avatarMissing)
        assertContentEquals(jpeg, profile.avatar)
        assertTrue(transport.seen.none { it.url.contains("avatars.githubusercontent.com") || it.url.contains("/user") })
        assertTrue(transport.seen.none { it.url.startsWith("https://example.com/") })
        assertTrue(transport.seen.none { it.url.contains("liquid-icon.svg") })
    }

    @Test
    fun fallsBackToThePublicUrlWithoutSendingTheToken() {
        val transport = RouteTransport { request ->
            when {
                request.url.contains("/contents/_config.liquid.yml") -> file(override)
                request.url.contains("/contents/themes/liquid/_config.yml") -> file(theme)
                request.url.contains("/contents/_config.yml") -> file(site)
                request.url.startsWith("https://example.com/blog/images/avatar.jpg") -> HttpResponse(200, jpeg)
                else -> missing()
            }
        }
        val bytes = blog(transport).loadAvatar()
        assertContentEquals(jpeg, bytes)
        val image = transport.seen.single { it.url.startsWith("https://example.com/") }
        assertEquals("https://example.com/blog/images/avatar.jpg", image.url)
        assertNull(image.headers["Authorization"])
        assertTrue(transport.seen.filter { it.url.contains("api.github.com") }.all { it.headers["Authorization"] == "Bearer token" })
    }

    @Test
    fun missingAvatarDoesNotThrow() {
        val transport = RouteTransport { missing() }
        assertNull(blog(transport).loadAvatar())
        assertNull(
            BlogService(
                GitHubClient(transport, "token"),
                BlogSettings(owner = "example-owner", repo = "example-blog", token = "token"),
            ).loadAvatar(),
        )
    }

    private fun blog(transport: HttpTransport) = BlogService(
        GitHubClient(transport, "token"),
        BlogSettings(owner = "example-owner", repo = "example-blog", branch = "main", token = "token"),
    )

    private fun file(text: String): HttpResponse = file(text.toByteArray())

    private fun file(bytes: ByteArray): HttpResponse {
        val encoded = Base64.getEncoder().encodeToString(bytes)
        return HttpResponse(200, """{"sha":"abc","encoding":"base64","content":"$encoded"}""".toByteArray())
    }

    private fun missing() = HttpResponse(404, """{"message":"Not Found"}""".toByteArray())
}

private class RouteTransport(private val handler: (HttpRequest) -> HttpResponse) : HttpTransport {
    val seen = mutableListOf<HttpRequest>()
    override fun execute(request: HttpRequest): HttpResponse {
        seen += request
        return handler(request)
    }
}
