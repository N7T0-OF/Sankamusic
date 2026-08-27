package com.sankamusic.plugins.hellospacekai

import com.sankamusic.core.PluginEngine
import com.sankamusic.core.api.DownloadsApi
import com.sankamusic.core.api.HomeSection
import com.sankamusic.core.api.LibraryApi
import com.sankamusic.core.api.NavigationApi
import com.sankamusic.core.api.NetworkApi
import com.sankamusic.core.api.PlayerAction
import com.sankamusic.core.api.PlayerApi
import com.sankamusic.core.api.PlaylistApi
import com.sankamusic.core.api.PluginState
import com.sankamusic.core.api.SettingsApi
import com.sankamusic.core.api.SettingsEntry
import com.sankamusic.core.api.SpaceKaiApi
import com.sankamusic.core.api.SpaceKaiThemeTokens
import com.sankamusic.core.api.ThemeApi
import com.sankamusic.core.api.UiExtensionApi
import com.sankamusic.core.api.model.UnifiedAlbum
import com.sankamusic.core.api.model.UnifiedPlaylist
import com.sankamusic.core.api.model.UnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validation du plugin d'exemple à travers le moteur (Phase 3) :
 * manifest valide, cycle de vie complet, et enregistrement des extensions UI
 * quand la SpaceKai API est injectée.
 */
class HelloSpaceKaiPluginTest {

    @Test
    fun `manifest is valid`() {
        assertTrue(HelloSpaceKaiPlugin().manifest.validationErrors().isEmpty())
    }

    @Test
    fun `full lifecycle works through the engine`() {
        val engine = PluginEngine()
        val plugin = HelloSpaceKaiPlugin()

        assertTrue(engine.register(plugin).isSuccess)
        assertEquals(PluginState.INSTALLED, engine.state(HelloSpaceKaiPlugin.ID))

        assertTrue(engine.enable(HelloSpaceKaiPlugin.ID).isSuccess)
        assertEquals(PluginState.ENABLED, engine.state(HelloSpaceKaiPlugin.ID))

        assertTrue(engine.disable(HelloSpaceKaiPlugin.ID).isSuccess)
        assertEquals(PluginState.DISABLED, engine.state(HelloSpaceKaiPlugin.ID))

        assertTrue(engine.unload(HelloSpaceKaiPlugin.ID).isSuccess)
        assertNull(engine.state(HelloSpaceKaiPlugin.ID))
    }

    @Test
    fun `enable without injected API does not crash`() {
        // SpaceKaiApi.instance n'est pas injecté dans ce test : le plugin doit
        // se garder d'y accéder (isolation) et rester activable.
        val engine = PluginEngine()
        engine.register(HelloSpaceKaiPlugin())
        assertTrue(engine.enable(HelloSpaceKaiPlugin.ID).isSuccess)
        assertEquals(PluginState.ENABLED, engine.state(HelloSpaceKaiPlugin.ID))
    }

    @Test
    fun `enable registers UI extensions and disable removes them`() {
        val api = FakeSpaceKaiApi()
        SpaceKaiApi.instance = api

        val engine = PluginEngine()
        engine.register(HelloSpaceKaiPlugin())

        assertTrue(engine.enable(HelloSpaceKaiPlugin.ID).isSuccess)
        assertEquals(1, api.sections.size)
        assertEquals("Hello SpaceKai", api.sections.first().title)
        assertEquals(1, api.entries.size)
        assertEquals(HelloSpaceKaiPlugin.SETTINGS_ENTRY_ID, api.entries.first().id)

        assertTrue(engine.disable(HelloSpaceKaiPlugin.ID).isSuccess)
        assertTrue(api.sections.isEmpty())
        assertTrue(api.entries.isEmpty())
    }

    /** Fausse implémentation minimale de la SpaceKai API (test uniquement). */
    private class FakeSpaceKaiApi : SpaceKaiApi {
        val sections = mutableListOf<HomeSection>()
        val entries = mutableListOf<SettingsEntry>()

        override val uiExtensions = object : UiExtensionApi {
            override fun registerHomeSection(section: HomeSection) {
                sections += section
            }

            override fun removeHomeSection(id: String) {
                sections.removeAll { it.id == id }
            }

            override fun registerSettingsEntry(entry: SettingsEntry) {
                entries += entry
            }

            override fun removeSettingsEntry(id: String) {
                entries.removeAll { it.id == id }
            }

            override fun registerPlayerAction(action: PlayerAction) = Unit

            override fun removePlayerAction(id: String) = Unit
        }

        override val player = object : PlayerApi {
            override val isPlaying: Boolean get() = false
            override suspend fun play(track: UnifiedTrack) = Unit
            override suspend fun pause() = Unit
            override suspend fun resume() = Unit
        }

        override val library = object : LibraryApi {
            override suspend fun tracks(): List<UnifiedTrack> = emptyList()
            override suspend fun albums(): List<UnifiedAlbum> = emptyList()
        }

        override val playlists = object : PlaylistApi {
            override suspend fun all(): List<UnifiedPlaylist> = emptyList()
        }

        override val navigation = object : NavigationApi {
            override suspend fun addSection(id: String, label: String) = Unit
            override suspend fun removeSection(id: String) = Unit
        }

        override val theme = object : ThemeApi {
            override suspend fun apply(tokens: SpaceKaiThemeTokens) = Unit
        }

        override val settings = object : SettingsApi {
            override suspend fun get(key: String): String? = null
            override suspend fun set(key: String, value: String) = Unit
        }

        override val downloads = object : DownloadsApi {
            override suspend fun download(track: UnifiedTrack): Result<Unit> = Result.success(Unit)
        }

        override val network = object : NetworkApi {
            override suspend fun get(url: String): Result<String> =
                Result.failure(UnsupportedOperationException())
        }
    }
}
