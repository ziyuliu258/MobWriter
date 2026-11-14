package dev.mobwriter.app.core.blog

import dev.mobwriter.app.core.model.ImageMode
import dev.mobwriter.app.core.model.SiteDefaults
import dev.mobwriter.app.core.model.SiteEngine
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object HexoPaths {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val isoFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    /** Hexo 的日期，形如 2026-11-14 10:05:00。 */
    fun now(zone: ZoneId = ZoneId.of("Asia/Shanghai")): String =
        LocalDateTime.now(zone).format(dateFormat)

    /** Astro 要带时区的 ISO 8601，Hexo 用 [now]。 */
    fun timestamp(engine: SiteEngine, zone: ZoneId = ZoneId.of("Asia/Shanghai")): String =
        if (engine == SiteEngine.Astro) ZonedDateTime.now(zone).withNano(0).format(isoFormat) else now(zone)

    fun fileNameForTitle(title: String): String {
        val cleaned = title.trim()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("""[\\/:*?"<>|]"""), "")
            .trim('.', ' ')
        return "${cleaned.ifBlank { "未命名" }}.md"
    }

    /** 填了 slug 就用它做文件名，否则按标题生成。两种站点都以文件名作链接。 */
    fun fileNameFor(title: String, slug: String): String {
        val cleaned = slug.trim()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("""[\\/:*?"<>|#%]"""), "")
            .removeSuffix(".mdx")
            .removeSuffix(".md")
            .trim('.', ' ', '-')
        return if (cleaned.isEmpty()) fileNameForTitle(title) else "$cleaned.md"
    }

    fun draftsDirectory(postsPath: String): String {
        val normalized = postsPath.trim().trim('/')
        val parent = normalized.substringBeforeLast('/', "")
        val drafts = "_drafts"
        return if (parent.isEmpty()) drafts else "$parent/$drafts"
    }

    fun join(dir: String, name: String): String =
        dir.trim().trim('/') + "/" + name.trim().trim('/')

    fun baseName(markdownPath: String): String =
        markdownPath.substringAfterLast('/').removeSuffix(".mdx").removeSuffix(".md")

    fun normalizeImageDirectory(directory: String): String {
        val cleaned = cleanRelative(directory)
        return if (cleaned.startsWith("source/") && cleaned.length > "source/".length) cleaned
        else SiteDefaults.HEXO_IMAGES
    }

    fun normalizeAssetsDirectory(directory: String): String =
        cleanRelative(directory).ifEmpty { SiteDefaults.ASTRO_ASSETS }

    fun normalizeRepoPath(path: String, fallback: String): String =
        cleanRelative(path).ifEmpty { fallback }

    fun imageRemotePath(
        postsPath: String,
        markdownPath: String,
        fileName: String,
        mode: ImageMode,
        imageDirectory: String = SiteDefaults.HEXO_IMAGES,
        assetsDirectory: String = SiteDefaults.ASTRO_ASSETS,
    ): String {
        val safe = safeName(fileName)
        return when (mode) {
            ImageMode.PostFolder -> join(join(postsPath, baseName(markdownPath)), safe)
            ImageMode.SourceImages -> join(normalizeImageDirectory(imageDirectory), safe)
            ImageMode.Assets -> join(normalizeAssetsDirectory(assetsDirectory), safe)
        }
    }

    fun imagePublicSrc(
        markdownPath: String,
        fileName: String,
        mode: ImageMode,
        imageDirectory: String = SiteDefaults.HEXO_IMAGES,
        assetsDirectory: String = SiteDefaults.ASTRO_ASSETS,
    ): String {
        val safe = safeName(fileName)
        return when (mode) {
            ImageMode.PostFolder -> "./${baseName(markdownPath)}/$safe"
            ImageMode.SourceImages -> "/" + normalizeImageDirectory(imageDirectory).removePrefix("source/") + "/$safe"
            ImageMode.Assets -> relativePath(markdownPath, join(normalizeAssetsDirectory(assetsDirectory), safe))
        }
    }

    /** 从文章文件到仓库内另一个文件的相对路径。 */
    fun relativePath(fromFile: String, toFile: String): String {
        val from = fromFile.trim().trim('/').substringBeforeLast('/', "")
            .split('/').filter { it.isNotEmpty() }
        val to = toFile.trim().trim('/').split('/').filter { it.isNotEmpty() }
        var shared = 0
        while (shared < from.size && shared < to.size && from[shared] == to[shared]) shared++
        return (List(from.size - shared) { ".." } + to.drop(shared)).joinToString("/").ifBlank { "." }
    }

    /** 把文章里的图片地址换成仓库内的文件路径，供手机拉取预览。外链返回 null。 */
    fun repoPathForImage(postsPath: String, markdownPath: String, src: String): String? {
        val value = src.trim()
        if (value.isEmpty()) return null
        if (value.startsWith("http://") || value.startsWith("https://")) return null
        if (value.split('/').any { it == ".." }) return resolveRelative(markdownPath, value).ifBlank { null }
        if (value.startsWith("/")) return "source" + value
        val posts = postsPath.trim().trim('/')
        if (value.startsWith("./")) return join(posts, value.removePrefix("./"))
        if (value.contains('/')) return join(posts, value)
        return join(join(posts, baseName(markdownPath)), value)
    }

    fun resolveRelative(fromFile: String, relative: String): String {
        val base = fromFile.trim().trim('/').substringBeforeLast('/', "")
        val parts = mutableListOf<String>()
        if (base.isNotEmpty()) parts += base.split('/').filter { it.isNotEmpty() }
        for (segment in relative.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += segment
            }
        }
        return parts.joinToString("/")
    }

    private fun cleanRelative(directory: String): String =
        directory.trim().replace('\\', '/').trim('/')
            .split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")

    private fun safeName(fileName: String): String =
        fileName.substringAfterLast('/').replace(Regex("""[\\/:*?"<>|]"""), "")
}
