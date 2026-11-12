package dev.mobwriter.app.core.blog

import java.net.URI

/**
 * 头像跟 Hexo 主题用的是同一张图。
 * 先读站点 `_config.yml` 的 theme、url、root，再读 `_config.<theme>.yml`，没有再读主题 `_config.yml`。
 * 取值顺序是 `avatar.img`、`avatar.url`、标量 `avatar`、`author.avatar`、`about.avatar`。
 * 相对路径按 Hexo `url_for` 接上 root，再用站点 url 的主机拼成绝对地址；同时对应仓库 `source` 里的文件。
 */
object HexoAvatar {
    data class SiteConfig(
        val theme: String,
        val url: String,
        val root: String,
        val sourceDir: String,
    )

    data class ResolvedAvatar(
        val publicUrl: String,
        val sourcePath: String?,
        val themeSourcePath: String?,
    )

    fun parseSite(yaml: String): SiteConfig {
        val theme = scalar(yaml, "theme").orEmpty().trim()
        val url = scalar(yaml, "url").orEmpty().trim()
        val root = scalar(yaml, "root")?.trim()?.ifBlank { "/" } ?: "/"
        val sourceDir = sanitizeSourceDir(scalar(yaml, "source_dir"))
        return SiteConfig(theme, url, root, sourceDir)
    }

    /** 主题覆盖文件优先；里面没有头像时再用主题目录里的配置。 */
    fun chooseAvatar(overrideYaml: String?, themeYaml: String?): String? =
        overrideYaml?.let(::parseAvatar) ?: themeYaml?.let(::parseAvatar)

    /** 站点 `_config.yml` 里的作者名。标量 `author`，或 `author.name`。 */
    fun parseAuthor(yaml: String): String {
        val lines = meaningful(yaml)
        return scalarTop(lines, "author") ?: directChild(lines, "author", "name").orEmpty()
    }

    /**
     * 同一份配置里，`avatar.img` 优先于 `avatar.url`、标量 `avatar`、`author.avatar` 和 `about.avatar`。
     * 侧栏评论之类的嵌套 avatar 不是站点头像。
     */
    fun parseAvatar(yaml: String): String? {
        val lines = meaningful(yaml)
        directChild(lines, "avatar", "img")?.let { return it }
        directChild(lines, "avatar", "url")?.let { return it }
        scalarTop(lines, "avatar")?.let { return it }
        directChild(lines, "author", "avatar")?.let { return it }
        directChild(lines, "about", "avatar")?.let { return it }
        return null
    }

    fun resolve(site: SiteConfig, avatar: String): ResolvedAvatar? {
        val raw = avatar.trim()
        if (raw.isEmpty() || raw == "~" || raw.equals("null", true) || raw.equals("true", true) || raw.equals("false", true) || raw.equals("yes", true) || raw.equals("no", true)) {
            return null
        }
        if (raw.startsWith("https://") || raw.startsWith("http://")) {
            return ResolvedAvatar(
                publicUrl = raw,
                sourcePath = sourceForAbsolute(site, raw),
                themeSourcePath = themeSourceForAbsolute(site, raw),
            )
        }
        if (raw.startsWith("//")) {
            return ResolvedAvatar("https:$raw", null, null)
        }
        val relative = repositoryRelative(site.root, raw) ?: return null
        val publicPath = hexoPublicPath(site.root, raw)
        return ResolvedAvatar(
            publicUrl = absolutePublic(site.url, publicPath),
            sourcePath = joinSource(site.sourceDir, relative),
            themeSourcePath = themeSource(site.theme, relative),
        )
    }

    fun isThemeName(theme: String): Boolean =
        theme.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$"))

    private fun sourceForAbsolute(site: SiteConfig, url: String): String? {
        val path = sameSitePath(site, url) ?: return null
        val relative = repositoryRelative(site.root, path) ?: return null
        return joinSource(site.sourceDir, relative)
    }

    private fun themeSourceForAbsolute(site: SiteConfig, url: String): String? {
        val path = sameSitePath(site, url) ?: return null
        val relative = repositoryRelative(site.root, path) ?: return null
        return themeSource(site.theme, relative)
    }

    private fun sameSitePath(site: SiteConfig, url: String): String? {
        val image = runCatching { URI(url) }.getOrNull() ?: return null
        val base = runCatching { URI(site.url) }.getOrNull() ?: return null
        if (image.host.isNullOrBlank() || !image.host.equals(base.host, ignoreCase = true)) return null
        return image.path
    }

    private fun themeSource(theme: String, relative: String): String? {
        if (!isThemeName(theme)) return null
        return joinSource("themes/$theme/source", relative)
    }

    /** Hexo `url_for`：把 root 接在路径前面，并收起重复的斜杠。 */
    internal fun hexoPublicPath(root: String, avatar: String): String {
        val rootPart = root.trim().ifBlank { "/" }
        val path = avatar.trim().substringBefore('?').substringBefore('#')
        val prefix = if (rootPart == "/") "/" else rootPart.trimEnd('/') + "/"
        val combined = (prefix + path.trimStart('/')).replace(Regex("/{2,}"), "/")
        return if (combined.startsWith("/")) combined else "/$combined"
    }

    internal fun repositoryRelative(root: String, path: String): String? {
        var value = path.trim().substringBefore('?').substringBefore('#')
        if (value.startsWith("./")) value = value.removePrefix("./")
        value = value.trimStart('/')
        val rootPart = root.trim().trim('/')
        if (rootPart.isNotEmpty() && (value == rootPart || value.startsWith("$rootPart/"))) {
            value = value.removePrefix(rootPart).trimStart('/')
        }
        if (value.isEmpty() || value.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
        return value
    }

    internal fun absolutePublic(siteUrl: String, sitePath: String): String {
        val base = siteUrl.trim()
        if (base.isBlank()) return sitePath
        return runCatching {
            val parsed = URI(base)
            if (parsed.scheme.isNullOrBlank() || parsed.host.isNullOrBlank()) sitePath
            else URI(parsed.scheme, parsed.userInfo, parsed.host, parsed.port, sitePath, null, null).toASCIIString()
        }.getOrDefault(sitePath)
    }

    private fun joinSource(dir: String, relative: String): String? {
        val safeDir = dir.trim().trim('/')
        val safeRel = relative.trim().trim('/')
        if (safeDir.isEmpty() || safeRel.isEmpty()) return null
        if (listOf(safeDir, safeRel).any { part -> part.split('/').any { it == ".." } }) return null
        return "$safeDir/$safeRel"
    }

    private fun sanitizeSourceDir(value: String?): String {
        val cleaned = value?.trim()?.trim('/').orEmpty()
        if (cleaned.isEmpty() || cleaned.split('/').any { it.isEmpty() || it == "." || it == ".." }) return "source"
        return cleaned
    }

    private data class Line(val indent: Int, val text: String)

    private fun meaningful(yaml: String): List<Line> =
        yaml.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').lines().mapNotNull { raw ->
            val indent = raw.takeWhile { it == ' ' || it == '\t' }.length
            val text = raw.trim()
            if (text.isEmpty() || text.startsWith("#") || text == "---" || text == "...") null
            else Line(indent, text)
        }

    private fun scalar(yaml: String, key: String): String? = scalarTop(meaningful(yaml), key)

    private fun scalarTop(lines: List<Line>, key: String): String? {
        val line = lines.firstOrNull { it.indent == 0 && keyOf(it.text) == key } ?: return null
        return inlineValue(line.text)
    }

    private fun directChild(lines: List<Line>, parent: String, child: String): String? {
        val index = lines.indexOfFirst { it.indent == 0 && keyOf(it.text) == parent }
        if (index < 0 || index + 1 >= lines.size || lines[index + 1].indent == 0) return null
        val childIndent = lines[index + 1].indent
        var cursor = index + 1
        while (cursor < lines.size && lines[cursor].indent > 0) {
            val line = lines[cursor]
            if (line.indent == childIndent && keyOf(line.text) == child) {
                return inlineValue(line.text)
            }
            val nested = line.indent
            cursor++
            while (cursor < lines.size && lines[cursor].indent > nested) cursor++
        }
        return null
    }

    private fun keyOf(text: String): String = text.substringBefore(':').trim()

    private fun inlineValue(text: String): String? {
        val colon = text.indexOf(':')
        if (colon < 0) return null
        val raw = text.substring(colon + 1).trim()
        if (raw.isEmpty() || raw == "|" || raw == ">" || raw == "|-" || raw == ">-" || raw == "|+" || raw == ">+") {
            return null
        }
        val value = unquote(stripComment(raw)).trim()
        if (value.isEmpty() || value == "~" || value.equals("null", true)) return null
        if (value.equals("true", true) || value.equals("false", true) || value.equals("yes", true) || value.equals("no", true)) {
            return null
        }
        return value
    }

    private fun stripComment(value: String): String {
        var quote: Char? = null
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (quote != null) {
                if (char == quote) quote = null
            } else if (char == '"' || char == '\'') {
                quote = char
            } else if (char == '#' && (index == 0 || value[index - 1].isWhitespace())) {
                return value.substring(0, index).trim()
            }
            index++
        }
        return value.trim()
    }

    private fun unquote(value: String): String {
        if (value.length >= 2) {
            val first = value.first()
            val last = value.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length - 1)
            }
        }
        return value
    }
}
