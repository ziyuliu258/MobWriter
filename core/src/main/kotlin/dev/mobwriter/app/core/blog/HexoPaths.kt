package dev.mobwriter.app.core.blog

import dev.mobwriter.app.core.model.ImageMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object HexoPaths {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun now(zone: ZoneId = ZoneId.of("Asia/Shanghai")): String =
        LocalDateTime.now(zone).format(dateFormat)

    fun fileNameForTitle(title: String): String {
        val cleaned = title.trim()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("""[\\/:*?"<>|]"""), "")
            .trim('.', ' ')
        return "${cleaned.ifBlank { "未命名" }}.md"
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
        markdownPath.substringAfterLast('/').removeSuffix(".md")

    fun imageRemotePath(postsPath: String, markdownPath: String, fileName: String, mode: ImageMode): String {
        val safe = fileName.substringAfterLast('/').replace(Regex("""[\\/:*?"<>|]"""), "")
        return when (mode) {
            ImageMode.PostFolder -> join(join(postsPath, baseName(markdownPath)), safe)
            ImageMode.SourceImages -> join("source/images", safe)
        }
    }

    fun imagePublicSrc(markdownPath: String, fileName: String, mode: ImageMode): String {
        val safe = fileName.substringAfterLast('/').replace(Regex("""[\\/:*?"<>|]"""), "")
        return when (mode) {
            ImageMode.PostFolder -> "./${baseName(markdownPath)}/$safe"
            ImageMode.SourceImages -> "/images/$safe"
        }
    }

    /** 把文章里的图片地址换成仓库内的文件路径，供手机拉取预览。外链返回 null。 */
    fun repoPathForImage(postsPath: String, markdownPath: String, src: String): String? {
        val value = src.trim()
        if (value.isEmpty()) return null
        if (value.startsWith("http://") || value.startsWith("https://")) return null
        if (value.startsWith("/")) return "source" + value
        val posts = postsPath.trim().trim('/')
        if (value.startsWith("./")) return join(posts, value.removePrefix("./"))
        if (value.contains('/')) return join(posts, value)
        return join(join(posts, baseName(markdownPath)), value)
    }
}
