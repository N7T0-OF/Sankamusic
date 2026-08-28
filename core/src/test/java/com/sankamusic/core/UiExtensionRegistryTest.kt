package com.sankamusic.core

import com.sankamusic.core.api.HomeSection
import com.sankamusic.core.api.NavigationTab
import com.sankamusic.core.api.PlayerAction
import com.sankamusic.core.api.SettingsEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiExtensionRegistryTest {

    @Test
    fun `register and list navigation tabs sorted by priority`() {
        val registry = UiExtensionRegistry()
        registry.registerNavigationTab(NavigationTab(id = "home", label = "Accueil", priority = 0))
        registry.registerNavigationTab(NavigationTab(id = "library", label = "Bibliothèque", priority = 10))
        registry.registerNavigationTab(NavigationTab(id = "search", label = "Recherche", priority = 20))
        assertEquals(
            listOf("home", "library", "search"),
            registry.navigationTabs().map { it.id },
        )
    }

    @Test
    fun `navigation tab duplicate registration is rejected`() {
        val registry = UiExtensionRegistry()
        registry.registerNavigationTab(NavigationTab(id = "home", label = "Accueil"))
        try {
            registry.registerNavigationTab(NavigationTab(id = "home", label = "Accueil 2"))
            assertTrue("un doublon d'onglet aurait dû être refusé", false)
        } catch (_: IllegalStateException) {
            // attendu
        }
    }

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
        registry.registerNavigationTab(NavigationTab(id = "n", label = "N"))
        registry.registerHomeSection(HomeSection(id = "a", title = "A"))
        registry.registerSettingsEntry(SettingsEntry(id = "s", title = "S"))
        registry.registerPlayerAction(PlayerAction(id = "p", label = "P"))

        registry.removeNavigationTab("n")
        registry.removeHomeSection("a")
        registry.removeSettingsEntry("s")
        registry.removePlayerAction("p")

        assertTrue(registry.navigationTabs().isEmpty())
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
