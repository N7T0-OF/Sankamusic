package com.sankamusic.core.api

import com.sankamusic.core.api.model.UnifiedAlbum
import com.sankamusic.core.api.model.UnifiedPlaylist
import com.sankamusic.core.api.model.UnifiedTrack

/**
 * API publique stable de la plateforme (voir docs/PLUGIN_SYSTEM.md § 5).
 *
 * Les plugins accèdent aux services via [SpaceKaiApi.instance] :
 *
 * ```kotlin
 * SpaceKaiApi.instance.player.play(track)
 * SpaceKaiApi.instance.playlists.all()
 * ```
 *
 * Règles : API stable, détails internes cachés, changements uniquement en version majeure.
 */
interface SpaceKaiApi {
    val player: PlayerApi
    val library: LibraryApi
    val playlists: PlaylistApi
    val navigation: NavigationApi
    val theme: ThemeApi
    val settings: SettingsApi
    val downloads: DownloadsApi
    val network: NetworkApi
    val uiExtensions: UiExtensionApi

    companion object {
        /**
         * Instance injectée par le Core au démarrage de l'application.
         * Un plugin ne doit être activé qu'après l'injection (Phase 2).
         */
        lateinit var instance: SpaceKaiApi

        /** Vrai si [instance] a été injectée (permet aux plugins de se garder d'y accéder trop tôt). */
        fun isInitialized(): Boolean = ::instance.isInitialized
    }
}

// ── Services exposés (squelettes minimaux — à étoffer après l'audit) ──

interface PlayerApi {
    val isPlaying: Boolean
    suspend fun play(track: UnifiedTrack)
    suspend fun pause()
    suspend fun resume()
}

interface LibraryApi {
    suspend fun tracks(): List<UnifiedTrack>
    suspend fun albums(): List<UnifiedAlbum>
}

interface PlaylistApi {
    suspend fun all(): List<UnifiedPlaylist>
}

interface NavigationApi {
    /** Ajoute une entrée de navigation (permission NAVIGATION_MODIFY requise). */
    suspend fun addSection(id: String, label: String)
    suspend fun removeSection(id: String)
}

interface ThemeApi {
    /** Applique des tokens de thème (permission THEME_MODIFY requise). */
    suspend fun apply(tokens: SpaceKaiThemeTokens)
}

interface SettingsApi {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
}

interface DownloadsApi {
    suspend fun download(track: UnifiedTrack): Result<Unit>
}

interface NetworkApi {
    suspend fun get(url: String): Result<String>
}
