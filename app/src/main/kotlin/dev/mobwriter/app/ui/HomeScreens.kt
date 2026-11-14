package dev.mobwriter.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.res.painterResource
import dev.mobwriter.app.R
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mobwriter.app.core.blog.BlogService
import dev.mobwriter.app.core.github.GitHubClient
import dev.mobwriter.app.core.github.OkHttpTransport
import dev.mobwriter.app.core.model.AccountSession
import dev.mobwriter.app.core.model.Appearance
import dev.mobwriter.app.core.model.BlogSettings
import dev.mobwriter.app.core.model.ImageMode
import dev.mobwriter.app.core.model.PostSummary
import dev.mobwriter.app.core.model.connectionLabel
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
import java.util.UUID

internal enum class MainTab { Home, Me }

private sealed interface Overlay {
    data class Settings(val creating: Boolean) : Overlay
    data object SwitchAccount : Overlay
    data class Edit(val remotePath: String?, val draftKey: String) : Overlay
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
        Box(Modifier.fillMaxSize()) {
            AppearanceBackground(appearance.background)
            AppContent(
                appearance = appearance,
                onAppearance = { next ->
                    appearanceStore.save(next)
                    appearance = next
                },
            )
            if (showSplash) SplashCover()
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
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(dev.mobwriter.app.R.drawable.mobwriter_logo),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Text("墨哺", style = MaterialTheme.typography.headlineMedium, color = Ink)
        Text("MobWriter", color = Muted, modifier = Modifier.padding(top = 4.dp))
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
    val blog = remember(settings.token, settings.owner, settings.repo, settings.branch, settings.postsPath, settings.imageMode, settings.autoBuild, settings.workflow) {
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
    var knownTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var knownCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    fun persist(next: AccountSession) {
        accountStore.save(next)
        session = next
    }
    fun pop() {
        if (overlay.isNotEmpty()) overlay.removeAt(overlay.lastIndex)
    }
    BackHandler(enabled = overlay.isNotEmpty(), onBack = ::pop)
    val bottomBar: @Composable () -> Unit = { MobBottomBar(tab) { tab = it } }
    when (val screen = overlay.lastOrNull()) {
        null -> when (tab) {
            MainTab.Home -> key(accountId) {
                PostListScreen(
                    blog = blog,
                    drafts = drafts,
                    onOpen = { path -> overlay.add(Overlay.Edit(path, DraftStore.keyForRemote(path))) },
                    onNew = { overlay.add(Overlay.Edit(null, DraftStore.newKey())) },
                    onOpenDraft = { draft ->
                        overlay.add(Overlay.Edit(draft.document.path, draft.key.ifBlank { DraftStore.newKey() }))
                    },
                    onCatalog = { tags, categories ->
                        knownTags = tags
                        knownCategories = categories
                    },
                    bottomBar = bottomBar,
                )
            }
            MainTab.Me -> MeScreen(
                onOpenSettings = {
                    if (overlay.none { it is Overlay.Settings }) overlay.add(Overlay.Settings(creating = false))
                },
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
        is Overlay.Settings -> SetupScreen(
            initial = if (screen.creating) BlogSettings() else settings,
            title = if (screen.creating) "添加账号" else "设置",
            showUpdate = !screen.creating,
            appearance = appearance,
            onAppearance = onAppearance,
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
            onClose = ::pop,
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
            )
        }
    }
}

@Composable
internal fun MobBottomBar(tab: MainTab, onSelect: (MainTab) -> Unit) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = Cinnabar,
        selectedTextColor = Cinnabar,
        indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
        unselectedIconColor = Muted,
        unselectedTextColor = Muted,
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = tab == MainTab.Home,
            onClick = { onSelect(MainTab.Home) },
            icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
            label = { Text("主页") },
            colors = colors,
        )
        NavigationBarItem(
            selected = tab == MainTab.Me,
            onClick = { onSelect(MainTab.Me) },
            icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            label = { Text("我的") },
            colors = colors,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeScreen(
    onOpenSettings: () -> Unit,
    author: String = "",
    accountLabel: String = "未登录",
    avatar: ImageBitmap? = null,
    avatarLoading: Boolean = false,
    onSwitchAccount: () -> Unit = {},
    bottomBar: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("我的") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AvatarBadge(avatar, avatarLoading)
                if (author.isNotBlank()) {
                    Text(author, style = MaterialTheme.typography.headlineMedium, color = Ink)
                }
                Text(
                    accountLabel,
                    style = if (author.isBlank()) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
                    color = if (author.isBlank()) Ink else Muted,
                    modifier = Modifier.padding(top = if (author.isBlank()) 0.dp else 4.dp),
                )
            }
            MeEntry(Icons.Outlined.SwitchAccount, "切换账号", onSwitchAccount)
            MeEntry(Icons.Outlined.Settings, "设置", onOpenSettings)
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
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = Cinnabar)
        }
        else -> Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Person, contentDescription = "默认头像", tint = Muted, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun MeEntry(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    subtitle: String = "",
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(22.dp))
            if (subtitle.isBlank()) {
                Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 14.dp).weight(1f))
            } else {
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleLarge)
                    Text(subtitle, color = Muted)
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Muted)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("切换账号") },
                navigationIcon = {
                    AppIconButton("返回", onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (accounts.isEmpty()) {
                Text("还没有已保存的账号", color = Muted, modifier = Modifier.padding(vertical = 16.dp))
            }
            accounts.forEach { account ->
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier.weight(1f).clickable { onSwitch(account.id) }.padding(vertical = 14.dp),
                        ) {
                            Text(account.label, style = MaterialTheme.typography.titleLarge)
                            if (account.detail.isNotBlank()) Text(account.detail, color = Muted)
                        }
                        if (account.current) {
                            Icon(Icons.Outlined.Check, contentDescription = "当前账号", tint = Cinnabar)
                        }
                        AppIconButton("删除${account.label}", onClick = { pendingId = account.id }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除${account.label}")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
            MeEntry(Icons.Outlined.PersonAdd, "添加账号", onAdd)
            if (signedIn) {
                Text(
                    "退出当前账号后，这个账号的令牌和草稿仍留在手机上，可以再切换回来。其他账号也不会被清除。",
                    color = Muted,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                )
                MeEntry(Icons.AutoMirrored.Outlined.Logout, "退出登录", onSignOut)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pendingId = null },
            title = { Text("删除账号") },
            text = { Text("「${pending.label}」的令牌和草稿会从这台手机上移除。其他账号不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    val id = pending.id
                    pendingId = null
                    onDelete(id)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingId = null }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostListScreen(
    blog: BlogService,
    drafts: DraftStore,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onOpenDraft: (StoredDraft) -> Unit,
    onCatalog: (List<String>, List<String>) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var posts by remember { mutableStateOf<List<PostSummary>>(emptyList()) }
    var local by remember { mutableStateOf<List<StoredDraft>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val connected = blog.settings.token.isNotBlank() && blog.settings.owner.isNotBlank() && blog.settings.repo.isNotBlank()
    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        local = drafts.list().filter { it.document.path == null || it.key.startsWith("n-") }
        if (!connected) {
            posts = emptyList()
            error = null
            loading = false
            return@LaunchedEffect
        }
        posts = runCatching { withContext(Dispatchers.IO) { blog.listPosts() } }
            .onFailure { error = it.message ?: "读不到文章" }
            .getOrDefault(emptyList())
        onCatalog(posts.flatMap { it.tags }.distinct(), posts.flatMap { it.categories }.distinct())
        loading = false
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("墨哺") },
                actions = {
                    if (loading) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Cinnabar)
                        }
                    } else {
                        AppIconButton("刷新", onClick = { loading = true }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            if (connected) TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("写文章") } },
                state = rememberTooltipState(),
            ) {
                FloatingActionButton(onClick = onNew, containerColor = Cinnabar) {
                    Icon(Icons.Outlined.Edit, contentDescription = "写文章", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
            )
            error?.let { Text(it, color = Cinnabar, modifier = Modifier.padding(top = 8.dp)) }
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                if (local.isNotEmpty()) {
                    item { Text("手机上的草稿", color = Muted, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(local, key = { it.key + it.savedAt }) { draft ->
                        PostRow(draft.document.title.ifBlank { "未命名" }, "还没发布", draft.document.tags, unpublished = true) {
                            onOpenDraft(draft)
                        }
                    }
                }
                item { Text("仓库里的文章", color = Muted, modifier = Modifier.padding(vertical = 8.dp)) }
                val visible = posts.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
                items(visible, key = { it.path }) { post ->
                    PostRow(post.title, post.date, post.tags, unpublished = false) { onOpen(post.path) }
                }
                if (!loading && !connected) {
                    item { Text("还没有登录。到「我的」里切换账号。", modifier = Modifier.padding(top = 24.dp)) }
                } else if (!loading && visible.isEmpty() && error == null) {
                    item { Text("还没有文章", modifier = Modifier.padding(top = 24.dp)) }
                }
                if (loading && posts.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Cinnabar)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostRow(title: String, date: String, tags: List<String>, unpublished: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (unpublished) Icons.Outlined.EditNote else Icons.AutoMirrored.Outlined.Article,
                contentDescription = null,
                tint = if (unpublished) Cinnabar else Muted,
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                val subtitle = listOf(date).plus(tags.take(3)).filter { it.isNotBlank() }.joinToString("  ·  ")
                if (subtitle.isNotBlank()) Text(subtitle, color = Muted)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupScreen(
    initial: BlogSettings,
    onSave: (BlogSettings) -> Unit,
    onClose: (() -> Unit)?,
    title: String = "设置",
    showUpdate: Boolean = true,
    appearance: Appearance = Appearance(),
    onAppearance: (Appearance) -> Unit = {},
) {
    var page by remember { mutableStateOf(SettingsPage.List) }
    BackHandler(enabled = showUpdate && page != SettingsPage.List) { page = SettingsPage.List }
    when {
        showUpdate && page == SettingsPage.List -> SettingsListScreen(
            accountLabel = initial.connectionLabel(),
            appearance = appearance,
            onOpenAccount = { page = SettingsPage.Account },
            onOpenAppearance = { page = SettingsPage.Appearance },
            onClose = onClose,
        )
        showUpdate && page == SettingsPage.Appearance -> AppearanceScreen(
            appearance = appearance,
            onChange = onAppearance,
            onClose = { page = SettingsPage.List },
        )
        else -> AccountSettingsForm(
            initial = initial,
            onSave = onSave,
            onClose = if (showUpdate) ({ page = SettingsPage.List }) else onClose,
            title = if (showUpdate) "账户信息" else title,
        )
    }
}

private enum class SettingsPage { List, Account, Appearance }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsListScreen(
    accountLabel: String,
    appearance: Appearance,
    onOpenAccount: () -> Unit,
    onOpenAppearance: () -> Unit,
    onClose: (() -> Unit)?,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("设置") },
                navigationIcon = {
                    if (onClose != null) {
                        AppIconButton("返回", onClick = onClose) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            MeEntry(Icons.Outlined.AccountCircle, "账户信息", onOpenAccount, subtitle = accountLabel)
            MeEntry(Icons.Outlined.FormatSize, "外观", onOpenAppearance, subtitle = appearance.summary())
            UpdateSettingRow()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var imageMode by remember { mutableStateOf(initial.imageMode) }
    val savedToken = initial.token
    val tokenToStore = token.trim().ifBlank { savedToken }
    val canSave = tokenToStore.isNotBlank() && owner.isNotBlank() && repo.isNotBlank()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(title) },
                navigationIcon = {
                    if (onClose != null) {
                        AppIconButton("返回", onClick = onClose) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
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
                                    imageMode = imageMode,
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (savedToken.isNotBlank()) "令牌只保存在这台手机上。已保存，留空则保持不变。" else "令牌只保存在这台手机上。")
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("GitHub 令牌") },
                placeholder = { if (savedToken.isNotBlank()) Text("已保存，留空则保持不变") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                trailingIcon = {
                    AppIconButton("在 GitHub 创建令牌", onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens/new")))
                    }) {
                        Icon(painterResource(R.drawable.ic_github), contentDescription = "在 GitHub 创建令牌")
                    }
                },
            )
            OutlinedTextField(owner, { owner = it }, label = { Text("仓库所有者") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(repo, { repo = it }, label = { Text("仓库名") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(branch, { branch = it }, label = { Text("分支") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(posts, { posts = it }, label = { Text("文章目录") }, modifier = Modifier.fillMaxWidth())
            Text("图片放在哪", modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = imageMode == ImageMode.PostFolder, onClick = { imageMode = ImageMode.PostFolder })
                Text("跟文章放在一起")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = imageMode == ImageMode.SourceImages, onClick = { imageMode = ImageMode.SourceImages })
                Text("放进 source/images")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("发布后触发自动构建", modifier = Modifier.weight(1f))
                Switch(checked = autoBuild, onCheckedChange = { autoBuild = it })
            }
            OutlinedTextField(workflow, { workflow = it }, label = { Text("工作流文件名") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}
