package dev.mobwriter.app.ui

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
@Config(sdk = [28], qualifiers = "w360dp-h640dp-xxhdpi")
class MainShellTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun bottomBarHasHomeAndProfileOnly() {
        val tab = mutableStateOf(MainTab.Home)
        rule.setContent {
            val current by tab
            MobWriterTheme {
                MobBottomBar(current) { tab.value = it }
            }
        }
        rule.onNodeWithText("主页").assertIsDisplayed()
        rule.onNodeWithText("我的").assertIsDisplayed()
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(2)
        rule.onNodeWithText("我的").performClick()
        rule.runOnIdle { assertEquals(MainTab.Me, tab.value) }
        rule.onNodeWithText("主页").performClick()
        rule.runOnIdle { assertEquals(MainTab.Home, tab.value) }
    }

    @Test
    fun profileOpensSettings() {
        var opened = false
        rule.setContent {
            MobWriterTheme {
                MeScreen(onOpenSettings = { opened = true }) {
                    MobBottomBar(MainTab.Me) {}
                }
            }
        }
        rule.onNodeWithText("设置").performClick()
        rule.runOnIdle { assertTrue(opened) }
        rule.onNodeWithText("主页").assertIsDisplayed()
        rule.onAllNodesWithText("我的").assertCountEquals(2)
    }

    @Test
    fun settingsListsAccountAndUpdateAsPeerRows() {
        rule.setContent {
            MobWriterTheme {
                SetupScreen(
                    initial = BlogSettings(owner = "example-owner", repo = "example-app", token = "token"),
                    onSave = {},
                    onClose = {},
                )
            }
        }
        rule.onNodeWithText("设置").assertIsDisplayed()
        rule.onNodeWithText("账户信息").assertIsDisplayed()
        rule.onNodeWithText("外观").assertIsDisplayed()
        rule.onNodeWithText("界面 标准 · 文章 标准").assertIsDisplayed()
        rule.onNodeWithText("检查更新").assertIsDisplayed()
        val accountTop = rule.onNodeWithText("账户信息").fetchSemanticsNode().boundsInRoot.top
        val appearanceTop = rule.onNodeWithText("外观").fetchSemanticsNode().boundsInRoot.top
        val updateTop = rule.onNodeWithText("检查更新").fetchSemanticsNode().boundsInRoot.top
        assertTrue(accountTop < appearanceTop && appearanceTop < updateTop)
        rule.onNodeWithText("example-owner/example-app").assertIsDisplayed()
        rule.onNodeWithText("GitHub 令牌").assertDoesNotExist()
        rule.onNodeWithText("仓库所有者").assertDoesNotExist()
        rule.onNodeWithText("仓库名").assertDoesNotExist()
        rule.onNodeWithText("分支").assertDoesNotExist()
        rule.onNodeWithText("文章目录").assertDoesNotExist()
        rule.onNodeWithText("图片放在哪").assertDoesNotExist()
        rule.onNodeWithText("发布后触发自动构建").assertDoesNotExist()
        rule.onNodeWithText("工作流文件名").assertDoesNotExist()
        rule.onNodeWithText("账户信息").performClick()
        rule.onNodeWithText("检查更新").assertDoesNotExist()
        rule.onNodeWithText("外观").assertDoesNotExist()
        rule.onNodeWithText("GitHub 令牌").assertIsDisplayed()
        rule.onNodeWithText("仓库所有者").assertIsDisplayed()
        rule.onNodeWithText("仓库名").assertIsDisplayed()
        rule.onNodeWithText("分支").assertIsDisplayed()
        rule.onNodeWithText("文章目录").assertIsDisplayed()
        rule.onNodeWithText("图片放在哪").assertIsDisplayed()
        rule.onNodeWithText("跟文章放在一起").assertIsDisplayed()
        rule.onNodeWithText("放进 source/images").assertIsDisplayed()
        rule.onNodeWithText("example-owner").assertIsDisplayed()
        rule.onNodeWithText("example-app").assertIsDisplayed()
        rule.onNodeWithText("发布后触发自动构建").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("工作流文件名").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithText("设置").assertIsDisplayed()
        rule.onNodeWithText("账户信息").assertIsDisplayed()
        rule.onNodeWithText("外观").assertIsDisplayed()
        rule.onNodeWithText("检查更新").assertIsDisplayed()
        rule.onNodeWithText("GitHub 令牌").assertDoesNotExist()
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
                    showUpdate = false,
                )
            }
        }
        rule.onNodeWithText("添加账号").assertIsDisplayed()
        rule.onNodeWithText("GitHub 令牌").assertIsDisplayed()
        rule.onNodeWithText("仓库所有者").assertIsDisplayed()
        rule.onNodeWithText("仓库名").assertIsDisplayed()
        rule.onNodeWithText("分支").assertIsDisplayed()
        rule.onNodeWithText("文章目录").assertIsDisplayed()
        rule.onNodeWithText("图片放在哪").assertIsDisplayed()
        rule.onNodeWithText("发布后触发自动构建").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("工作流文件名").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("检查更新").assertDoesNotExist()
        rule.onNodeWithText("账户信息").assertDoesNotExist()
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
                    onNew = { writing = true },
                    onOpenDraft = { openedDraft = true },
                    onCatalog = { _, _ -> },
                    bottomBar = { MobBottomBar(MainTab.Home) {} },
                )
            }
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText("还没有文章")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("手机上的草稿").assertIsDisplayed()
        rule.onNodeWithText("未完成的草稿").performClick()
        rule.runOnIdle { assertTrue(openedDraft) }
        rule.onNodeWithText("仓库里的文章").assertIsDisplayed()
        rule.onAllNodesWithText("设置").assertCountEquals(0)
        rule.onNodeWithContentDescription("写文章").performClick()
        rule.runOnIdle { assertTrue(writing) }
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(2)
    }
}

private class EmptyDirectory : HttpTransport {
    override fun execute(request: HttpRequest): HttpResponse = HttpResponse(200, "[]".toByteArray())
}
