package dev.mobwriter.app.core

import dev.mobwriter.app.core.model.BlogSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsTest {
    @Test
    fun newInstallRequiresTheUsersOwnRepositoryAndToken() {
        val settings = BlogSettings()
        assertEquals("", settings.owner)
        assertEquals("", settings.repo)
        assertEquals("", settings.token)
    }
}
