package dev.mobwriter.app.core

import dev.mobwriter.app.core.github.GitHubClient
import dev.mobwriter.app.core.github.HttpRequest
import dev.mobwriter.app.core.github.HttpResponse
import dev.mobwriter.app.core.github.HttpTransport
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GitHubImageTest {
    @Test
    fun largeFileWithoutInlineContentUsesBlob() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
        val transport = ScriptedTransport(
            listOf(
                HttpResponse(200, """{"sha":"abc123","size":2500000}""".toByteArray()),
                HttpResponse(200, png),
            ),
        )
        val bytes = GitHubClient(transport, "token").readBytes("example-owner", "example-blog", "source/_posts/a/big.png", "main")
        assertContentEquals(png, bytes)
        assertEquals(2, transport.seen.size)
        assertEquals("application/vnd.github+json", transport.seen[0].headers["Accept"])
        assertEquals("application/vnd.github.raw", transport.seen[1].headers["Accept"])
        assertEquals(true, transport.seen[1].url.contains("/git/blobs/abc123"))
    }

    @Test
    fun smallFileBase64StillDecodes() {
        val payload = "hi".toByteArray()
        val encoded = Base64.getEncoder().encodeToString(payload)
        val transport = ScriptedTransport(
            listOf(HttpResponse(200, """{"content":"$encoded","encoding":"base64"}""".toByteArray())),
        )
        val bytes = GitHubClient(transport, "token").readBytes("example-owner", "example-blog", "source/_posts/a/small.png", "main")
        assertContentEquals(payload, bytes)
        assertEquals(1, transport.seen.size)
    }
}

private class ScriptedTransport(private val responses: List<HttpResponse>) : HttpTransport {
    val seen = mutableListOf<HttpRequest>()
    private var index = 0
    override fun execute(request: HttpRequest): HttpResponse {
        seen += request
        return responses[index++]
    }
}
