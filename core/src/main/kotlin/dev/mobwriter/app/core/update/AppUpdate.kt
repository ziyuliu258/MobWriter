package dev.mobwriter.app.core.update

import dev.mobwriter.app.core.github.HttpRequest
import dev.mobwriter.app.core.github.HttpResponse
import dev.mobwriter.app.core.github.HttpTransport
import dev.mobwriter.app.core.github.OkHttpTransport
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

class UpdateException(message: String) : Exception(message)

data class ReleaseAsset(val name: String, val downloadUrl: String)

data class PublishedRelease(
    val tag: String,
    val versionName: String,
    val htmlUrl: String,
    val assets: List<ReleaseAsset>,
    val notes: String = "",
)

data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkName: String,
)

sealed interface UpdateChoice {
    val pageUrl: String

    data class UpToDate(override val pageUrl: String) : UpdateChoice

    data class Install(
        val versionName: String,
        val versionCode: Int,
        val downloadUrl: String,
        override val pageUrl: String,
        val notes: String = "",
    ) : UpdateChoice

    data class OpenPage(override val pageUrl: String) : UpdateChoice
}

enum class DirectInstallStep { Download, AskPermission, OpenPage }

object AppUpdate {
    const val MaxApkBytes = 150 * 1024 * 1024

    private val slug = Regex("^[A-Za-z0-9_.-]{1,100}$")
    private val numericVersion = Regex("""\d+(\.\d+)*""")
    private val json = Json { ignoreUnknownKeys = true }

    fun isRepositorySlug(value: String): Boolean =
        slug.matches(value) &&
            !value.contains("..") &&
            !value.startsWith(".") &&
            !value.startsWith("-") &&
            !value.endsWith(".") &&
            !value.endsWith("-")

    fun latestReleaseUrl(owner: String, repo: String): String {
        requireSlug(owner, repo)
        return "https://api.github.com/repos/$owner/$repo/releases/latest"
    }

    fun releaseListUrl(owner: String, repo: String): String {
        requireSlug(owner, repo)
        return "https://api.github.com/repos/$owner/$repo/releases?per_page=10"
    }

    fun releasesPage(owner: String, repo: String): String {
        requireSlug(owner, repo)
        return "https://github.com/$owner/$repo/releases"
    }

    fun isAllowedPageUrl(url: String): Boolean {
        val host = httpsHost(url) ?: return false
        return host == "github.com" || host.endsWith(".github.com")
    }

    fun isAllowedArtifactUrl(url: String): Boolean {
        val host = httpsHost(url) ?: return false
        return host == "github.com" ||
            host == "api.github.com" ||
            host == "objects.githubusercontent.com" ||
            host == "release-assets.githubusercontent.com" ||
            host.endsWith(".githubusercontent.com")
    }

    fun looksLikeApk(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    fun parseRelease(body: String): PublishedRelease {
        val element = json.parseToJsonElement(body)
        if (element is JsonArray) {
            if (element.isEmpty()) throw UpdateException("还没有发布包")
            val published = element.firstOrNull { item ->
                item.jsonObject["draft"]?.jsonPrimitive?.booleanOrNull != true
            } ?: throw UpdateException("还没有发布包")
            return releaseFrom(published.jsonObject)
        }
        return releaseFrom(element.jsonObject)
    }

    fun parseManifest(body: String): UpdateManifest {
        val obj = json.parseToJsonElement(body).jsonObject
        val primitive = obj["versionCode"]?.jsonPrimitive
        val code = primitive?.intOrNull ?: primitive?.contentOrNull?.toIntOrNull()
            ?: throw UpdateException("更新信息缺少版本号")
        if (code < 1) throw UpdateException("更新信息缺少版本号")
        val name = obj.text("versionName")
        val apkName = obj.text("apkName").ifBlank { "MobWriter.apk" }
        return UpdateManifest(code, name, apkName)
    }

    fun choose(
        currentVersionCode: Int,
        currentVersionName: String,
        release: PublishedRelease,
        manifest: UpdateManifest?,
    ): UpdateChoice {
        val page = release.htmlUrl
        val download = selectApk(release, manifest)?.downloadUrl?.takeIf { isAllowedArtifactUrl(it) }
        val manifestCode = manifest?.versionCode
        if (manifestCode != null) {
            if (manifestCode <= currentVersionCode) return UpdateChoice.UpToDate(page)
            val name = manifest.versionName.ifBlank { release.versionName }
            return if (download != null) {
                UpdateChoice.Install(name, manifestCode, download, page, release.notes)
            } else {
                UpdateChoice.OpenPage(page)
            }
        }
        val compared = compareVersionNames(currentVersionName, release.versionName)
        if (compared == null) return UpdateChoice.OpenPage(page)
        if (compared >= 0) return UpdateChoice.UpToDate(page)
        return if (download != null) {
            UpdateChoice.Install(release.versionName, 0, download, page, release.notes)
        } else {
            UpdateChoice.OpenPage(page)
        }
    }

    fun compareVersionNames(current: String, remote: String): Int? {
        val left = versionParts(current) ?: return null
        val right = versionParts(remote) ?: return null
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val difference = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }

    fun directInstallStep(canInstallPackages: Boolean, alreadyAskedPermission: Boolean): DirectInstallStep =
        when {
            canInstallPackages -> DirectInstallStep.Download
            alreadyAskedPermission -> DirectInstallStep.OpenPage
            else -> DirectInstallStep.AskPermission
        }

    private fun selectApk(release: PublishedRelease, manifest: UpdateManifest?): ReleaseAsset? {
        val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val named = manifest?.apkName?.takeIf { it.isNotBlank() }
        if (named != null) apks.firstOrNull { it.name == named }?.let { return it }
        return apks.firstOrNull { it.name.equals("MobWriter.apk", ignoreCase = true) } ?: apks.firstOrNull()
    }

    private fun releaseFrom(obj: JsonObject): PublishedRelease {
        val tag = obj.text("tag_name")
        val html = obj.text("html_url")
        val assets = obj["assets"] as? JsonArray ?: JsonArray(emptyList())
        val files = assets.mapNotNull { item ->
            val asset = item.jsonObject
            val name = asset.text("name")
            val url = asset.text("browser_download_url")
            if (name.isBlank() || url.isBlank()) null else ReleaseAsset(name, url)
        }
        if (tag.isBlank() && html.isBlank()) throw UpdateException("还没有发布包")
        return PublishedRelease(
            tag = tag,
            versionName = tag.removePrefix("v").ifBlank { tag },
            htmlUrl = html,
            assets = files,
            notes = obj.text("body"),
        )
    }

    private fun versionParts(value: String): List<Int>? {
        val core = value.trim().removePrefix("v").substringBefore('+').substringBefore('-')
        if (!numericVersion.matches(core)) return null
        return core.split('.').map { it.toInt() }
    }

    private fun httpsHost(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (!uri.userInfo.isNullOrBlank()) return null
        return uri.host?.lowercase()
    }

    private fun requireSlug(owner: String, repo: String) {
        if (!isRepositorySlug(owner) || !isRepositorySlug(repo)) {
            throw UpdateException("未配置更新源")
        }
    }

    private fun JsonObject.text(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
}

class ReleaseLookup(private val transport: HttpTransport) {
    fun latest(owner: String, repo: String): PublishedRelease {
        val response = get(AppUpdate.latestReleaseUrl(owner, repo), "application/vnd.github+json")
        val body = if (response.code == 404) {
            val listed = get(AppUpdate.releaseListUrl(owner, repo), "application/vnd.github+json")
            if (listed.code == 404) throw UpdateException("还没有发布包")
            ensure(listed)
            listed.text()
        } else {
            ensure(response)
            response.text()
        }
        val release = AppUpdate.parseRelease(body)
        val page = release.htmlUrl.ifBlank { AppUpdate.releasesPage(owner, repo) }
        return release.copy(htmlUrl = page)
    }

    fun manifest(release: PublishedRelease): UpdateManifest? {
        val asset = release.assets.firstOrNull { it.name == "update.json" } ?: return null
        if (!AppUpdate.isAllowedArtifactUrl(asset.downloadUrl)) return null
        val response = runCatching { get(asset.downloadUrl, "*/*") }.getOrNull() ?: return null
        if (response.code !in 200..299) return null
        return runCatching { AppUpdate.parseManifest(response.text()) }.getOrNull()
    }

    fun downloadTo(url: String, destination: File, onProgress: (Float) -> Unit = {}) {
        if (!AppUpdate.isAllowedArtifactUrl(url)) throw UpdateException("无法下载安装包")
        val streaming = transport as? OkHttpTransport
        if (streaming != null) {
            destination.parentFile?.mkdirs()
            val partial = File(destination.parentFile, "${destination.name}.part")
            if (partial.exists()) partial.delete()
            try {
                streaming.download(url, downloadHeaders(), partial, AppUpdate.MaxApkBytes.toLong(), onProgress)
            } catch (error: Exception) {
                partial.delete()
                if (error is UpdateException) throw error
                throw UpdateException(error.message ?: "无法下载安装包")
            }
            if (!AppUpdate.looksLikeApk(partial.inputStream().use { stream ->
                    ByteArray(8).also { stream.read(it) }
                })) {
                partial.delete()
                throw UpdateException("下载内容不是安装包")
            }
            if (destination.exists()) destination.delete()
            if (!partial.renameTo(destination)) throw UpdateException("无法保存安装包")
            return
        }
        destination.writeBytes(download(url))
        onProgress(1f)
    }

    fun download(url: String, maxBytes: Int = AppUpdate.MaxApkBytes): ByteArray {
        if (!AppUpdate.isAllowedArtifactUrl(url)) throw UpdateException("无法下载安装包")
        val response = get(url, "*/*")
        if (response.code !in 200..299 || response.bytes.isEmpty()) throw UpdateException("无法下载安装包")
        if (response.bytes.size > maxBytes) throw UpdateException("安装包太大")
        return response.bytes
    }

    private fun downloadHeaders(): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "User-Agent" to "MobWriter",
    )

    private fun get(url: String, accept: String): HttpResponse =
        transport.execute(
            HttpRequest(
                method = "GET",
                url = url,
                headers = mapOf(
                    "Accept" to accept,
                    "User-Agent" to "MobWriter",
                    "X-GitHub-Api-Version" to "2022-11-28",
                ),
                body = null,
            ),
        )

    private fun ensure(response: HttpResponse) {
        if (response.code in 200..299) return
        throw UpdateException(
            when (response.code) {
                403, 429 -> "GitHub 暂时限制了更新检查"
                404 -> "还没有发布包"
                else -> "检查更新失败"
            },
        )
    }
}
