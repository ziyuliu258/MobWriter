package dev.mobwriter.app.core.github

import dev.mobwriter.app.core.model.BlogException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit

data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
)

data class HttpResponse(val code: Int, val bytes: ByteArray) {
    fun text(): String = bytes.toString(Charsets.UTF_8)
}

fun interface HttpTransport {
    fun execute(request: HttpRequest): HttpResponse
}

class OkHttpTransport : HttpTransport {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    override fun execute(request: HttpRequest): HttpResponse {
        val media = "application/json; charset=utf-8".toMediaType()
        val body = when (request.method) {
            "GET" -> null
            else -> (request.body ?: "").toRequestBody(media)
        }
        var call = Request.Builder().url(request.url).apply {
            request.headers.forEach { (name, value) -> header(name, value) }
            method(request.method, body)
        }.build()
        var hops = 0
        while (true) {
            client.newCall(call).execute().use { response ->
                if (!response.isRedirect || hops >= 5) {
                    return HttpResponse(response.code, response.body?.bytes() ?: byteArrayOf())
                }
                val location = response.header("Location")
                    ?: return HttpResponse(response.code, response.body?.bytes() ?: byteArrayOf())
                hops++
                call = call.newBuilder().url(location).build()
            }
        }
    }

    /** 边下边写，供更新进度使用。重定向规则与 [execute] 相同。 */
    fun download(url: String, headers: Map<String, String>, destination: File, maxBytes: Long, onProgress: (Float) -> Unit) {
        var call = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        var hops = 0
        while (true) {
            client.newCall(call).execute().use { response ->
                if (response.isRedirect && hops < 5) {
                    val location = response.header("Location") ?: error("下载被重定向但没有地址")
                    hops++
                    call = call.newBuilder().url(location).build()
                    return@use
                }
                if (response.code !in 200..299) error("下载失败（${response.code}）")
                val body = response.body ?: error("下载内容为空")
                val total = body.contentLength()
                destination.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            downloaded += count
                            if (downloaded > maxBytes) error("安装包太大")
                            output.write(buffer, 0, count)
                            if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                onProgress(1f)
                return
            }
        }
    }
}

data class RemoteEntry(val name: String, val path: String, val sha: String, val type: String)

class GitHubClient(
    private val transport: HttpTransport,
    private val token: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun listDirectory(owner: String, repo: String, path: String, branch: String): List<RemoteEntry> {
        val response = call("GET", contentsUrl(owner, repo, path, branch), null)
        if (response.code == 404) {
            throw BlogException("找不到目录 $path。请确认这是 Hexo 仓库，并且文章目录填写正确。", 404)
        }
        ensure(response)
        val element = json.parseToJsonElement(response.text())
        if (element !is JsonArray) throw BlogException("「$path」不是文件夹。")
        return element.map { item ->
            val obj = item.jsonObject
            RemoteEntry(
                name = obj.text("name"),
                path = obj.text("path"),
                sha = obj.text("sha"),
                type = obj.text("type"),
            )
        }
    }

    fun readText(owner: String, repo: String, path: String, branch: String): Pair<String, String> {
        val file = readObject(owner, repo, path, branch)
        return decodeText(file) to file.text("sha")
    }

    fun readBytes(owner: String, repo: String, path: String, branch: String): ByteArray {
        val meta = call("GET", contentsUrl(owner, repo, path, branch), null)
        if (meta.code == 404) throw BlogException("找不到 $path", 404)
        ensure(meta)
        val file = json.parseToJsonElement(meta.text()).jsonObject
        val inline = file["content"]?.jsonPrimitive?.contentOrNull
        if (!inline.isNullOrBlank()) return Base64.getMimeDecoder().decode(inline)
        val sha = file.text("sha")
        if (sha.isBlank()) throw BlogException("读不到这个文件。")
        val blobUrl = "https://api.github.com/repos/${encode(owner)}/${encode(repo)}/git/blobs/${encode(sha)}"
        val raw = call("GET", blobUrl, null, accept = "application/vnd.github.raw")
        ensure(raw)
        return decodeDownloaded(owner, repo, raw.bytes)
    }

    fun write(
        owner: String,
        repo: String,
        path: String,
        bytes: ByteArray,
        message: String,
        branch: String,
        sha: String?,
    ): String {
        val payload = buildJsonObject {
            put("message", message)
            put("content", Base64.getEncoder().encodeToString(bytes))
            put("branch", branch)
            if (!sha.isNullOrBlank()) put("sha", sha)
        }.toString()
        val response = call("PUT", contentsUrl(owner, repo, path, null), payload)
        if (response.code == 409 || response.code == 422) {
            throw BlogException("这篇文章刚被别处改过，请返回后重新打开再发布。", response.code)
        }
        ensure(response)
        val content = json.parseToJsonElement(response.text()).jsonObject["content"]?.jsonObject
        return content?.text("sha").orEmpty().ifBlank { sha.orEmpty() }
    }

    fun delete(owner: String, repo: String, path: String, message: String, branch: String, sha: String) {
        val payload = buildJsonObject {
            put("message", message)
            put("sha", sha)
            put("branch", branch)
        }.toString()
        val response = call("DELETE", contentsUrl(owner, repo, path, null), payload)
        if (response.code == 404) return
        ensure(response)
    }

    fun dispatch(owner: String, repo: String, workflow: String, branch: String): Int {
        val url = "https://api.github.com/repos/${encode(owner)}/${encode(repo)}/actions/workflows/${encode(workflow)}/dispatches"
        val payload = buildJsonObject { put("ref", branch) }.toString()
        return call("POST", url, payload).code
    }

    /** 拉取站点上的图片。不附带 GitHub 令牌。 */
    fun readPublicBytes(url: String): ByteArray {
        val parsed = runCatching { java.net.URI(url) }.getOrElse { throw BlogException("头像地址无效") }
        val scheme = parsed.scheme?.lowercase()
        if ((scheme != "https" && scheme != "http") || parsed.host.isNullOrBlank()) {
            throw BlogException("头像地址无效")
        }
        val response = transport.execute(
            HttpRequest(
                method = "GET",
                url = url,
                headers = mapOf(
                    "Accept" to "image/*,*/*",
                    "User-Agent" to "MobWriter",
                ),
                body = null,
            ),
        )
        if (response.code !in 200..299 || response.bytes.isEmpty()) {
            throw BlogException("头像加载失败", response.code)
        }
        return response.bytes
    }

    private fun readObject(owner: String, repo: String, path: String, branch: String): JsonObject {
        val response = call("GET", contentsUrl(owner, repo, path, branch), null)
        if (response.code == 404) throw BlogException("找不到 $path", 404)
        ensure(response)
        val element = json.parseToJsonElement(response.text())
        if (element !is JsonObject) throw BlogException("$path 不是文件")
        return element
    }

    private fun decodeDownloaded(owner: String, repo: String, bytes: ByteArray): ByteArray {
        if (bytes.isEmpty() || bytes[0] != '{'.code.toByte()) return bytes
        val file = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        val inline = file["content"]?.jsonPrimitive?.contentOrNull
        if (!inline.isNullOrBlank()) return Base64.getMimeDecoder().decode(inline)
        val sha = file.text("sha")
        if (sha.isBlank()) throw BlogException("读不到这个文件。")
        val blobUrl = "https://api.github.com/repos/${encode(owner)}/${encode(repo)}/git/blobs/${encode(sha)}"
        val raw = call("GET", blobUrl, null, accept = "application/vnd.github.raw")
        ensure(raw)
        if (raw.bytes.isNotEmpty() && raw.bytes[0] == '{'.code.toByte()) {
            val nested = json.parseToJsonElement(raw.text()).jsonObject["content"]?.jsonPrimitive?.contentOrNull
            if (!nested.isNullOrBlank()) return Base64.getMimeDecoder().decode(nested)
        }
        return raw.bytes
    }

    private fun call(
        method: String,
        url: String,
        body: String?,
        accept: String = "application/vnd.github+json",
    ): HttpResponse {
        if (token.isBlank()) throw BlogException("还没有填写 GitHub 令牌。")
        return transport.execute(
            HttpRequest(
                method = method,
                url = url,
                headers = mapOf(
                    "Authorization" to "Bearer $token",
                    "Accept" to accept,
                    "X-GitHub-Api-Version" to "2022-11-28",
                    "User-Agent" to "MobWriter",
                ),
                body = body,
            ),
        )
    }

    private fun ensure(response: HttpResponse) {
        if (response.code in 200..299) return
        val message = runCatching {
            json.parseToJsonElement(response.text()).jsonObject["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val text = when (response.code) {
            401 -> "令牌无效或已过期。"
            403 -> message ?: "没有权限写入这个仓库。令牌需要内容读写，自动构建还需要 Actions 权限。"
            404 -> message ?: "找不到仓库或文件。"
            else -> message ?: "GitHub 请求失败（${response.code}）。"
        }
        throw BlogException(text, response.code)
    }

    private fun decodeText(file: JsonObject): String {
        val encoded = file["content"]?.jsonPrimitive?.contentOrNull
            ?: throw BlogException("这篇文件太大，手机端暂时打不开。")
        return String(Base64.getMimeDecoder().decode(encoded), Charsets.UTF_8)
    }

    private fun JsonObject.text(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

    companion object {
        fun encodePath(path: String): String =
            path.trim().trim('/').split('/').filter { it.isNotEmpty() }.joinToString("/") { encode(it) }

        fun encode(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

        fun contentsUrl(owner: String, repo: String, path: String, branch: String?): String {
            val base = "https://api.github.com/repos/${encode(owner)}/${encode(repo)}/contents/${encodePath(path)}"
            return if (branch.isNullOrBlank()) base else "$base?ref=${encode(branch)}"
        }
    }
}
