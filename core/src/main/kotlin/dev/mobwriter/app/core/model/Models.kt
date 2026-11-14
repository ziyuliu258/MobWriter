package dev.mobwriter.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SiteEngine {
    /** 主题字段不统一：置顶用 top，系列序号的字段名由设置指定。 */
    Hexo,

    /** 专栏用 series 与 series_index，置顶用 pinned，图片用站内相对路径。 */
    Astro,
}

@Serializable
enum class ImageMode {
    /** 图片放进与文章同名的资源文件夹，正文只保留相对路径。 */
    PostFolder,

    /** 图片放进 source/ 下的目录，正文写成站点根路径，如 /images/文件名。 */
    SourceImages,

    /** 图片放进站点资源目录，正文写成从文章到该文件的相对路径。 */
    Assets,
}

object SiteDefaults {
    const val HEXO_IMAGES = "source/images"
    const val ASTRO_ASSETS = "src/assets/imgs"
    const val SERIES_FILE = "source/_data/series.yml"
}

@Serializable
data class BlogSettings(
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    val postsPath: String = "source/_posts",
    val engine: SiteEngine = SiteEngine.Hexo,
    val imageMode: ImageMode = ImageMode.PostFolder,
    /** SourceImages 模式下的仓库目录，须在 source/ 之下。 */
    val imageDirectory: String = SiteDefaults.HEXO_IMAGES,
    /** Assets 模式下的仓库目录。 */
    val assetsDirectory: String = SiteDefaults.ASTRO_ASSETS,
    /**
     * Hexo 主题用来给系列排序的 Front-matter 字段名，如 series_index。
     * 留空则不自动编号。Astro 固定使用 series_index，不读这项。
     */
    val seriesIndexKey: String = "",
    /** Astro 专栏名单。文章里的 series 必须是其中的顶层键。 */
    val seriesFile: String = SiteDefaults.SERIES_FILE,
    val autoBuild: Boolean = true,
    val workflow: String = "publish.yml",
    val token: String = "",
)

@Serializable
enum class Align { Left, Center, Right }

@Serializable
sealed interface Block {
    @Serializable
    @SerialName("p")
    data class Paragraph(val text: String) : Block

    @Serializable
    @SerialName("h")
    data class Heading(val level: Int, val text: String) : Block

    @Serializable
    @SerialName("q")
    data class Quote(val text: String, val alert: String? = null) : Block

    @Serializable
    @SerialName("ul")
    data class BulletList(val items: List<String>) : Block

    @Serializable
    @SerialName("ol")
    data class OrderedList(val items: List<String>) : Block

    @Serializable
    @SerialName("code")
    data class Code(val language: String = "", val code: String) : Block

    /** 独立公式块。latex 里只有公式本身，不包含 $$。 */
    @Serializable
    @SerialName("math")
    data class Math(val latex: String) : Block

    @Serializable
    @SerialName("img")
    data class Image(
        val alt: String,
        val src: String,
        val pendingFile: String? = null,
    ) : Block

    @Serializable
    @SerialName("table")
    data class Table(
        val header: List<String>,
        val aligns: List<Align>,
        val rows: List<List<String>>,
    ) : Block

    @Serializable
    @SerialName("rule")
    data class Rule(val mark: String = "---") : Block

    @Serializable
    @SerialName("raw")
    data class Raw(val text: String) : Block
}

@Serializable
data class PostDocument(
    val path: String? = null,
    val sha: String? = null,
    val title: String = "",
    val date: String = "",
    val tags: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val categoryKey: String? = null,
    val hadTagsKey: Boolean = false,
    val hadCategoriesKey: Boolean = false,
    val series: String? = null,
    /** 专栏阅读顺序。Astro 写入 series_index；Hexo 仅在序号字段就是 series_index 时使用。 */
    val seriesIndex: Int? = null,
    val aplayer: Boolean = false,
    val top: Boolean = false,
    /** Astro 的列表置顶。Hexo 仍用 [top]。 */
    val pinned: Boolean = false,
    val sticky: Int? = null,
    val abbrlink: String? = null,
    val extras: List<String> = emptyList(),
    val blocks: List<Block> = emptyList(),
    /** 新文章的文件名，不含扩展名。留空时按标题生成。 */
    val slug: String = "",
)

data class PostSummary(
    val path: String,
    val sha: String,
    val title: String,
    val date: String,
    val tags: List<String>,
    val categories: List<String>,
    val series: String? = null,
    val seriesIndex: Int? = null,
)

enum class BuildStatus { Started, Skipped, WorkflowMissing, Failed }

data class PublishReport(
    val path: String,
    val sha: String,
    val build: BuildStatus,
    val detail: String,
)

class BlogException(message: String, val code: Int? = null) : Exception(message)
