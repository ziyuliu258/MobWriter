package dev.mobwriter.app.core.model

/** 文章列表怎么分组。只影响这台手机上的显示。 */
enum class PostGrouping(val key: String, val label: String) {
    Month("month", "按时间"),
    Category("category", "按分类"),
    Series("series", "按系列"),
    None("none", "不分组"),
    ;

    companion object {
        fun fromStored(value: String?): PostGrouping =
            entries.firstOrNull { it.key == value?.trim() } ?: Month
    }
}

/** 一组文章。title 为空时不画组头。 */
data class PostGroup(val title: String, val posts: List<PostSummary>)

object PostListing {
    private val DATE = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""")

    /** 文章日期写成「2026年3月18日」。今年的省掉年份。读不出来就原样返回。 */
    fun dateLabel(raw: String, currentYear: Int): String {
        val match = DATE.find(raw.trim()) ?: return raw.trim()
        val (year, month, day) = match.destructured
        val md = "${month.toInt()}月${day.toInt()}日"
        return if (year.toInt() == currentYear) md else "${year}年$md"
    }

    /** 月份组头写成「2026年3月」。 */
    private fun monthKey(raw: String): String? {
        val match = DATE.find(raw.trim()) ?: return null
        val (year, month, _) = match.destructured
        return "%s-%02d".format(year, month.toInt())
    }

    fun group(posts: List<PostSummary>, grouping: PostGrouping): List<PostGroup> {
        val newestFirst = posts.sortedByDescending { it.date.trim() }
        return when (grouping) {
            PostGrouping.None -> if (posts.isEmpty()) emptyList() else listOf(PostGroup("", newestFirst))
            PostGrouping.Month -> {
                val dated = newestFirst.groupBy { monthKey(it.date) }
                dated.entries
                    .sortedWith(compareBy<Map.Entry<String?, List<PostSummary>>> { it.key == null }.thenByDescending { it.key })
                    .map { (key, items) ->
                        val title = key?.let { "${it.substring(0, 4)}年${it.substring(5).toInt()}月" } ?: "未注明日期"
                        PostGroup(title, items)
                    }
            }
            PostGrouping.Category -> {
                val (sorted, loose) = newestFirst.partition { it.categories.any(String::isNotBlank) }
                val groups = sorted
                    .groupBy { it.categories.first(String::isNotBlank).trim() }
                    .entries
                    .sortedByDescending { entry -> entry.value.maxOf { it.date.trim() } }
                    .map { (name, items) -> PostGroup(name, items) }
                if (loose.isEmpty()) groups else groups + PostGroup("未分类", loose)
            }
            PostGrouping.Series -> {
                val (inSeries, single) = newestFirst.partition { !it.series.isNullOrBlank() }
                val series = inSeries
                    .groupBy { it.series!!.trim() }
                    .entries
                    .sortedByDescending { entry -> entry.value.maxOf { it.date.trim() } }
                    .map { (name, items) ->
                        PostGroup(name, items.sortedWith(compareBy<PostSummary> { it.seriesIndex ?: Int.MAX_VALUE }.thenBy { it.date.trim() }))
                    }
                if (single.isEmpty()) series else series + PostGroup("单篇", single)
            }
        }
    }
}
