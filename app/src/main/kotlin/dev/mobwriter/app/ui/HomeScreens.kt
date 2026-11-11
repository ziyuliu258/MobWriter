package dev.mobwriter.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mobwriter.app.core.blog.BlogService
import dev.mobwriter.app.core.github.GitHubClient
import dev.mobwriter.app.core.github.OkHttpTransport
import dev.mobwriter.app.core.model.BlogSettings
import dev.mobwriter.app.core.model.ImageMode
import dev.mobwriter.app.core.model.PostSummary
import dev.mobwriter.app.data.DraftStore
import dev.mobwriter.app.data.ImageIntake
import dev.mobwriter.app.data.SettingsStore
import dev.mobwriter.app.data.StoredDraft

private sealed interface Screen {
    data object Setup : Screen
    data object Posts : Screen
    data class Edit(val remotePath: String?, val draftKey: String) : Screen
}

@Composable
fun MobWriterApp() {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1100)
        showSplash = false
    }
    Box(Modifier.fillMaxSize()) {
    AppContent()
    if (showSplash) SplashCover()
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
private fun AppContent() {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val drafts = remember { DraftStore(context) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    val stack = remember {
        mutableStateListOf<Screen>(if (settings.token.isBlank()) Screen.Setup else Screen.Posts)
    }
    val blog = remember(settings.token, settings.owner, settings.repo, settings.branch, settings.postsPath, settings.imageMode, settings.autoBuild, settings.workflow) {
        BlogService(GitHubClient(OkHttpTransport(), settings.token), settings)
    }
    var knownTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var knownCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    when (val screen = stack.last()) {
        Screen.Setup -> SetupScreen(
            initial = settings,
            onSave = {
                settingsStore.save(it)
                settings = settingsStore.load()
                stack.clear()
                stack.add(Screen.Posts)
            },
            onClose = if (stack.size > 1) ({ stack.removeAt(stack.lastIndex) }) else null,
        )
        Screen.Posts -> PostListScreen(
            blog = blog,
            drafts = drafts,
            onOpen = { path -> stack.add(Screen.Edit(path, DraftStore.keyForRemote(path))) },
            onNew = { stack.add(Screen.Edit(null, DraftStore.newKey())) },
            onSetup = { stack.add(Screen.Setup) },
            onOpenDraft = { draft ->
                stack.add(Screen.Edit(draft.document.path, draft.key.ifBlank { DraftStore.newKey() }))
            },
            onCatalog = { tags, categories ->
                knownTags = tags
                knownCategories = categories
            },
        )
        is Screen.Edit -> {
            val editor = viewModel<EditorViewModel>(
                key = screen.draftKey,
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
                onBack = { stack.removeAt(stack.lastIndex) },
                onPickImage = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostListScreen(
    blog: BlogService,
    drafts: DraftStore,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onSetup: () -> Unit,
    onOpenDraft: (StoredDraft) -> Unit,
    onCatalog: (List<String>, List<String>) -> Unit,
) {
    var posts by remember { mutableStateOf<List<PostSummary>>(emptyList()) }
    var local by remember { mutableStateOf<List<StoredDraft>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        local = drafts.list().filter { it.document.path == null || it.key.startsWith("n-") }
        posts = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { blog.listPosts() } }
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
                    AppIconButton("设置", onClick = onSetup) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            TooltipBox(
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
                if (!loading && visible.isEmpty() && error == null) {
                    item { Text("还没有文章。点右下角开始写。", modifier = Modifier.padding(top = 24.dp)) }
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
private fun SetupScreen(initial: BlogSettings, onSave: (BlogSettings) -> Unit, onClose: (() -> Unit)?) {
    val context = LocalContext.current
    var owner by remember { mutableStateOf(initial.owner) }
    var repo by remember { mutableStateOf(initial.repo) }
    var branch by remember { mutableStateOf(initial.branch) }
    var posts by remember { mutableStateOf(initial.postsPath) }
    var token by remember { mutableStateOf(initial.token) }
    var workflow by remember { mutableStateOf(initial.workflow) }
    var autoBuild by remember { mutableStateOf(initial.autoBuild) }
    var imageMode by remember { mutableStateOf(initial.imageMode) }
    var showToken by remember { mutableStateOf(false) }
    val canSave = token.isNotBlank() && owner.isNotBlank() && repo.isNotBlank()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("连接 Hexo 仓库") },
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
                                    token = token,
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
            Text("令牌只保存在这台手机上。")
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("GitHub 令牌") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        AppIconButton(if (showToken) "隐藏令牌" else "显示令牌", onClick = { showToken = !showToken }) {
                            Icon(
                                if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showToken) "隐藏令牌" else "显示令牌",
                            )
                        }
                        AppIconButton("在 GitHub 创建令牌", onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens/new")))
                        }) {
                            Icon(painterResource(R.drawable.ic_github), contentDescription = "在 GitHub 创建令牌")
                        }
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
