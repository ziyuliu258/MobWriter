package dev.mobwriter.app.core.blog

import dev.mobwriter.app.core.github.GitHubClient
import dev.mobwriter.app.core.markdown.PostCodec
import dev.mobwriter.app.core.model.Block
import dev.mobwriter.app.core.model.BlogException
import dev.mobwriter.app.core.model.BlogSettings
import dev.mobwriter.app.core.model.BuildStatus
import dev.mobwriter.app.core.model.PostDocument
import dev.mobwriter.app.core.model.PostSummary
import dev.mobwriter.app.core.model.PublishReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File

class BlogService(
    private val github: GitHubClient,
    val settings: BlogSettings,
    private val readLocal: (String) -> ByteArray = { File(it).readBytes() },
) {
    fun listPosts(): List<PostSummary> = runBlocking {
        val entries = github.listDirectory(settings.owner, settings.repo, settings.postsPath, settings.branch)
        coroutineScope {
            entries.filter { it.type == "file" && it.name.endsWith(".md") }
                .map { entry ->
                    async(Dispatchers.IO) {
                        runCatching { summaryOf(entry.path, entry.sha) }.getOrElse {
                            PostSummary(entry.path, entry.sha, entry.name.removeSuffix(".md"), "", emptyList(), emptyList())
                        }
                    }
                }.awaitAll()
        }.sortedByDescending { it.date }
    }

    fun load(path: String): PostDocument {
        val (text, sha) = github.readText(settings.owner, settings.repo, path, settings.branch)
        return PostCodec.parse(text, path, sha)
    }

    fun loadImage(repoPath: String): ByteArray =
        github.readBytes(settings.owner, settings.repo, repoPath, settings.branch)

    fun publish(document: PostDocument, asDraft: Boolean = false, retainOriginal: Boolean = false): PublishReport {
        if (document.title.isBlank()) throw BlogException("先写标题。")
        val oldPath = document.path
        val oldSha = document.sha
        val directory = if (asDraft) HexoPaths.draftsDirectory(settings.postsPath) else settings.postsPath.trim().trim('/')
        val staying = oldPath != null && oldPath.substringBeforeLast('/', "") == directory
        val preferred = HexoPaths.join(directory, HexoPaths.fileNameForTitle(document.title))
        val existingCopySha = if (asDraft && retainOriginal && !staying) shaOf(preferred) else null
        val path = when {
            staying -> oldPath!!
            existingCopySha != null -> preferred
            else -> uniquePath(directory, HexoPaths.fileNameForTitle(document.title))
        }
        val prepared = uploadImages(document, path)
        val markdown = PostCodec.render(prepared.copy(path = path))
        val shaForWrite = when {
            staying -> oldSha
            existingCopySha != null -> existingCopySha
            else -> null
        }
        val message = when {
            asDraft -> "草稿：${document.title}"
            staying -> "更新：${document.title}"
            else -> "发布：${document.title}"
        }
        val sha = github.write(
            settings.owner,
            settings.repo,
            path,
            markdown.toByteArray(Charsets.UTF_8),
            message,
            settings.branch,
            shaForWrite,
        )
        if (!retainOriginal && oldPath != null && oldPath != path && !oldSha.isNullOrBlank()) {
            runCatching {
                github.delete(settings.owner, settings.repo, oldPath, "移出草稿：${document.title}", settings.branch, oldSha)
            }
        }
        val build = if (asDraft || !settings.autoBuild) {
            BuildStatus.Skipped to if (asDraft) "已存成 Hexo 草稿，生成站点时不会带上它。" else "已写入仓库。自动构建已关闭。"
        } else {
            dispatch()
        }
        return PublishReport(path, sha, build.first, build.second)
    }

    private fun summaryOf(path: String, sha: String): PostSummary {
        val document = load(path)
        return PostSummary(
            path = path,
            sha = sha,
            title = document.title.ifBlank { path.substringAfterLast('/').removeSuffix(".md") },
            date = document.date,
            tags = document.tags,
            categories = document.categories,
        )
    }

    private fun uploadImages(document: PostDocument, markdownPath: String): PostDocument {
        val blocks = document.blocks.map { block ->
            if (block !is Block.Image || block.pendingFile.isNullOrBlank()) block
            else {
                val fileName = block.pendingFile.substringAfterLast('/')
                val remote = HexoPaths.imageRemotePath(settings.postsPath, markdownPath, fileName, settings.imageMode)
                val bytes = readLocal(block.pendingFile)
                val existing = runCatching {
                    github.readText(settings.owner, settings.repo, remote, settings.branch).second
                }.getOrNull()
                github.write(settings.owner, settings.repo, remote, bytes, "配图：$fileName", settings.branch, existing)
                block.copy(
                    src = HexoPaths.imagePublicSrc(markdownPath, fileName, settings.imageMode),
                    pendingFile = null,
                )
            }
        }
        return document.copy(blocks = blocks)
    }

    private fun uniquePath(directory: String, fileName: String): String {
        var candidate = HexoPaths.join(directory, fileName)
        var number = 2
        while (exists(candidate)) {
            val base = fileName.removeSuffix(".md")
            candidate = HexoPaths.join(directory, "$base-$number.md")
            number++
            if (number > 40) break
        }
        return candidate
    }

    private fun exists(path: String): Boolean = shaOf(path) != null

    private fun shaOf(path: String): String? = runCatching {
        github.readText(settings.owner, settings.repo, path, settings.branch).second
    }.getOrNull()

    private fun dispatch(): Pair<BuildStatus, String> {
        val code = github.dispatch(settings.owner, settings.repo, settings.workflow, settings.branch)
        return when (code) {
            204 -> BuildStatus.Started to "文章已提交，站点开始按仓库里的 Hexo 配置构建。"
            404 -> BuildStatus.WorkflowMissing to "文章已写入仓库。还没有自动构建工作流，站点要等下次在电脑上执行 hexo 部署后更新。模板在应用项目的 deploy/publish.yml。"
            401, 403 -> BuildStatus.Failed to "文章已写入仓库，但令牌不能触发 Actions。"
            else -> BuildStatus.Failed to "文章已写入仓库，自动构建没有启动（$code）。"
        }
    }
}
