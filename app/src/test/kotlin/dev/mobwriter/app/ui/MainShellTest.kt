package dev.mobwriter.app.ui

import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class MainShellTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun bottomBarHasHomeAndProfileOnly() {
        val tab = mutableStateOf(MainTab.Home)
        rule.setContent {
            val current by tab
            MobWriterTheme {
                MainNavigationBar(current) { tab.value = it }
            }
        }
        // 悬浮胶囊的文字标签带展开动画，按 Tab 角色找两项更可靠
        val tabs = rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        tabs.assertCountEquals(2)
        tabs[1].performClick()
        rule.runOnIdle { assertEquals(MainTab.Me, tab.value) }
        tabs[0].performClick()
        rule.runOnIdle { assertEquals(MainTab.Home, tab.value) }
    }

    @Test
    fun profileHoldsTheSettingsInPlace() {
        var account = false
        var look = false
        rule.setContent {
            MobWriterTheme {
                MeScreen(onOpenAccount = { account = true }, onOpenAppearance = { look = true }) {
                    MainNavigationBar(MainTab.Me) {}
                }
            }
        }
        // 设置项直接铺在「我的」里，没有单独的「设置」入口
        rule.onNodeWithText("设置").assertDoesNotExist()
        rule.onNodeWithText("切换账号").assertIsDisplayed()
        rule.onNodeWithText("外观").assertIsDisplayed()
        rule.onNodeWithText("界面 标准 · 文章 标准 · 墨哺").assertIsDisplayed()
        val accountTop = rule.onNodeWithText("账户信息").fetchSemanticsNode().boundsInRoot.top
        val appearanceTop = rule.onNodeWithText("外观").fetchSemanticsNode().boundsInRoot.top
        assertTrue(accountTop < appearanceTop)
        rule.onNodeWithText("账户信息").performClick()
        rule.onNodeWithText("外观").performClick()
        rule.runOnIdle {
            assertTrue(account)
            assertTrue(look)
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("检查更新"))
        rule.onNodeWithText("检查更新").assertIsDisplayed()
        rule.onNodeWithText("GitHub 令牌").assertDoesNotExist()
    }

    @Test
    fun accountFormShowsEveryRepositoryField() {
        rule.setContent {
            MobWriterTheme {
                SetupScreen(
                    initial = BlogSettings(owner = "example-owner", repo = "example-app", token = "token"),
                    onSave = {},
                    onClose = {},
                )
            }
        }
        rule.onAllNodesWithText("账户信息").onFirst().assertIsDisplayed()
        rule.onNodeWithText("检查更新").assertDoesNotExist()
        rule.onNodeWithText("外观").assertDoesNotExist()
        rule.onNodeWithText("GitHub 令牌").assertIsDisplayed()
        rule.onNodeWithText("仓库所有者").assertIsDisplayed()
        rule.onNodeWithText("仓库名").assertIsDisplayed()
        rule.onNodeWithText("分支").assertIsDisplayed()
        rule.onNodeWithText("文章目录").assertIsDisplayed()
        rule.onNodeWithText("example-owner").assertIsDisplayed()
        rule.onNodeWithText("example-app").assertIsDisplayed()
        rule.onNodeWithText("图片放在哪").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("跟文章放在一起").assertIsDisplayed()
        rule.onNodeWithText("站点图片目录").assertIsDisplayed()
        rule.onNodeWithText("发布后触发自动构建").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("工作流文件名").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun addAccountOpensTheFormWithoutTheUpdateRow() {
        rule.setContent {
            MobWriterTheme {
                SetupScreen(
                    initial = BlogSettings(),
                    onSave = {},
                    onClose = {},
                    title = "添加账号",
                )
            }
        }
        rule.onAllNodesWithText("添加账号").onFirst().assertIsDisplayed()
        rule.onNodeWithText("GitHub 令牌").assertIsDisplayed()
        rule.onNodeWithText("仓库所有者").assertIsDisplayed()
        rule.onNodeWithText("仓库名").assertIsDisplayed()
        rule.onNodeWithText("分支").assertIsDisplayed()
        rule.onNodeWithText("文章目录").assertIsDisplayed()
        rule.onNodeWithText("图片放在哪").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("发布后触发自动构建").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("工作流文件名").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("检查更新").assertDoesNotExist()
        rule.onNodeWithText("外观").assertDoesNotExist()
    }

    @Test
    fun homeKeepsTheArticleListDraftsAndWriter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drafts = DraftStore(context)
        drafts.save("n-local", PostDocument(title = "未完成的草稿"))
        var writing = false
        var openedDraft = false
        rule.setContent {
            MobWriterTheme {
                PostListScreen(
                    blog = BlogService(
                        GitHubClient(EmptyDirectory(), "token"),
                        BlogSettings(owner = "example-owner", repo = "example-app", token = "token"),
                    ),
                    drafts = drafts,
                    onOpen = {},
                    onSearch = {},
                    onNew = { writing = true },
                    onOpenDraft = { openedDraft = true },
                    onCatalog = { _, _ -> },
                    bottomBar = { MainNavigationBar(MainTab.Home) {} },
                )
            }
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText("未完成的草稿")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("手机上的草稿").assertIsDisplayed()
        rule.onNodeWithText("未完成的草稿").performClick()
        rule.runOnIdle { assertTrue(openedDraft) }
        rule.onAllNodesWithText("设置").assertCountEquals(0)
        rule.onNodeWithContentDescription("写文章").performClick()
        rule.runOnIdle { assertTrue(writing) }
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(2)
    }
}

private class EmptyDirectory : HttpTransport {
    override fun execute(request: HttpRequest): HttpResponse = HttpResponse(200, "[]".toByteArray())
}
