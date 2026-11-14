package dev.mobwriter.app.core

import dev.mobwriter.app.core.model.PostGrouping
import dev.mobwriter.app.core.model.PostListing
import dev.mobwriter.app.core.model.PostSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class PostListingTest {
    private fun post(path: String, date: String, series: String? = null, index: Int? = null) =
        PostSummary(path = path, sha = "", title = path, date = date, tags = emptyList(), categories = emptyList(), series = series, seriesIndex = index)

    @Test
    fun datesReadLikeABookNotATimestamp() {
        assertEquals("3月18日", PostListing.dateLabel("2026-03-18T00:00:00+08:00", 2026))
        assertEquals("2025年2月19日", PostListing.dateLabel("2025-02-19 10:00:00", 2026))
        assertEquals("someday", PostListing.dateLabel("someday", 2026))
    }

    @Test
    fun monthGroupsAreNewestFirstWithUndatedLast() {
        val groups = PostListing.group(
            listOf(post("a", "2026-01-02"), post("b", "2026-03-18"), post("c", ""), post("d", "2026-03-01")),
            PostGrouping.Month,
        )
        assertEquals(listOf("2026年3月", "2026年1月", "未注明日期"), groups.map { it.title })
        assertEquals(listOf("b", "d"), groups[0].posts.map { it.path })
    }

    @Test
    fun seriesGroupsFollowTheirIndexAndSinglesComeLast() {
        val groups = PostListing.group(
            listOf(
                post("p2", "2025-02-19", "原子物理", 2),
                post("solo", "2026-01-01"),
                post("p1", "2025-02-01", "原子物理", 1),
            ),
            PostGrouping.Series,
        )
        assertEquals(listOf("原子物理", "单篇"), groups.map { it.title })
        assertEquals(listOf("p1", "p2"), groups[0].posts.map { it.path })
    }

    @Test
    fun noGroupingKeepsOneHeaderlessGroup() {
        val groups = PostListing.group(listOf(post("a", "2026-01-02"), post("b", "2026-03-18")), PostGrouping.None)
        assertEquals(listOf(""), groups.map { it.title })
        assertEquals(listOf("b", "a"), groups[0].posts.map { it.path })
    }
}
