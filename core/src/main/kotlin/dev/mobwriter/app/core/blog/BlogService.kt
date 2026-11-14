package dev.mobwriter.app.core.blog

import dev.mobwriter.app.core.github.GitHubClient
import dev.mobwriter.app.core.markdown.FrontMatterCodec
import dev.mobwriter.app.core.markdown.PostCodec
import dev.mobwriter.app.core.model.Block
import dev.mobwriter.app.core.model.BlogException
import dev.mobwriter.app.core.model.BlogSettings
import dev.mobwriter.app.core.model.BuildStatus
import dev.mobwriter.app.core.model.PostDocument
import dev.mobwriter.app.core.model.searchText
import dev.mobwriter.app.core.model.PostSummary
import dev.mobwriter.app.core.model.PublishReport
import dev.mobwriter.app.core.model.SiteDefaults
import dev.mobwriter.app.core.model.SiteEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File

data class BlogProfile(
    val author: String = "",
    val avatar: ByteArray? = null,
    val avatarMissing: Boolean = false,
)

class BlogService(
    private val github: GitHubClient,
    val settings: BlogSettings,
    private val readLocal: (String) -> ByteArray = { File(it).readBytes() },
) {
    /**
     * 列出文章。[known] 是上次的结果：目录里 sha 没变的文章直接沿用，只下载新增和改过的，
     * 平常刷新只花一次目录请求。
     */
    fun listPosts(known: List<PostSummary> = emptyList()): List<PostSummary> = runBlocking {
        val entries = github.listDirectory(settings.owner, settings.repo, settings.postsPath, settings.branch)
        val cached = known.associateBy { it.path }
        coroutineScope {
            entries.filter { it.type == "file" && it.name.endsWith(".md") }
                .map { entry ->
                    async(Dispatchers.IO) {
                        cached[entry.path]?.takeIf { it.sha == entry.sha && entry.sha.isNotBlank() }?.let { return@async it }
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

    /**
     * 读取当前博客的作者名，以及主题正在使用的头像。
     * 先找仓库 `source` 和主题 `source` 里的文件，再请求公开地址。配置里没有头像时 [BlogProfile.avatarMissing] 为 true。
     */
    fun loadProfile(): BlogProfile {
        if (settings.token.isBlank() || settings.owner.isBlank() || settings.repo.isBlank()) {
            return BlogProfile(avatarMissing = true)
        }
        return runCatching { readProfile() }.getOrDefault(BlogProfile())
    }

    fun loadAvatar(): ByteArray? = loadProfile().avatar

    private fun readProfile(): BlogProfile {
        if (settings.engine == SiteEngine.Astro) return BlogProfile(avatarMissing = true)
        val siteYaml = readOptionalText("_config.yml") ?: return BlogProfile()
        val author = HexoAvatar.parseAuthor(siteYaml)
        val site = HexoAvatar.parseSite(siteYaml)
        if (!HexoAvatar.isThemeName(site.theme)) return BlogProfile(author = author, avatarMissing = true)
        val avatar = HexoAvatar.chooseAvatar(
            readOptionalText("_config.${site.theme}.yml"),
            readOptionalText("themes/${site.theme}/_config.yml"),
        ) ?: return BlogProfile(author = author, avatarMissing = true)
        val resolved = HexoAvatar.resolve(site, avatar) ?: return BlogProfile(author = author, avatarMissing = true)
        resolved.sourcePath?.let { path -> readOptionalBytes(path)?.let { return BlogProfile(author, it, avatarMissing = false) } }
        resolved.themeSourcePath?.let { path -> readOptionalBytes(path)?.let { return BlogProfile(author, it, avatarMissing = false) } }
        if (resolved.publicUrl.startsWith("https://") || resolved.publicUrl.startsWith("http://")) {
            val bytes = runCatching { github.readPublicBytes(resolved.publicUrl) }.getOrNull()
            if (bytes != null) return BlogProfile(author, bytes, avatarMissing = false)
        }
        return BlogProfile(author = author, avatarMissing = false)
    }

    private fun readOptionalText(path: String): String? =
        runCatching { github.readText(settings.owner, settings.repo, path, settings.branch).first }.getOrNull()

    private fun readOptionalBytes(path: String): ByteArray? =
        runCatching { github.readBytes(settings.owner, settings.repo, path, settings.branch) }.getOrNull()
            ?.takeIf { it.isNotEmpty() }

    fun publish(document: PostDocument, asDraft: Boolean = false, retainOriginal: Boolean = false): PublishReport {
        if (document.title.isBlank()) throw BlogException("先写标题。")
        val oldPath = document.path
        val oldSha = document.sha
        val directory = if (asDraft) HexoPaths.draftsDirectory(settings.postsPath) else settings.postsPath.trim().trim('/')
        val staying = oldPath != null && oldPath.substringBeforeLast('/', "") == directory
        val fileName = HexoPaths.fileNameFor(document.title, document.slug)
        val preferred = HexoPaths.join(directory, fileName)
        val existingCopySha = if (asDraft && retainOriginal && !staying) shaOf(preferred) else null
        val path = when {
            staying -> oldPath!!
            existingCopySha != null -> preferred
            else -> uniquePath(directory, fileName)
        }
        val prepared = uploadImages(shapeForSite(document), path)
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
            val skipped = if (asDraft) "已存成草稿，生成站点时不会带上它。" else "已写入仓库。自动构建已关闭。"
            BuildStatus.Skipped to skipped
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
            series = document.series,
            seriesIndex = when (settings.engine) {
                SiteEngine.Astro -> document.seriesIndex
                SiteEngine.Hexo -> SeriesIndex.read(document, settings.seriesIndexKey)
            },
            plain = document.blocks.joinToString("\n") { it.searchText() },
        )
    }

    /**
     * Astro 把 series_index 当成正式字段，并要求专栏名已经写在专栏名单里。
     * Hexo 只有设置了主题序号字段时才补号，字段名不是 series_index 时写进其他字段。
     */
    private fun shapeForSite(document: PostDocument): PostDocument {
        val shaped = if (settings.engine == SiteEngine.Astro) astroDocument(document) else document
        val series = shaped.series?.trim().orEmpty()
        val missing = when (settings.engine) {
            SiteEngine.Astro -> series.isNotEmpty() && shaped.seriesIndex == null
            SiteEngine.Hexo -> series.isNotEmpty() && settings.seriesIndexKey.isNotBlank() &&
                SeriesIndex.read(shaped, settings.seriesIndexKey) == null
        }
        return if (missing) SeriesNumbers.apply(shaped, listPosts(), settings) else shaped
    }

    private fun astroDocument(document: PostDocument): PostDocument {
        val series = document.series?.trim().orEmpty()
        if (series.isNotEmpty()) {
            val file = HexoPaths.normalizeRepoPath(settings.seriesFile, SiteDefaults.SERIES_FILE)
            SeriesCatalog.requireKnown(series, readOptionalText(file), file)
        }
        return document.copy(
            uuid = document.uuid.ifBlank { java.util.UUID.randomUUID().toString() },
            aplayer = false,
            top = false,
            sticky = null,
            abbrlink = null,
            extras = document.extras.filterNot { FrontMatterCodec.topKey(it) in astroDropped },
        )
    }

    private fun uploadImages(document: PostDocument, markdownPath: String): PostDocument {
        val blocks = document.blocks.map { block ->
            if (block !is Block.Image || block.pendingFile.isNullOrBlank()) block
            else {
                val fileName = block.pendingFile.substringAfterLast('/')
                val remote = HexoPaths.imageRemotePath(
                    settings.postsPath,
                    markdownPath,
                    fileName,
                    settings.imageMode,
                    settings.imageDirectory,
                    settings.assetsDirectory,
                )
                val bytes = readLocal(block.pendingFile)
                val existing = runCatching {
                    github.readText(settings.owner, settings.repo, remote, settings.branch).second
                }.getOrNull()
                github.write(settings.owner, settings.repo, remote, bytes, "配图：$fileName", settings.branch, existing)
                block.copy(
                    src = HexoPaths.imagePublicSrc(
                        markdownPath,
                        fileName,
                        settings.imageMode,
                        settings.imageDirectory,
                        settings.assetsDirectory,
                    ),
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
            204 -> BuildStatus.Started to startedDetail()
            404 -> BuildStatus.WorkflowMissing to missingWorkflowDetail()
            401, 403 -> BuildStatus.Failed to "文章已写入仓库，但令牌不能触发 Actions。"
            else -> BuildStatus.Failed to "文章已写入仓库，自动构建没有启动（$code）。"
        }
    }

    private fun startedDetail(): String = when (settings.engine) {
        SiteEngine.Hexo -> "文章已提交，站点开始按仓库里的 Hexo 配置构建。"
        SiteEngine.Astro -> "文章已提交，站点开始构建。"
    }

    private fun missingWorkflowDetail(): String = when (settings.engine) {
        SiteEngine.Hexo -> "文章已写入仓库。还没有自动构建工作流，站点要等下次在电脑上执行 hexo 部署后更新。模板在应用项目的 deploy/publish.yml。"
        SiteEngine.Astro -> "文章已写入仓库。还没有自动构建工作流，站点要等下次部署后更新。"
    }

    private companion object {
        val astroDropped = setOf("top", "aplayer", "sticky", "abbrlink", "mathjax", "updated")
    }
}
