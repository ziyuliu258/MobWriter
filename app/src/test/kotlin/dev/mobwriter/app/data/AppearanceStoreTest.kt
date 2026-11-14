package dev.mobwriter.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.mobwriter.app.core.model.AccountSession
import dev.mobwriter.app.core.model.Appearance
import dev.mobwriter.app.core.model.BlogAccount
import dev.mobwriter.app.core.model.BlogSettings
import dev.mobwriter.app.core.model.FontStep
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
    fun missingOrUnknownValuesUseTheCurrentSize() {
        val prefs = context.getSharedPreferences("appearance-blank", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertEquals(Appearance(), AppearanceStore(prefs).load())
        prefs.edit().putString("ui", "nope").putString("article", FontStep.Large.name).commit()
        val loaded = AppearanceStore(prefs).load()
        assertEquals(FontStep.Standard, loaded.ui)
        assertEquals(FontStep.Large, loaded.article)
    }

    @Test
    fun choicesSurviveARestartAndAnAccountSave() {
        val store = AppearanceStore(context)
        val chosen = Appearance(ui = FontStep.Small, article = FontStep.ExtraLarge)
        store.save(chosen)
        assertEquals(chosen, AppearanceStore(context).load())

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
}
