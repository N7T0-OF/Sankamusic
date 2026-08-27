package com.sankamusic.app

import com.sankamusic.core.api.DownloadsApi
import com.sankamusic.core.api.LibraryApi
import com.sankamusic.core.api.NavigationApi
import com.sankamusic.core.api.NetworkApi
import com.sankamusic.core.api.PlayerApi
import com.sankamusic.core.api.PlaylistApi
import com.sankamusic.core.api.SettingsApi
import com.sankamusic.core.api.SpaceKaiApi
import com.sankamusic.core.api.SpaceKaiThemeTokens
import com.sankamusic.core.api.ThemeApi
import com.sankamusic.core.api.UiExtensionApi
import com.sankamusic.core.api.model.UnifiedAlbum
import com.sankamusic.core.api.model.UnifiedPlaylist
import com.sankamusic.core.api.model.UnifiedTrack
import com.sankamusic.core.UiExtensionRegistry

/**
 * Implémentation squelette de la [SpaceKaiApi] — permet de démarrer le
 * framework (injection + plugins) avant que le Core complet n'existe.
 *
 * ⚠️ TODO(Phase 2) : chaque service sera branché sur la vraie implémentation
 * (player, library, navigation Compose…) après l'audit (Phase 1).
 */
class DefaultSpaceKaiApi(
    private val networkApi: NetworkApi = HttpNetworkApi(),
) : SpaceKaiApi {

    private val uiRegistry = UiExtensionRegistry()

    override val uiExtensions: UiExtensionApi = uiRegistry

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
        private val store = mutableMapOf<String, String>()

        override suspend fun get(key: String): String? = store[key]

        override suspend fun set(key: String, value: String) {
            store[key] = value
        }
    }

    override val downloads = object : DownloadsApi {
        override suspend fun download(track: UnifiedTrack): Result<Unit> = Result.success(Unit)
    }

    override val network: NetworkApi = networkApi
}
