package dev.mobwriter.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.mobwriter.app.core.model.AccountSession
import dev.mobwriter.app.core.model.Appearance
import dev.mobwriter.app.core.model.BlogAccount
import dev.mobwriter.app.core.model.BlogSettings
import dev.mobwriter.app.core.model.BackgroundChoice
import dev.mobwriter.app.core.model.FontScale
import dev.mobwriter.app.core.model.PaletteChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppearanceStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearAppearance() {
        context.getSharedPreferences(AppearanceStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun missingOrUnknownValuesUseTheStandardSize() {
        val prefs = context.getSharedPreferences("appearance-blank", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertEquals(Appearance(), AppearanceStore(prefs).load())
        prefs.edit().putString("ui", "nope").putString("article", "120").commit()
        val loaded = AppearanceStore(prefs).load()
        assertEquals(FontScale.STANDARD, loaded.ui.percent)
        assertEquals(120, loaded.article.percent)
    }

    @Test
    fun choicesSurviveARestartAndAnAccountSave() {
        val store = AppearanceStore(context)
        val chosen = Appearance(ui = FontScale.of(75), article = FontScale.of(125))
        store.save(chosen)
        assertEquals(chosen, AppearanceStore(context).load())
        val saved = context.getSharedPreferences(AppearanceStore.FILE, Context.MODE_PRIVATE)
        assertEquals("75", saved.getString("ui", null))
        assertEquals("cinnabar", saved.getString("palette", null))
        assertEquals("none", saved.getString("background", null))

        val accountPrefs = context.getSharedPreferences("appearance-account-split", Context.MODE_PRIVATE)
        accountPrefs.edit().clear().commit()
        AccountStore(accountPrefs).save(
            AccountSession(accounts = listOf(BlogAccount("a", BlogSettings(owner = "alpha", repo = "blog", token = "token-alpha-kept"))), activeId = "a"),
        )
        assertFalse(accountPrefs.contains("ui"))
        assertFalse(accountPrefs.contains("article"))
        assertEquals(chosen, AppearanceStore(context).load())
        assertEquals("token-alpha-kept", AccountStore(accountPrefs).load().active?.settings?.token)
    }

    @Test
    fun paletteAndBackgroundSurviveARestart() {
        val store = AppearanceStore(context)
        val chosen = Appearance(
            palette = PaletteChoice.custom(0xC96442),
            background = BackgroundChoice.image("background-1.jpg") ?: BackgroundChoice.plain(),
        )
        store.save(chosen)
        val loaded = AppearanceStore(context).load()
        assertEquals(0xC96442, loaded.palette.customAccent)
        assertEquals("background-1.jpg", loaded.background.imageName)
        assertEquals(FontScale.STANDARD, loaded.ui.percent)
    }
}
