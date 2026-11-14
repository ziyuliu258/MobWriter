package dev.mobwriter.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mobwriter.app.R
import dev.mobwriter.app.core.blog.BlogService
import dev.mobwriter.app.core.blog.HexoPaths
import dev.mobwriter.app.core.github.GitHubClient
import dev.mobwriter.app.core.github.OkHttpTransport
import dev.mobwriter.app.core.model.AccountSession
import dev.mobwriter.app.core.model.Appearance
import dev.mobwriter.app.core.model.BlogSettings
import dev.mobwriter.app.core.model.ImageMode
import dev.mobwriter.app.core.model.PostGrouping
import dev.mobwriter.app.core.model.PostListing
import dev.mobwriter.app.core.model.PostSummary
import dev.mobwriter.app.core.model.SiteDefaults
import dev.mobwriter.app.core.model.SiteEngine
import dev.mobwriter.app.core.model.connectionLabel
import dev.mobwriter.app.core.model.searchText
import dev.mobwriter.app.data.AccountStore
import dev.mobwriter.app.data.AppearanceStore
import dev.mobwriter.app.data.AvatarCache
import dev.mobwriter.app.data.DraftStore
import dev.mobwriter.app.data.ImageIntake
import dev.mobwriter.app.data.StoredDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import com.kyant.backdrop.backdrops.layerBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.time.LocalDate
import java.util.UUID

private sealed interface Scene {
    data class Tabs(val tab: MainTab) : Scene
    data class Page(val overlay: Overlay) : Scene
}

private sealed interface Overlay {
    /** 账户信息表单。creating = 添加新账号。 */
    data class Settings(val creating: Boolean) : Overlay
    data object Appearance : Overlay
    data object SwitchAccount : Overlay
    data class Edit(val remotePath: String?, val draftKey: String) : Overlay
    data object Search : Overlay
}

internal data class AccountChoice(
    val id: String,
    val label: String,
    val detail: String,
    val current: Boolean,
)

@Composable
fun MobWriterApp() {
    val context = LocalContext.current
    val appearanceStore = remember { AppearanceStore(context) }
    var appearance by remember { mutableStateOf(appearanceStore.load()) }
    MobWriterTheme(appearance) {
        var showSplash by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1100)
            showSplash = false
        }
        UpdateHost {
            Box(Modifier.fillMaxSize().background(Paper)) {
                AppearanceBackground(appearance.background)
                AppContent(
                    appearance = appearance,
                    onAppearance = { next ->
                        appearanceStore.save(next)
                        appearance = next
                    },
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = showSplash,
                    exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(420)),
                ) {
                    SplashCover()
                }
            }
        }
    }
}

@Composable
private fun SplashCover() {
    Column(
        Modifier.fillMaxSize().background(Paper),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.mobwriter_logo),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Text("墨哺", style = MiuixTheme.textStyles.title2, color = Ink)
        Text("MobWriter", color = Muted, style = MiuixTheme.textStyles.footnote1, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AppContent(
    appearance: Appearance,
    onAppearance: (Appearance) -> Unit,
) {
    val context = LocalContext.current
    val accountStore = remember { AccountStore(context) }
    var session by remember {
        mutableStateOf(
            accountStore.load().also { loaded ->
                DraftStore.migrateLooseDrafts(context, loaded.active?.id)
            },
        )
    }
    val settings = session.active?.settings ?: BlogSettings()
    val accountId = session.active?.id.orEmpty()
    var tab by remember { mutableStateOf(if (session.active == null) MainTab.Me else MainTab.Home) }
    val overlay = remember {
        mutableStateListOf<Overlay>().apply {
            if (session.accounts.isEmpty()) add(Overlay.Settings(creating = false))
        }
    }
    val drafts = remember(accountId) { DraftStore(context, accountId) }
    val avatarCache = remember { AvatarCache(context) }
    val blog = remember(
        settings.token,
        settings.owner,
        settings.repo,
        settings.branch,
        settings.postsPath,
        settings.engine,
        settings.imageMode,
        settings.imageDirectory,
        settings.assetsDirectory,
        settings.seriesIndexKey,
        settings.seriesFile,
        settings.autoBuild,
        settings.workflow,
    ) {
        BlogService(GitHubClient(OkHttpTransport(), settings.token), settings)
    }
    var author by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf<ImageBitmap?>(null) }
    var avatarLoading by remember { mutableStateOf(false) }
    LaunchedEffect(accountId, settings.owner, settings.repo, settings.branch, settings.token) {
        val currentAccount = accountId
        author = ""
        val cached = currentAccount.takeIf { it.isNotBlank() }?.let { avatarCache.read(it) }
        avatar = cached?.let { decodeAvatar(it) }
        if (settings.token.isBlank() || settings.owner.isBlank() || settings.repo.isBlank()) {
            avatarLoading = false
            return@LaunchedEffect
        }
        avatarLoading = avatar == null
        val profile = try {
            withContext(Dispatchers.IO) { blog.loadProfile() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (!isActive) return@LaunchedEffect
        if (profile == null) {
            avatarLoading = false
            return@LaunchedEffect
        }
        author = profile.author
        val fresh = profile.avatar?.let { bytes -> decodeAvatar(bytes)?.let { bytes to it } }
        when {
            fresh != null -> {
                avatarCache.write(currentAccount, fresh.first)
                avatar = fresh.second
            }
            profile.avatarMissing -> {
                avatarCache.delete(currentAccount)
                avatar = null
            }
        }
        avatarLoading = false
    }
    // 文章列表挂在整个应用上：换页回来不重新拉
    val catalogScope = androidx.compose.runtime.rememberCoroutineScope()
    val catalog = remember(blog, drafts) { PostCatalog(blog, drafts, catalogScope) }
    val knownTags = remember(catalog.posts) { catalog.posts.flatMap { it.tags }.distinct() }
    val knownCategories = remember(catalog.posts) { catalog.posts.flatMap { it.categories }.distinct() }
    var filter by remember(accountId) { mutableStateOf(PostFilter()) }
    fun persist(next: AccountSession) {
        accountStore.save(next)
        session = next
    }
    fun pop() {
        if (overlay.isNotEmpty()) overlay.removeAt(overlay.lastIndex)
    }
    BackHandler(enabled = overlay.isNotEmpty(), onBack = ::pop)
    // 底栏只画一份，浮在页面外面；页面只给它让出位置，换页时它不跟着动
    val bottomBar: @Composable () -> Unit = { DockSpace() }
    val dockBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val scene = overlay.lastOrNull()?.let { Scene.Page(it) } ?: Scene.Tabs(tab)
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().layerBackdrop(dockBackdrop)) {
    AnimatedContent(
        targetState = scene,
        transitionSpec = {
            val from = initialState
            val to = targetState
            val forward = when {
                from is Scene.Tabs && to is Scene.Page -> true
                from is Scene.Page && to is Scene.Tabs -> false
                from is Scene.Tabs && to is Scene.Tabs -> to.tab.ordinal >= from.tab.ordinal
                else -> true
            }
            val fade = tween<Float>(220)
            val enter = fadeIn(fade) + slideInHorizontally { full -> if (forward) full / 5 else -full / 5 }
            val exit = fadeOut(fade) + slideOutHorizontally { full -> if (forward) -full / 8 else full / 5 }
            enter togetherWith exit
        },
        label = "page",
    ) { current ->
    when (val screen = (current as? Scene.Page)?.overlay) {
        null -> when ((current as Scene.Tabs).tab) {
            MainTab.Home -> key(accountId) {
                PostListScreen(
                    catalog = catalog,
                    filter = filter,
                    onFilter = { filter = it },
                    onGrouping = { onAppearance(appearance.copy(grouping = it)) },
                    onSearch = { overlay.add(Overlay.Search) },
                    onOpen = { path -> overlay.add(Overlay.Edit(path, DraftStore.keyForRemote(path))) },
                    onNew = { overlay.add(Overlay.Edit(null, DraftStore.newKey())) },
                    onOpenDraft = { draft ->
                        overlay.add(Overlay.Edit(draft.document.path, draft.key.ifBlank { DraftStore.newKey() }))
                    },
                    grouping = appearance.grouping,
                    bottomBar = bottomBar,
                )
            }
            MainTab.Me -> MeScreen(
                onOpenAccount = {
                    if (overlay.none { it is Overlay.Settings }) overlay.add(Overlay.Settings(creating = false))
                },
                onOpenAppearance = {
                    if (overlay.none { it is Overlay.Appearance }) overlay.add(Overlay.Appearance)
                },
                appearance = appearance,
                onAppearance = onAppearance,
                author = author,
                accountLabel = session.active?.settings?.connectionLabel() ?: "未登录",
                avatar = avatar,
                avatarLoading = avatarLoading,
                onSwitchAccount = {
                    if (overlay.none { it is Overlay.SwitchAccount }) overlay.add(Overlay.SwitchAccount)
                },
                bottomBar = bottomBar,
            )
        }
        Overlay.Search -> SearchPage(
            catalog = catalog,
            onOpen = { path -> overlay.add(Overlay.Edit(path, DraftStore.keyForRemote(path))) },
            onOpenDraft = { draft ->
                overlay.add(Overlay.Edit(draft.document.path, draft.key.ifBlank { DraftStore.newKey() }))
            },
            onClose = ::pop,
        )
        Overlay.Appearance -> AppearanceScreen(
            appearance = appearance,
            onChange = onAppearance,
            onClose = ::pop,
        )
        is Overlay.Settings -> SetupScreen(
            initial = if (screen.creating) BlogSettings() else settings,
            title = if (screen.creating) "添加账号" else "账户信息",
            onSave = { updated ->
                val wasSignedOut = session.active == null
                val next = if (screen.creating) {
                    session.add(updated, UUID.randomUUID().toString())
                } else {
                    session.replaceActive(updated, UUID.randomUUID().toString())
                }
                persist(next)
                overlay.clear()
                if (wasSignedOut || screen.creating) tab = MainTab.Home
            },
            // 还没有任何账号时这是第一页，没有可以返回的地方
            onClose = if (session.accounts.isEmpty() && overlay.size <= 1) null else ::pop,
        )
        Overlay.SwitchAccount -> SwitchAccountScreen(
            accounts = session.accounts.map { account ->
                AccountChoice(
                    id = account.id,
                    label = account.settings.connectionLabel(),
                    detail = account.settings.branch.trim(),
                    current = account.id == session.activeId,
                )
            },
            signedIn = session.active != null,
            onSwitch = { id ->
                persist(session.switchTo(id))
                overlay.clear()
            },
            onAdd = { overlay.add(Overlay.Settings(creating = true)) },
            onSignOut = {
                persist(session.signOut())
                overlay.clear()
                tab = MainTab.Me
            },
            onDelete = { id ->
                DraftStore.deleteAccountFiles(context, id)
                avatarCache.delete(id)
                val next = session.remove(id)
                persist(next)
                if (next.active == null) tab = MainTab.Me
            },
            onClose = ::pop,
        )
        is Overlay.Edit -> {
            val editor = viewModel<EditorViewModel>(
                key = "$accountId/${screen.draftKey}",
                factory = EditorViewModelFactory(blog, drafts, screen.draftKey, screen.remotePath),
            )
            val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
                if (uri != null) {
                    ImageIntake.save(context, uri)?.let(editor::insertImage)
                }
            }
            EditorScreen(
                viewModel = editor,
                blog = blog,
                suggestedTags = knownTags,
                suggestedCategories = knownCategories,
                onBack = ::pop,
                onPickImage = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                onPublished = { catalog.refresh(byUser = false) },
            )
        }
    }
    }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = scene is Scene.Tabs,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn(tween(220)) + androidx.compose.animation.slideInVertically(tween(280)) { it / 2 },
        exit = fadeOut(tween(160)) + androidx.compose.animation.slideOutVertically(tween(220)) { it / 2 },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalPageBackdrop provides dockBackdrop) {
            MainNavigationBar(tab = tab, onSelect = { tab = it })
        }
    }
    }
}

/** 主页的筛选：只看某个分类、某个标签。只在这次打开里有效。 */
internal data class PostFilter(val category: String? = null, val tag: String? = null) {
    val active: Boolean get() = category != null || tag != null
    fun matches(post: PostSummary): Boolean =
        (category == null || category in post.categories) && (tag == null || tag in post.tags)
}

/** 搜索页：顶上是 Miuix 原装搜索栏，命中的草稿和文章列在下面。 */
@Composable
private fun SearchPage(
    catalog: PostCatalog,
    onOpen: (String) -> Unit,
    onOpenDraft: (StoredDraft) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // 和主页同一份列表，不再自己拉一遍
    LaunchedEffect(Unit) { catalog.open() }
    val posts = catalog.posts
    val local = catalog.local
    val needle = query.trim()
    val hits = remember(needle, posts, local) {
        if (needle.isEmpty()) return@remember emptyList()
        buildList {
            local.forEach { draft ->
                val plain = draft.document.blocks.joinToString("\n") { it.searchText() }
                val title = draft.document.title.ifBlank { "未命名" }
                if (title.contains(needle, true) || plain.contains(needle, true)) {
                    add(Triple(title, bodyHit(plain, needle), { onOpenDraft(draft) }))
                }
            }
            posts.forEach { post ->
                if (post.title.contains(needle, true) || post.plain.contains(needle, true)) {
                    add(Triple(post.title, bodyHit(post.plain, needle), { onOpen(post.path) }))
                }
            }
        }
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            SearchBar(
                inputField = {
                    InputField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = {},
                        expanded = true,
                        onExpandedChange = { if (!it) onClose() },
                        label = "搜索标题和正文",
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null, tint = Muted, modifier = Modifier.padding(start = 12.dp, end = 4.dp))
                        },
                    )
                },
                onExpandedChange = { if (!it) onClose() },
                modifier = Modifier.background(Paper).padding(top = 8.dp).statusBarsPadding(),
                expanded = true,
                outsideEndAction = {
                    Text(
                        "取消",
                        color = Cinnabar,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clickable(interactionSource = null, indication = null, onClick = onClose),
                    )
                },
            ) {}
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxHeight().overScrollVertical(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding() + 8.dp, bottom = inner.calculateBottomPadding() + 24.dp),
        ) {
            when {
                needle.isEmpty() -> inkNote("在手机上的草稿和仓库里的文章中查找。")
                hits.isEmpty() -> inkNote("没有找到「$needle」。")
                else -> inkSection(title = "${hits.size} 篇") {
                    hits.forEach { (title, hit, open) ->
                        BasicComponent(onClick = open) {
                            Text(title, style = ArticleListTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (hit != null) BodyPreview(hit)
                        }
                    }
                }
            }
        }
    }
}

/** 「我的」：头像和账户在上，原来的设置项平铺在下面，不再另开一页。 */
@Composable
internal fun MeScreen(
    onOpenAccount: () -> Unit,
    onOpenAppearance: () -> Unit = {},
    appearance: Appearance = Appearance(),
    onAppearance: (Appearance) -> Unit = {},
    author: String = "",
    accountLabel: String = "未登录",
    avatar: ImageBitmap? = null,
    avatarLoading: Boolean = false,
    onSwitchAccount: () -> Unit = {},
    bottomBar: @Composable () -> Unit,
) {
    InkPage(title = "我的", bottomBar = bottomBar) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AvatarBadge(avatar, avatarLoading)
                Spacer(Modifier.height(12.dp))
                Text(
                    author.ifBlank { accountLabel },
                    style = MiuixTheme.textStyles.title3,
                    color = Ink,
                )
                if (author.isNotBlank()) {
                    Text(accountLabel, color = Muted, style = MiuixTheme.textStyles.body2, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        inkSection(title = "账户") {
            ArrowPreference(title = "账户信息", summary = "仓库、令牌与发布", onClick = onOpenAccount)
            ArrowPreference(title = "切换账号", onClick = onSwitchAccount)
        }
        inkSection(title = "显示") {
            ArrowPreference(title = "外观", summary = appearance.summary(), onClick = onOpenAppearance)
        }
        inkSection(title = "关于") {
            UpdateSettingRow()
        }
    }
}

@Composable
private fun AvatarBadge(avatar: ImageBitmap?, loading: Boolean) {
    when {
        avatar != null -> Image(
            bitmap = avatar,
            contentDescription = "头像",
            modifier = Modifier.size(72.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        loading -> Box(
            Modifier.size(72.dp).semantics { contentDescription = "头像加载中" },
            contentAlignment = Alignment.Center,
        ) {
            InfiniteProgressIndicator(color = Cinnabar)
        }
        else -> Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Person, contentDescription = "默认头像", tint = Muted, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
internal fun SwitchAccountScreen(
    accounts: List<AccountChoice>,
    signedIn: Boolean,
    onSwitch: (String) -> Unit,
    onAdd: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: (String) -> Unit = {},
    onClose: () -> Unit,
) {
    var pendingId by remember { mutableStateOf<String?>(null) }
    val pending = accounts.firstOrNull { it.id == pendingId }
    InkPage(title = "切换账号", onBack = onClose) {
        if (accounts.isEmpty()) {
            inkNote("还没有已保存的账号")
        } else {
            inkSection(title = "已保存的账号") {
                accounts.forEach { account ->
                    BasicComponent(
                        title = account.label,
                        summary = account.detail.ifBlank { null },
                        onClick = { onSwitch(account.id) },
                        endActions = {
                            if (account.current) {
                                Icon(Icons.Outlined.Check, contentDescription = "当前账号", tint = Cinnabar)
                            }
                            AppIconButton("删除${account.label}", onClick = { pendingId = account.id }, tint = Muted) {
                                Icon(Icons.Outlined.Delete, contentDescription = "删除${account.label}")
                            }
                        },
                    )
                }
            }
        }
        inkSection(title = null) {
            ArrowPreference(title = "添加账号", onClick = onAdd)
        }
        if (signedIn) {
            inkNote("退出当前账号后，这个账号的令牌和草稿仍留在手机上，可以再切换回来。其他账号也不会被清除。")
            inkSection(title = null) {
                BasicComponent(
                    title = "退出登录",
                    onClick = onSignOut,
                    startAction = {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = Cinnabar, modifier = Modifier.padding(end = 12.dp))
                    },
                )
            }
        }
    }
    if (pending != null) {
        InkSheet(
            onDismiss = { pendingId = null },
            title = "删除账号",
            primary = "删除",
            onPrimary = {
                val id = pending.id
                pendingId = null
                onDelete(id)
            },
        ) { Text("「${pending.label}」的令牌和草稿会从这台手机上移除。其他账号不受影响。") }
    }
}

/** 测试和单独预览用：自己建一份列表缓存。 */
@Composable
internal fun PostListScreen(
    blog: BlogService,
    drafts: DraftStore,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    onNew: () -> Unit,
    onOpenDraft: (StoredDraft) -> Unit,
    onCatalog: (List<String>, List<String>) -> Unit = { _, _ -> },
    grouping: PostGrouping = PostGrouping.Month,
    bottomBar: @Composable () -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val catalog = remember(blog, drafts) { PostCatalog(blog, drafts, scope) }
    var filter by remember { mutableStateOf(PostFilter()) }
    var shownGrouping by remember(grouping) { mutableStateOf(grouping) }
    LaunchedEffect(catalog.posts) {
        onCatalog(catalog.posts.flatMap { it.tags }.distinct(), catalog.posts.flatMap { it.categories }.distinct())
    }
    PostListScreen(
        catalog = catalog,
        filter = filter,
        onFilter = { filter = it },
        grouping = shownGrouping,
        onGrouping = { shownGrouping = it },
        onOpen = onOpen,
        onSearch = onSearch,
        onNew = onNew,
        onOpenDraft = onOpenDraft,
        bottomBar = bottomBar,
    )
}

/** 主页：草稿和仓库里的文章。右上角菜单选排列方式，二级菜单按分类、标签筛选。 */
@Composable
internal fun PostListScreen(
    catalog: PostCatalog,
    filter: PostFilter,
    onFilter: (PostFilter) -> Unit,
    grouping: PostGrouping,
    onGrouping: (PostGrouping) -> Unit,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    onNew: () -> Unit,
    onOpenDraft: (StoredDraft) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    LaunchedEffect(catalog) { catalog.open() }
    val year = remember { LocalDate.now().year }
    val shown = remember(catalog.posts, filter) { catalog.posts.filter(filter::matches) }
    val groups = remember(shown, grouping) { PostListing.group(shown, grouping) }
    val categories = remember(catalog.posts) { catalog.posts.flatMap { it.categories }.filter(String::isNotBlank).distinct().sorted() }
    val tags = remember(catalog.posts) { catalog.posts.flatMap { it.tags }.filter(String::isNotBlank).distinct().sorted() }
    var menu by remember { mutableStateOf(false) }
    InkPage(
        title = "墨哺",
        bottomBar = bottomBar,
        refreshing = catalog.pulling,
        onRefresh = { catalog.refresh() },
        actions = {
            AppIconButton("搜索", onClick = onSearch) {
                Icon(Icons.Outlined.Search, contentDescription = "搜索")
            }
            AppIconMenu(
                expanded = menu,
                onExpandedChange = { menu = it },
                label = "排列与筛选",
                icon = {
                    Icon(
                        Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = "排列与筛选",
                        tint = if (filter.active) Cinnabar else Ink,
                    )
                },
            ) {
                PostGrouping.entries.forEach { option ->
                    AppDropdownOption(option.label, selected = grouping == option) {
                        menu = false
                        onGrouping(option)
                    }
                }
                if (categories.isNotEmpty()) {
                    AppDropdownSubMenu(title = "只看分类", summary = filter.category ?: "全部") {
                        AppDropdownOption("全部", selected = filter.category == null) {
                            menu = false
                            onFilter(filter.copy(category = null))
                        }
                        categories.forEach { name ->
                            AppDropdownOption(name, selected = filter.category == name) {
                                menu = false
                                onFilter(filter.copy(category = name))
                            }
                        }
                    }
                }
                if (tags.isNotEmpty()) {
                    AppDropdownSubMenu(title = "只看标签", summary = filter.tag ?: "全部") {
                        AppDropdownOption("全部", selected = filter.tag == null) {
                            menu = false
                            onFilter(filter.copy(tag = null))
                        }
                        tags.forEach { name ->
                            AppDropdownOption(name, selected = filter.tag == name) {
                                menu = false
                                onFilter(filter.copy(tag = name))
                            }
                        }
                    }
                }
            }
            if (catalog.connected) {
                AppIconButton("写文章", onClick = onNew) {
                    Icon(Icons.Outlined.Edit, contentDescription = "写文章")
                }
            }
        },
    ) {
        catalog.error?.let { inkNote(it) }
        if (filter.active) {
            inkNote(
                listOfNotNull(filter.category?.let { "分类「$it」" }, filter.tag?.let { "标签「$it」" })
                    .joinToString("、", prefix = "只看") + " · 共 ${shown.size} 篇",
            )
        }
        if (catalog.local.isNotEmpty() && !filter.active) {
            inkSection(title = "手机上的草稿", key = "drafts") {
                catalog.local.forEach { draft ->
                    PostItem(
                        title = draft.document.title.ifBlank { "未命名" },
                        meta = listOf("还没发布"),
                        tags = draft.document.tags,
                        draft = true,
                    ) { onOpenDraft(draft) }
                }
            }
        }
        groups.forEach { group ->
            inkSection(title = group.title.ifBlank { "仓库里的文章" }, key = "g:" + group.title) {
                group.posts.forEach { post ->
                    val series = post.series?.takeIf { it.isNotBlank() && grouping != PostGrouping.Series }
                    val index = post.seriesIndex?.let { "第 $it 篇" }
                    PostItem(
                        title = post.title,
                        meta = listOfNotNull(
                            PostListing.dateLabel(post.date, year).ifBlank { null },
                            series,
                            index.takeIf { grouping == PostGrouping.Series },
                        ),
                        tags = post.tags,
                        excerpt = post.plain,
                        onTag = { onFilter(filter.copy(tag = it)) },
                    ) { onOpen(post.path) }
                }
            }
        }
        when {
            catalog.ready && !catalog.connected -> inkNote("还没有登录。到「我的」里切换账号。")
            catalog.ready && filter.active && shown.isEmpty() -> inkNote("没有符合筛选的文章。")
            catalog.ready && catalog.posts.isEmpty() && catalog.local.isEmpty() && catalog.error == null -> inkNote("还没有文章")
            !catalog.ready -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    InfiniteProgressIndicator(color = Cinnabar)
                }
            }
        }
    }
}

/** 去掉摘要里的 Markdown 记号：链接只留文字，反引号、强调、标题井号去掉。 */
private fun excerptOf(plain: String): String = plain
    .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")
    .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")
    .replace(Regex("""[`*_~#>]+"""), "")
    .replace(Regex("""\s+"""), " ")
    .trim()
    .take(140)

/** 列表里的一篇：衬线标题，两行摘要，一行淡墨的日期，下面一排标签胶囊。草稿用主色标出。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PostItem(
    title: String,
    meta: List<String>,
    tags: List<String> = emptyList(),
    excerpt: String = "",
    draft: Boolean = false,
    onTag: ((String) -> Unit)? = null,
    onClick: () -> Unit,
) {
    BasicComponent(onClick = onClick) {
        Text(title, style = ArticleListTitle, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val summary = remember(excerpt) { excerptOf(excerpt) }
        if (summary.isNotEmpty()) {
            Text(
                summary,
                style = MiuixTheme.textStyles.body2,
                color = Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        val line = meta.filter { it.isNotBlank() }.joinToString("  ·  ")
        val shownTags = tags.filter { it.isNotBlank() }
        if (line.isNotEmpty() || shownTags.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (line.isNotEmpty()) {
                    Text(
                        line,
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (draft) Cinnabar else MiuixTheme.colorScheme.onSurfaceVariantActions,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                shownTags.take(4).forEach { tag -> TagChip(tag, onTag) }
                if (shownTags.size > 4) TagChip("+${shownTags.size - 4}", null)
            }
        }
    }
}

/** 文章上的标签：小号淡色胶囊，点一下只看这个标签。 */
@Composable
private fun TagChip(text: String, onClick: ((String) -> Unit)?) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(MiuixTheme.colorScheme.secondaryContainer)
            .then(if (onClick != null) Modifier.clickable { onClick(text) } else Modifier)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text("#$text", style = MiuixTheme.textStyles.footnote2, color = Muted, maxLines = 1)
    }
}

@Composable
internal fun SetupScreen(
    initial: BlogSettings,
    onSave: (BlogSettings) -> Unit,
    onClose: (() -> Unit)?,
    title: String = "账户信息",
) {
    AccountSettingsForm(initial = initial, onSave = onSave, onClose = onClose, title = title)
}

@Composable
private fun AccountSettingsForm(
    initial: BlogSettings,
    onSave: (BlogSettings) -> Unit,
    onClose: (() -> Unit)?,
    title: String,
) {
    val context = LocalContext.current
    var owner by remember { mutableStateOf(initial.owner) }
    var repo by remember { mutableStateOf(initial.repo) }
    var branch by remember { mutableStateOf(initial.branch) }
    var posts by remember { mutableStateOf(initial.postsPath) }
    var token by remember { mutableStateOf("") }
    var workflow by remember { mutableStateOf(initial.workflow) }
    var autoBuild by remember { mutableStateOf(initial.autoBuild) }
    var engine by remember { mutableStateOf(initial.engine) }
    var imageMode by remember { mutableStateOf(initial.imageMode) }
    var imageDirectory by remember { mutableStateOf(initial.imageDirectory) }
    var assetsDirectory by remember { mutableStateOf(initial.assetsDirectory) }
    var seriesIndexKey by remember { mutableStateOf(initial.seriesIndexKey) }
    var seriesFile by remember { mutableStateOf(initial.seriesFile) }
    val savedToken = initial.token
    val tokenToStore = token.trim().ifBlank { savedToken }
    val canSave = tokenToStore.isNotBlank() && owner.isNotBlank() && repo.isNotBlank()
    InkPage(
        title = title,
        onBack = onClose,
        actions = {
            AppIconButton(
                label = "保存",
                enabled = canSave,
                tint = Cinnabar,
                onClick = {
                    onSave(
                        BlogSettings(
                            owner = owner,
                            repo = repo,
                            branch = branch,
                            postsPath = posts,
                            engine = engine,
                            imageMode = imageMode,
                            imageDirectory = imageDirectory,
                            assetsDirectory = assetsDirectory,
                            seriesIndexKey = seriesIndexKey,
                            seriesFile = seriesFile,
                            autoBuild = autoBuild,
                            workflow = workflow,
                            token = tokenToStore,
                        ),
                    )
                },
            ) {
                Icon(Icons.Outlined.Check, contentDescription = "保存")
            }
        },
    ) {
        item { Column {
        Text(
            "令牌只保存在这台手机上。",
            color = Muted,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
        )
        InkGroup("仓库") {
            run {
                InkField(
                    value = token,
                    onValueChange = { token = it },
                    label = "GitHub 令牌",
                    supporting = if (savedToken.isNotBlank()) "已保存，留空则保持不变" else "",
                    visualTransformation = PasswordVisualTransformation(),
                    trailing = {
                        AppIconButton("在 GitHub 创建令牌", onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens/new")))
                        }) {
                            Icon(painterResource(R.drawable.ic_github), contentDescription = "在 GitHub 创建令牌")
                        }
                    },
                )
                InkField(owner, { owner = it }, "仓库所有者")
                InkField(repo, { repo = it }, "仓库名")
                InkField(branch, { branch = it }, "分支")
                InkField(posts, { posts = it }, "文章目录")
            }
        }
        InkGroup("站点") {
            run {
                InkSegment(
                    options = listOf("Hexo", "Astro"),
                    selected = if (engine == SiteEngine.Astro) 1 else 0,
                    onSelect = { index ->
                        if (index == 0) {
                            engine = SiteEngine.Hexo
                            if (imageMode == ImageMode.Assets) imageMode = ImageMode.PostFolder
                        } else {
                            engine = SiteEngine.Astro
                            imageMode = ImageMode.Assets
                        }
                    },
                )
                if (engine == SiteEngine.Hexo) {
                    Text("图片放在哪", color = Muted, style = MiuixTheme.textStyles.footnote1)
                    InkSegment(
                        options = listOf("跟文章放在一起", "站点图片目录"),
                        selected = if (imageMode == ImageMode.SourceImages) 1 else 0,
                        onSelect = { imageMode = if (it == 1) ImageMode.SourceImages else ImageMode.PostFolder },
                    )
                    if (imageMode == ImageMode.SourceImages) {
                        InkField(
                            imageDirectory,
                            { imageDirectory = it },
                            "图片目录",
                            supporting = "须在 source/ 下，正文写成 /" + HexoPaths.normalizeImageDirectory(imageDirectory).removePrefix("source/") + "/文件名",
                        )
                    }
                    InkField(
                        seriesIndexKey,
                        { seriesIndexKey = it },
                        "系列序号字段，可不填",
                        supporting = "主题用来排序的字段，如 series_index。留空则不自动编号",
                    )
                } else {
                    InkField(
                        assetsDirectory,
                        { assetsDirectory = it },
                        "图片目录",
                        supporting = "正文写成从文章到 " + HexoPaths.normalizeAssetsDirectory(assetsDirectory) + " 的相对路径",
                    )
                    InkField(
                        seriesFile,
                        { seriesFile = it },
                        "专栏名单",
                        supporting = "默认 " + SiteDefaults.SERIES_FILE + "。文章里的专栏名必须是其中的顶层键，序号留空则自动接在最后",
                    )
                }
            }
        }
        InkGroup("发布", padded = false) {
            SwitchPreference(
                title = "发布后触发自动构建",
                checked = autoBuild,
                onCheckedChange = { autoBuild = it },
            )
            FormColumn {
                InkField(workflow, { workflow = it }, "工作流文件名")
            }
        }
        } }
    }
}

/** 卡片里的一叠输入框。 */
@Composable
private fun FormColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}
