package dev.mobwriter.app.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 一页的骨架，全是 Miuix 原件：大标题顶栏随滚动收起，列表到头有回弹，可选下拉刷新。
 * 页面底色透明，自定义背景图能透出来；顶栏是纸色。
 */
@Composable
internal fun InkPage(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    refreshing: Boolean? = null,
    onRefresh: () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val behavior = MiuixScrollBehavior()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val outline = rememberBarOutline(listState)
    // 正文这一层录成采样源：顶栏的渐进模糊、底栏胶囊的毛玻璃都从它取
    val backdrop = rememberLayerBackdrop()
    val barTint = Paper.copy(alpha = 0.72f)
    CompositionLocalProvider(LocalPageBackdrop provides backdrop) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = title,
                color = Color.Transparent,
                modifier = Modifier.progressiveBlurBar(backdrop, barTint),
                scrollBehavior = behavior,
                navigationIcon = {
                    if (onBack != null) {
                        ProvideBarOutline(outline) {
                            AppIconButton("返回", onClick = onBack) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                            }
                        }
                    }
                },
                actions = { ProvideBarOutline(outline) { androidx.compose.foundation.layout.Row { actions() } } },
            )
        },
        bottomBar = bottomBar,
    ) { inner ->
        val list: @Composable () -> Unit = {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .overScrollVertical()
                    .nestedScroll(behavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = inner.calculateTopPadding(),
                    bottom = inner.calculateBottomPadding() + 24.dp,
                ),
                content = content,
            )
        }
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            if (refreshing == null) {
                list()
            } else {
                PullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    topAppBarScrollBehavior = behavior,
                    contentPadding = inner,
                    refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "已刷新"),
                ) { list() }
            }
        }
    }
    }
}

/** 这一页正文的采样源；不在 [InkPage] 里时为 null（底栏退回实色）。 */
internal val LocalPageBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** 一组内容：淡墨小标题，下面一张白笺卡片。title 为空时只有卡片。 */
internal fun LazyListScope.inkSection(
    title: String?,
    key: Any? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = key) {
        if (!title.isNullOrBlank()) SmallTitle(title)
        Card(
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp, top = if (title.isNullOrBlank()) 12.dp else 0.dp),
            content = content,
        )
    }
}

/** 非懒加载的一组：用在表单这类不长、要整页可滚到的页面里。 */
@Composable
internal fun InkGroup(title: String?, padded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    if (!title.isNullOrBlank()) SmallTitle(title)
    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp, top = if (title.isNullOrBlank()) 12.dp else 0.dp)) {
        if (padded) {
            androidx.compose.foundation.layout.Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                content = content,
            )
        } else {
            content()
        }
    }
}

/** 卡片外的一段淡墨说明。 */
internal fun LazyListScope.inkNote(text: String) {
    item {
        Text(
            text,
            color = Muted,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
        )
    }
}

internal enum class MainTab { Home, Me }

/**
 * 主页和「我的」之间的底栏：Miuix 原装悬浮胶囊。在页面里时胶囊是毛玻璃（取正文采样、模糊后压一层纸色），
 * 不在页面里（没有采样源）时退回实色。
 */
@Composable
internal fun MainNavigationBar(tab: MainTab, onSelect: (MainTab) -> Unit) {
    val backdrop = LocalPageBackdrop.current
    val tint = Paper.copy(alpha = 0.62f)
    FloatingNavigationBar(
        color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
        modifier = if (backdrop == null) Modifier else Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CapsuleShape },
            effects = {
                vibrancy()
                blur(18.dp.toPx())
            },
            highlight = null,
            shadow = null,
            onDrawSurface = { drawRect(tint) },
        ),
    ) {
        FloatingNavigationBarItem(
            selected = tab == MainTab.Home,
            onClick = { onSelect(MainTab.Home) },
            icon = Icons.Outlined.Home,
            label = "主页",
        )
        FloatingNavigationBarItem(
            selected = tab == MainTab.Me,
            onClick = { onSelect(MainTab.Me) },
            icon = Icons.Outlined.Person,
            label = "我的",
        )
    }
}

private val CapsuleShape = RoundedCornerShape(50)

/**
 * 悬浮底栏占的高度：页面把它放进 bottomBar 槽位，列表底部就让出这么多，底栏本身画在页面外面。
 * 和 Miuix FloatingNavigationBar 的排法一致：52dp 胶囊，下方 26dp 加导航栏（没有导航栏时 36dp）。
 */
@Composable
internal fun DockSpace() {
    val nav = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()
    val below = if (nav > 0.dp) 26.dp + nav else 36.dp
    androidx.compose.foundation.layout.Spacer(
        Modifier.fillMaxWidth().height(52.dp + below),
    )
}

internal data class BodyHit(val before: String, val match: String, val after: String)

/** 正文里查询第一次出现的位置，前后留一截，方便在列表里预览。 */
internal fun bodyHit(text: String, query: String): BodyHit? {
    val needle = query.trim()
    if (needle.isEmpty()) return null
    val flat = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    val at = flat.indexOf(needle, ignoreCase = true)
    if (at < 0) return null
    val start = (at - 16).coerceAtLeast(0)
    val end = (at + needle.length + 28).coerceAtMost(flat.length)
    return BodyHit(
        before = (if (start > 0) "…" else "") + flat.substring(start, at),
        match = flat.substring(at, at + needle.length),
        after = flat.substring(at + needle.length, end) + if (end < flat.length) "…" else "",
    )
}

/** 命中的那一截正文，查询词用主色标出。 */
@Composable
internal fun BodyPreview(hit: BodyHit, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append(hit.before)
            withStyle(SpanStyle(color = Cinnabar, fontWeight = FontWeight.SemiBold)) { append(hit.match) }
            append(hit.after)
        },
        modifier = modifier.padding(top = 4.dp),
        color = Muted,
        style = MiuixTheme.textStyles.body2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
