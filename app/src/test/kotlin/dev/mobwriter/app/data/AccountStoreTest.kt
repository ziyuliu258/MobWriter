package dev.mobwriter.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.mobwriter.app.core.model.AccountSession
import dev.mobwriter.app.core.model.BlogSettings
import dev.mobwriter.app.core.model.PostDocument
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccountStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun cleanDrafts() {
        File(context.filesDir, "drafts").deleteRecursively()
    }

    @Test
    fun legacyConnectionBecomesAnAccountAndKeepsItsDrafts() {
        val prefs = context.getSharedPreferences("legacy-account", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putString("owner", "alpha")
            .putString("repo", "blog")
            .putString("token", "token-alpha-kept")
            .commit()
        val loose = DraftStore(context)
        loose.save("n-old", PostDocument(title = "旧草稿"))
        val stored = File(context.filesDir, "drafts/_signed_out/n-old.json")
        val root = File(context.filesDir, "drafts")
        stored.renameTo(File(root, "n-old.json"))

        val session = AccountStore(prefs).load()
        DraftStore.migrateLooseDrafts(context, session.active?.id)

        assertEquals("alpha", session.active?.settings?.owner)
        assertEquals("token-alpha-kept", session.active?.settings?.token)
        assertEquals("旧草稿", DraftStore(context, session.active!!.id).load("n-old")?.document?.title)
        assertFalse(File(root, "n-old.json").exists())
        assertEquals(session.active?.id, AccountStore(prefs).load().activeId)
    }

    @Test
    fun savedAccountsStaySeparateAfterSignOut() {
        val prefs = context.getSharedPreferences("multi-account", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = AccountStore(prefs)
        val session = AccountSession()
            .add(BlogSettings(owner = "alpha", repo = "blog", token = "token-alpha-kept"), "a")
            .add(BlogSettings(owner = "beta", repo = "notes", token = "token-beta-kept"), "b")
        store.save(session)
        DraftStore(context, "a").save("n-a", PostDocument(title = "甲的草稿"))
        DraftStore(context, "b").save("n-b", PostDocument(title = "乙的草稿"))

        store.save(store.load().signOut())
        val signedOut = store.load()
        assertNull(signedOut.active)
        assertEquals("token-alpha-kept", signedOut.accounts.first { it.id == "a" }.settings.token)
        assertEquals("token-beta-kept", signedOut.accounts.first { it.id == "b" }.settings.token)
        assertEquals(listOf("甲的草稿"), DraftStore(context, "a").list().map { it.document.title })
        assertEquals(listOf("乙的草稿"), DraftStore(context, "b").list().map { it.document.title })
        assertEquals("alpha/blog", signedOut.switchTo("a").active?.settings?.let { "${it.owner}/${it.repo}" })
    }

    @Test
    fun deletingOneAccountKeepsTheOtherDraftsAndAvatarCache() {
        DraftStore(context, "a").save("n-a", PostDocument(title = "甲的草稿"))
        DraftStore(context, "b").save("n-b", PostDocument(title = "乙的草稿"))
        val cache = AvatarCache(context)
        cache.write("a", byteArrayOf(1, 2, 3))
        cache.write("b", byteArrayOf(4, 5, 6))
        DraftStore.deleteAccountFiles(context, "a")
        cache.delete("a")
        assertTrue(DraftStore(context, "a").list().isEmpty())
        assertEquals(listOf("乙的草稿"), DraftStore(context, "b").list().map { it.document.title })
        assertNull(cache.read("a"))
        assertArrayEquals(byteArrayOf(4, 5, 6), cache.read("b"))
    }
}
