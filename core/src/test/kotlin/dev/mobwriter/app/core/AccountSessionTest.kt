package dev.mobwriter.app.core

import dev.mobwriter.app.core.model.AccountSession
import dev.mobwriter.app.core.model.BlogSettings
import dev.mobwriter.app.core.model.connectionLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountSessionTest {
    @Test
    fun switchingKeepsEveryAccountsToken() {
        val alpha = BlogSettings(owner = "alpha", repo = "blog", token = "token-alpha-kept")
        val beta = BlogSettings(owner = "beta", repo = "notes", branch = "docs", token = "token-beta-kept")
        val session = AccountSession()
            .add(alpha, "a")
            .add(beta, "b")
        assertEquals("b", session.activeId)
        assertEquals("token-beta-kept", session.active?.settings?.token)
        val back = session.switchTo("a")
        assertEquals("alpha", back.active?.settings?.owner)
        assertEquals("token-alpha-kept", back.active?.settings?.token)
        assertEquals("token-beta-kept", back.accounts.first { it.id == "b" }.settings.token)
        val signedOut = back.signOut()
        assertNull(signedOut.active)
        assertEquals(2, signedOut.accounts.size)
        assertEquals("token-alpha-kept", signedOut.accounts.first { it.id == "a" }.settings.token)
        assertEquals("token-beta-kept", signedOut.switchTo("b").active?.settings?.token)
    }

    @Test
    fun editingTheCurrentAccountDoesNotReplaceTheOthers() {
        val session = AccountSession()
            .add(BlogSettings(owner = "alpha", repo = "blog", token = "token-alpha-kept"), "a")
            .add(BlogSettings(owner = "beta", repo = "notes", token = "token-beta-kept"), "b")
            .switchTo("a")
        val edited = session.replaceActive(
            BlogSettings(owner = "alpha", repo = "blog", token = "token-alpha-kept", postsPath = "source/_posts"),
            "unused",
        )
        assertEquals("a", edited.activeId)
        assertEquals("source/_posts", edited.active?.settings?.postsPath)
        assertEquals("token-beta-kept", edited.accounts.first { it.id == "b" }.settings.token)
        assertEquals("alpha/blog", edited.active?.settings?.connectionLabel())
    }

    @Test
    fun removingOneAccountLeavesTheOtherTokenAndDraftIdentity() {
        val session = AccountSession()
            .add(BlogSettings(owner = "alpha", repo = "blog", token = "token-alpha-kept"), "a")
            .add(BlogSettings(owner = "beta", repo = "notes", token = "token-beta-kept"), "b")
        val removed = session.remove("a")
        assertEquals("b", removed.activeId)
        assertEquals(listOf("b"), removed.accounts.map { it.id })
        assertEquals("token-beta-kept", removed.active?.settings?.token)
        val cleared = session.switchTo("b").remove("b")
        assertNull(cleared.activeId)
        assertEquals("token-alpha-kept", cleared.accounts.single().settings.token)
    }

    @Test
    fun unknownSwitchAndEmptyLabelStayPut() {
        val session = AccountSession().add(BlogSettings(token = "token-alpha-kept"), "a")
        assertEquals(session, session.switchTo("missing"))
        assertEquals("未命名账号", BlogSettings().connectionLabel())
        val signedOut = session.signOut()
        assertNull(signedOut.active)
        assertEquals(signedOut, signedOut.signOut())
        assertEquals("token-alpha-kept", signedOut.accounts.single().settings.token)
    }
}
