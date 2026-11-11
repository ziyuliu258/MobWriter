package dev.mobwriter.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ImageMode {
    /** 图片放进与文章同名的资源文件夹，正文只保留相对路径。 */
    PostFolder,

    /** 图片放进 source/images，正文写成 /images/文件名。适合站点在域名根目录的 Hexo。 */
    SourceImages,
}

@Serializable
data class BlogSettings(
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    val postsPath: String = "source/_posts",
    val imageMode: ImageMode = ImageMode.PostFolder,
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
    val aplayer: Boolean = false,
    val top: Boolean = false,
    val sticky: Int? = null,
    val abbrlink: String? = null,
    val extras: List<String> = emptyList(),
    val blocks: List<Block> = emptyList(),
)

data class PostSummary(
    val path: String,
    val sha: String,
    val title: String,
    val date: String,
    val tags: List<String>,
    val categories: List<String>,
)

enum class BuildStatus { Started, Skipped, WorkflowMissing, Failed }

data class PublishReport(
    val path: String,
    val sha: String,
    val build: BuildStatus,
    val detail: String,
)

class BlogException(message: String, val code: Int? = null) : Exception(message)
