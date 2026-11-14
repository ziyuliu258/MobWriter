package dev.mobwriter.app.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import dev.mobwriter.app.core.model.Appearance
import dev.mobwriter.app.core.model.ArticleType
import dev.mobwriter.app.core.model.BlogSettings
import dev.mobwriter.app.core.model.FontStep
import dev.mobwriter.app.data.AppearanceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h640dp-xxhdpi")
class AppearanceSettingsTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearAppearance() {
        context.getSharedPreferences(AppearanceStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun standardTypographyMatchesTodayAndTheTwoScalesStaySeparate() {
        val standard = typographyFor(FontStep.Standard)
        val large = typographyFor(FontStep.Large)
        assertEquals(22f, standard.titleLarge.fontSize.value, 0.01f)
        assertEquals(28f, standard.headlineMedium.fontSize.value, 0.01f)
        assertEquals(17f, standard.bodyLarge.fontSize.value, 0.01f)
        assertEquals(FontFamily.Serif, large.titleLarge.fontFamily)
        assertEquals(22f * FontStep.Large.scale, large.titleLarge.fontSize.value, 0.01f)
        assertEquals(30f * FontStep.Large.scale, large.titleLarge.lineHeight.value, 0.01f)
        assertEquals(standard.labelMedium.fontSize.value * FontStep.Large.scale, large.labelMedium.fontSize.value, 0.01f)
        assertEquals(standard.labelLarge.fontSize.value * FontStep.Large.scale, large.labelLarge.fontSize.value, 0.01f)
        assertEquals(standard.bodyMedium.fontSize.value * FontStep.Large.scale, large.bodyMedium.fontSize.value, 0.01f)
        assertEquals(standard.headlineSmall.fontSize.value * FontStep.Large.scale, large.headlineSmall.fontSize.value, 0.01f)
        assertEquals(17f, articleBodyStyle(FontStep.Standard).fontSize.value, 0.01f)
        assertEquals(17f * FontStep.Small.scale, articleBodyStyle(FontStep.Small).fontSize.value, 0.01f)
        assertEquals(28f * FontStep.ExtraLarge.scale, articleTitleStyle(FontStep.ExtraLarge).fontSize.value, 0.01f)
        assertEquals(
            ArticleType.headingSp(3) * FontStep.Large.scale,
            articleHeadingSize(3, FontStep.Large).value,
            0.01f,
        )
        assertEquals(1.21f, ArticleType.FORMULA_EM, 0.0001f)
        assertEquals(ArticleType.scaled(ArticleType.BODY_SP, FontStep.Large) * 1.21f, ArticleType.formulaSp(FontStep.Large), 0.0001f)
    }

    @Test
    fun appearanceRowOpensTwoIndependentSizesThatApplyImmediately() {
        val appearance = mutableStateOf(Appearance())
        var saved: BlogSettings? = null
        val store = AppearanceStore(context)
        rule.setContent {
            val current by appearance
            MobWriterTheme(current) {
                SetupScreen(
                    initial = BlogSettings(owner = "example-owner", repo = "example-app", token = "token"),
                    onSave = { saved = it },
                    onClose = {},
                    appearance = current,
                    onAppearance = {
                        appearance.value = it
                        store.save(it)
                    },
                )
            }
        }
        rule.onNodeWithText("外观").performClick()
        rule.onNodeWithText("界面字体大小").assertIsDisplayed()
        rule.onNodeWithText("文章字体大小").assertIsDisplayed()
        rule.onNodeWithText("界面上的文字会按这个大小显示。").assertIsDisplayed()
        rule.onNodeWithText("文章里的句子会按这个大小显示。").assertIsDisplayed()
        rule.onNodeWithText("检查更新").assertDoesNotExist()
        rule.onNodeWithText("账户信息").assertDoesNotExist()
        rule.onNodeWithText("GitHub 令牌").assertDoesNotExist()
        rule.onNodeWithText("保存").assertDoesNotExist()
        rule.onNodeWithContentDescription("保存").assertDoesNotExist()
        rule.onNode(hasContentDescription("界面字体大小 标准") and isSelected()).assertExists()
        rule.onNode(hasContentDescription("文章字体大小 标准") and isSelected()).assertExists()

        rule.onNodeWithContentDescription("文章字体大小 特大").performClick()
        rule.onNode(hasContentDescription("文章字体大小 特大") and isSelected()).assertExists()
        rule.onNode(hasContentDescription("文章字体大小 标准") and isNotSelected()).assertExists()
        rule.onNode(hasContentDescription("界面字体大小 标准") and isSelected()).assertExists()
        rule.onNode(hasContentDescription("界面字体大小 特大") and isNotSelected()).assertExists()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithText("界面 标准 · 文章 特大").assertIsDisplayed()
        rule.onNodeWithText("检查更新").assertIsDisplayed()
        rule.onNodeWithText("账户信息").assertIsDisplayed()

        rule.onNodeWithText("外观").performClick()
        rule.onNodeWithContentDescription("界面字体大小 较小").performClick()
        rule.onNode(hasContentDescription("界面字体大小 较小") and isSelected()).assertExists()
        rule.onNode(hasContentDescription("文章字体大小 特大") and isSelected()).assertExists()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithText("界面 较小 · 文章 特大").assertIsDisplayed()
        rule.onNodeWithText("检查更新").performClick()
        rule.onNodeWithText("界面字体大小").assertDoesNotExist()
        rule.onNodeWithText("账户信息").assertIsDisplayed()
        rule.onNodeWithText("外观").assertIsDisplayed()

        val stored = AppearanceStore(context).load()
        assertEquals(FontStep.Smaller, stored.ui)
        assertEquals(FontStep.ExtraLarge, stored.article)
        assertNull(saved)
    }

    @Test
    fun aLargerInterfaceSizeLeavesTheAvatarAtItsCurrentSize() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).asImageBitmap()
        rule.setContent {
            MobWriterTheme(Appearance(ui = FontStep.ExtraLarge)) {
                MeScreen(onOpenSettings = {}, author = "Example", avatar = bitmap) {
                    MobBottomBar(MainTab.Me) {}
                }
            }
        }
        val width = rule.onNodeWithContentDescription("头像").fetchSemanticsNode().boundsInRoot.width
        assertEquals(216f, width, 1.5f)
    }
}
