package dev.mobwriter.app.core.blog

import dev.mobwriter.app.core.model.BlogException
import dev.mobwriter.app.core.model.BlogSettings
import dev.mobwriter.app.core.model.PostDocument
import dev.mobwriter.app.core.model.PostSummary
import dev.mobwriter.app.core.model.SiteDefaults
import dev.mobwriter.app.core.model.SiteEngine

/**
 * Hexo 主题自己约定的系列序号。字段名在设置里，写在 extras 中。
 * 字段名就是 series_index 时，改走 [PostDocument.seriesIndex]，避免和正式字段各写一遍。
 */
object SeriesIndex {
    private fun pattern(key: String) = Regex("""^${Regex.escape(key)}:\s*(.*)$""")

    fun read(document: PostDocument, key: String): Int? {
        if (key.isBlank() || key == "series_index") return document.seriesIndex
        val line = document.extras.map { it.lineSequence().first() }.firstOrNull { pattern(key).matches(it) } ?: return null
        return pattern(key).matchEntire(line)!!.groupValues[1].trim().trim('\'', '"').toIntOrNull()
    }

    fun write(document: PostDocument, key: String, value: Int): PostDocument {
        if (key.isBlank() || key == "series_index") return document.copy(seriesIndex = value)
        val kept = document.extras.filterNot { pattern(key).matches(it.lineSequence().first()) }
        return document.copy(extras = kept + "$key: $value")
    }
}

/** Astro 的专栏名单是 series.yml 的顶层键，文章只引用这些名字。 */
object SeriesCatalog {
    private val key = Regex("""^([^\s#][^:]*):\s*$""")

    fun names(yaml: String): Set<String> =
        yaml.lineSequence().mapNotNull { line ->
            key.matchEntire(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        }.toSet()

    fun requireKnown(series: String?, yaml: String?, file: String) {
        val name = series?.trim().orEmpty()
        if (name.isEmpty()) return
        val place = file.trim().ifBlank { SiteDefaults.SERIES_FILE }
        if (yaml == null) throw BlogException("找不到专栏名单 $place。先在那里写上「$name」。")
        if (name !in names(yaml)) throw BlogException("专栏「$name」还不在 $place 里。")
    }
}

object SeriesNumbers {
    /** 有系列、没序号时，取同系列最大序号加一。Hexo 没填字段名时不动。 */
    fun apply(document: PostDocument, peers: List<PostSummary>, settings: BlogSettings): PostDocument {
        val series = document.series?.trim().orEmpty()
        if (series.isEmpty()) return document
        val key = settings.seriesIndexKey.trim()
        val missing = when (settings.engine) {
            SiteEngine.Astro -> document.seriesIndex == null
            SiteEngine.Hexo -> key.isNotEmpty() && SeriesIndex.read(document, key) == null
        }
        if (!missing) return document
        val next = peers
            .filter { it.path != document.path && it.series?.trim() == series }
            .mapNotNull { it.seriesIndex }
            .maxOrNull()?.plus(1) ?: 1
        return when (settings.engine) {
            SiteEngine.Astro -> document.copy(seriesIndex = next)
            SiteEngine.Hexo -> SeriesIndex.write(document, key, next)
        }
    }
}
