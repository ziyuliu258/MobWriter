package dev.mobwriter.app.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.mobwriter.app.core.blog.BlogService
import dev.mobwriter.app.core.github.GitHubClient
import dev.mobwriter.app.core.github.HttpRequest
import dev.mobwriter.app.core.github.HttpResponse
import dev.mobwriter.app.core.github.HttpTransport
import dev.mobwriter.app.core.model.BlogSettings
import dev.mobwriter.app.core.model.PostDocument
import dev.mobwriter.app.data.DraftStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class MeAccountTest {
    @get:Rule
    val rule = createComposeRule()

    @Before
    fun cleanDrafts() {
        File(ApplicationProvider.getApplicationContext<Context>().filesDir, "drafts").deleteRecursively()
    }

    @Test
    fun meTabShowsTheBlogAvatarAndASwitchEntry() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).asImageBitmap()
        rule.setContent {
            MobWriterTheme {
                MeScreen(
                    onOpenAccount = {},
                    author = "Example Author",
                    accountLabel = "alpha/blog",
                    avatar = bitmap,
                    onSwitchAccount = {},
                ) { MainNavigationBar(MainTab.Me) {} }
            }
        }
        rule.onNodeWithContentDescription("头像").assertIsDisplayed()
        rule.onNodeWithContentDescription("默认头像").assertDoesNotExist()
        rule.onNodeWithText("Example Author").assertIsDisplayed()
        rule.onNodeWithText("alpha/blog").assertIsDisplayed()
        rule.onNodeWithText("墨哺").assertDoesNotExist()
        rule.onNodeWithText("切换账号").assertIsDisplayed()
        rule.onNodeWithText("账户信息").assertIsDisplayed()
        rule.onNodeWithText("设置").assertDoesNotExist()
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(2)
    }

    @Test
    fun missingAvatarUsesAPlaceholder() {
        rule.setContent {
            MobWriterTheme {
                MeScreen(onOpenAccount = {}, accountLabel = "未登录") { MainNavigationBar(MainTab.Me) {} }
            }
        }
        rule.onNodeWithContentDescription("默认头像").assertIsDisplayed()
        rule.onNodeWithContentDescription("头像").assertDoesNotExist()
        rule.onNodeWithText("切换账号").assertIsDisplayed()
    }

    @Test
    fun switchingAccountsChangesTheAvatarAndHidesTokens() {
        rule.setContent {
            var screen by remember { mutableStateOf("me") }
            var id by remember { mutableStateOf("a") }
            val alpha = BlogSettings(owner = "alpha", repo = "blog", branch = "main", token = "token-alpha-kept")
            val beta = BlogSettings(owner = "beta", repo = "notes", branch = "docs", token = "token-beta-kept")
            val accounts = listOf(
                AccountChoice("a", "alpha/blog", "main", id == "a"),
                AccountChoice("b", "beta/notes", "docs", id == "b"),
            )
            val active = if (id == "a") alpha else beta
            MobWriterTheme {
                when (screen) {
                    "switch" -> SwitchAccountScreen(
                        accounts = accounts,
                        signedIn = true,
                        onSwitch = {
                            id = it
                            screen = "me"
                        },
                        onAdd = {},
                        onSignOut = { screen = "out" },
                        onClose = { screen = "me" },
                    )
                    "settings" -> SetupScreen(initial = active, onSave = {}, onClose = { screen = "me" })
                    else -> MeScreen(
                        onOpenAccount = { screen = "settings" },
                        author = if (id == "a") "甲作者" else "乙作者",
                        accountLabel = "${active.owner}/${active.repo}",
                        avatar = if (id == "a") Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).asImageBitmap() else null,
                        onSwitchAccount = { screen = "switch" },
                    ) { MainNavigationBar(MainTab.Me) {} }
                }
            }
        }
        rule.onNodeWithContentDescription("头像").assertIsDisplayed()
        rule.onNodeWithText("切换账号").performClick()
        rule.onNodeWithText("token-alpha-kept").assertDoesNotExist()
        rule.onNodeWithText("token-beta-kept").assertDoesNotExist()
        rule.onNodeWithText("可以再切换回来", substring = true).assertIsDisplayed()
        rule.onNodeWithText("beta/notes").performClick()
        rule.onNodeWithContentDescription("默认头像").assertIsDisplayed()
        rule.onNodeWithText("乙作者").assertIsDisplayed()
        rule.onNodeWithText("甲作者").assertDoesNotExist()
        rule.onNodeWithText("beta/notes").assertIsDisplayed()
        rule.onNodeWithText("token-beta-kept").assertDoesNotExist()
        rule.onNodeWithText("GitHub 令牌").assertDoesNotExist()
        rule.onNodeWithText("账户信息").performClick()
        rule.onNodeWithText("beta").assertIsDisplayed()
        rule.onNodeWithText("notes").assertIsDisplayed()
        rule.onNodeWithText("token-beta-kept").assertDoesNotExist()
        rule.onNodeWithText("检查更新").assertDoesNotExist()
    }

    @Test
    fun deleteDropsOnlyTheChosenAccount() {
        rule.setContent {
            var accounts by remember {
                mutableStateOf(
                    listOf(
                        AccountChoice("a", "alpha/blog", "main", true),
                        AccountChoice("b", "beta/notes", "docs", false),
                    ),
                )
            }
            MobWriterTheme {
                SwitchAccountScreen(
                    accounts = accounts,
                    signedIn = true,
                    onSwitch = {},
                    onAdd = {},
                    onSignOut = {},
                    onDelete = { id -> accounts = accounts.filterNot { it.id == id } },
                    onClose = {},
                )
            }
        }
        rule.onNodeWithText("token-alpha-kept").assertDoesNotExist()
        rule.onNodeWithContentDescription("删除beta/notes").performClick()
        rule.onNodeWithText("其他账号不受影响", substring = true).assertIsDisplayed()
        rule.onNodeWithText("删除").performClick()
        rule.onNodeWithText("beta/notes").assertDoesNotExist()
        rule.onNodeWithText("alpha/blog").assertIsDisplayed()
    }

    @Test
    fun settingsKeepASavedTokenHidden() {
        var saved: BlogSettings? = null
        rule.setContent {
            MobWriterTheme {
                SetupScreen(
                    initial = BlogSettings(owner = "alpha", repo = "blog", token = "token-alpha-kept"),
                    onSave = { saved = it },
                    onClose = {},
                )
            }
        }
        rule.onNodeWithText("检查更新").assertDoesNotExist()
        rule.onNodeWithText("token-alpha-kept").assertDoesNotExist()
        rule.onNodeWithText("显示令牌").assertDoesNotExist()
        rule.onNodeWithText("已保存，留空则保持不变", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("保存").performClick()
        rule.runOnIdle {
            assertEquals("token-alpha-kept", saved?.token)
            assertEquals("alpha", saved?.owner)
        }
    }

    @Test
    fun homeListFollowsTheCurrentAccount() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DraftStore(context, "a").save("n-a", PostDocument(title = "甲的草稿"))
        DraftStore(context, "b").save("n-b", PostDocument(title = "乙的草稿"))
        val account = mutableStateOf("a")
        rule.setContent {
            val id by account
            MobWriterTheme {
                androidx.compose.runtime.key(id) {
                    PostListScreen(
                        blog = BlogService(
                            GitHubClient(EmptyPosts(), "token"),
                            BlogSettings(owner = id, repo = "blog", token = "token"),
                        ),
                        drafts = DraftStore(context, id),
                        onOpen = {},
                        onSearch = {},
                        onNew = {},
                        onOpenDraft = {},
                        onCatalog = { _, _ -> },
                        bottomBar = { MainNavigationBar(MainTab.Home) {} },
                    )
                }
            }
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(hasText("甲的草稿")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("乙的草稿").assertDoesNotExist()
        account.value = "b"
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(hasText("乙的草稿")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("甲的草稿").assertDoesNotExist()
        rule.onNodeWithText("乙的草稿").assertIsDisplayed()
    }

    @Test
    fun brokenAvatarBytesDoNotThrow() {
        assertNull(decodeAvatar(byteArrayOf()))
        assertTrue(runCatching { decodeAvatar(byteArrayOf(1, 2, 3, 4)) }.isSuccess)
        val stream = ByteArrayOutputStream()
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, stream)
        assertNotNull(decodeAvatar(stream.toByteArray()))
    }
}

private class EmptyPosts : HttpTransport {
    override fun execute(request: HttpRequest): HttpResponse = HttpResponse(200, "[]".toByteArray())
}
