package dev.mobwriter.app.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.mobwriter.app.core.blog.BlogService
import dev.mobwriter.app.core.model.PostSummary
import dev.mobwriter.app.data.DraftStore
import dev.mobwriter.app.data.StoredDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 文章列表的缓存，挂在整个应用上，换页不丢。
 *
 * - 第一次打开：先摆出本机存的上一次列表，再在后台增量刷新（一次目录请求，只下载 sha 变了的文章）；
 * - 从别的页回来：不联网，只重读本机草稿；
 * - 下拉、发布成功之后：增量刷新。
 */
@Stable
internal class PostCatalog(
    private val blog: BlogService,
    private val drafts: DraftStore,
    private val scope: CoroutineScope,
) {
    var posts by mutableStateOf<List<PostSummary>>(emptyList())
        private set
    var local by mutableStateOf<List<StoredDraft>>(emptyList())
        private set

    /** 有刷新在跑（包括后台那次）。 */
    var loading by mutableStateOf(false)
        private set

    /** 用户自己下拉的那次：只有它显示下拉指示，后台刷新不打扰。 */
    var pulling by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** 已经有一份可以显示的结果（本机缓存或网络）。 */
    var ready by mutableStateOf(false)
        private set

    val connected: Boolean
        get() = with(blog.settings) { token.isNotBlank() && owner.isNotBlank() && repo.isNotBlank() }

    private val source = with(blog.settings) { "$owner/$repo@$branch:$postsPath" }
    private var started = false
    private var job: Job? = null

    fun open() {
        reloadLocal()
        if (started) return
        started = true
        scope.launch {
            if (connected) {
                val cached = withContext(Dispatchers.IO) { drafts.loadCatalog(source) }
                if (cached != null && posts.isEmpty()) {
                    posts = cached
                    ready = true
                }
            }
            refresh(byUser = false)
        }
    }

    fun refresh(byUser: Boolean = true) {
        reloadLocal()
        if (!connected) {
            posts = emptyList()
            error = null
            ready = true
            return
        }
        if (byUser) pulling = true
        if (job?.isActive == true) return
        loading = true
        job = scope.launch {
            try {
                val fresh = withContext(Dispatchers.IO) { blog.listPosts(posts) }
                posts = fresh
                error = null
                withContext(Dispatchers.IO) { drafts.saveCatalog(source, fresh) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "读不到文章"
            } finally {
                loading = false
                pulling = false
                ready = true
            }
        }
    }

    fun reloadLocal() {
        scope.launch {
            local = withContext(Dispatchers.IO) {
                drafts.list().filter { it.document.path == null || it.key.startsWith("n-") }
            }
        }
    }
}
