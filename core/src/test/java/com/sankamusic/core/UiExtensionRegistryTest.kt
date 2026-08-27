package com.sankamusic.core

import com.sankamusic.core.api.HomeSection
import com.sankamusic.core.api.PlayerAction
import com.sankamusic.core.api.SettingsEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiExtensionRegistryTest {

    @Test
    fun `register and list home sections sorted by priority`() {
        val registry = UiExtensionRegistry()
        registry.registerHomeSection(HomeSection(id = "a", title = "A", priority = 100))
        registry.registerHomeSection(HomeSection(id = "b", title = "B", priority = 10))
        assertEquals(listOf("b", "a"), registry.homeSections().map { it.id })
    }

    @Test
    fun `duplicate registration is rejected`() {
        val registry = UiExtensionRegistry()
        registry.registerHomeSection(HomeSection(id = "a", title = "A"))
        try {
            registry.registerHomeSection(HomeSection(id = "a", title = "A2"))
            assertTrue("un doublon aurait dû être refusé", false)
        } catch (_: IllegalStateException) {
            // attendu
        }
    }

    @Test
    fun `remove deletes the extension`() {
        val registry = UiExtensionRegistry()
        registry.registerHomeSection(HomeSection(id = "a", title = "A"))
        registry.registerSettingsEntry(SettingsEntry(id = "s", title = "S"))
        registry.registerPlayerAction(PlayerAction(id = "p", label = "P"))

        registry.removeHomeSection("a")
        registry.removeSettingsEntry("s")
        registry.removePlayerAction("p")

        assertTrue(registry.homeSections().isEmpty())
        assertTrue(registry.settingsEntries().isEmpty())
        assertTrue(registry.playerActions().isEmpty())
    }

    @Test
    fun `lists are independent per type`() {
        val registry = UiExtensionRegistry()
        registry.registerHomeSection(HomeSection(id = "a", title = "A"))
        registry.registerSettingsEntry(SettingsEntry(id = "s", title = "S"))
        registry.registerPlayerAction(PlayerAction(id = "p", label = "P"))

        assertEquals(1, registry.homeSections().size)
        assertEquals(1, registry.settingsEntries().size)
        assertEquals(1, registry.playerActions().size)
    }
}
