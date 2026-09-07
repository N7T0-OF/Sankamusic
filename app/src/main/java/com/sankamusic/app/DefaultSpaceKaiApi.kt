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
import com.sankamusic.core.api.ThemeColorSource
import com.sankamusic.core.api.ThemeMode
import com.sankamusic.core.api.UiExtensionApi
import com.sankamusic.core.api.model.UnifiedAlbum
import com.sankamusic.core.api.model.UnifiedPlaylist
import com.sankamusic.core.api.model.UnifiedTrack
import com.sankamusic.core.ThemeEngine
import com.sankamusic.core.UiExtensionRegistry
import com.sankamusic.core.player.PlayerController
import com.sankamusic.core.player.PlayerStatus
import com.sankamusic.core.settings.StringSettings
import com.sankamusic.core.settings.TypedSettings
import com.sankamusic.core.settings.themeColorSourcePreference
import com.sankamusic.core.settings.themeModePreference
import com.sankamusic.core.settings.themeSeedColorFromPreferenceValue
import com.sankamusic.core.settings.themeSeedColorPreference
import com.sankamusic.core.settings.themeSeedColorToPreferenceValue

/**
 * Implémentation squelette de la [SpaceKaiApi] — permet de démarrer le
 * framework (injection + plugins) avant que le Core complet n'existe.
 *
 * ⚠️ TODO(Phase 2) : chaque service sera branché sur la vraie implémentation
 * (player, library, navigation Compose…) après l'audit (Phase 1).
 */
class DefaultSpaceKaiApi(
    private val networkApi: NetworkApi = HttpNetworkApi(),
    /**
     * Store réel des préférences (SharedPreferences en production —
     * [SharedPreferencesSettings]) ; `null` → mémoire (tests, prototype).
     * Ferme le gap « persistance réelle » de docs/MIGRATION.md étape 7.
     */
    persistentStore: StringSettings? = null,
) : SpaceKaiApi {

    private val uiRegistry = UiExtensionRegistry()

    /** Moteur de thèmes (mode, source de couleur, seed) — état exposé à l'UI. */
    val themeEngine = ThemeEngine()

    private val settingsStore: StringSettings = persistentStore ?: run {
        val map = mutableMapOf<String, String>()
        object : StringSettings {
            override fun get(key: String): String? = map[key]

            override fun set(key: String, value: String) {
                map[key] = value
            }
        }
    }

    override val uiExtensions: UiExtensionApi = uiRegistry

    /** Accès typé aux préférences (étape 7 — docs/MIGRATION.md), partagé avec [settings]. */
    val typedSettings = TypedSettings(settingsStore)

    init {
        // Restauration des réglages de thème persistés (mode, source, graine).
        themeEngine.setMode(typedSettings.get(themeModePreference))
        val source = typedSettings.get(themeColorSourcePreference)
        if (source == ThemeColorSource.CUSTOM) {
            // CUSTOM sans graine valide → échec propre du moteur, état inchangé.
            themeSeedColorFromPreferenceValue(typedSettings.get(themeSeedColorPreference))
                ?.let { seed -> themeEngine.setColorSource(ThemeColorSource.CUSTOM, seed) }
        } else {
            themeEngine.setColorSource(source)
        }
    }

    /**
     * Contrôleur de lecture (étape 4 migration — docs/MIGRATION.md) : machine à
     * états pure + file d'attente. Le moteur audio réel (ExoPlayer/media3) et
     * l'UI du player le consommeront (étape 4 UI).
     */
    val playerController = PlayerController()

    override val player = object : PlayerApi {
        override val isPlaying: Boolean
            get() = playerController.snapshot().status == PlayerStatus.PLAYING

        override suspend fun play(track: UnifiedTrack) {
            playerController.play(track)
        }

        override suspend fun pause() {
            playerController.pause()
        }

        override suspend fun resume() {
            playerController.resume()
        }
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
        // Application des tokens → MaterialTheme Compose : assurée par l'UI du
        // Core (Phase 2/3, docs/THEME_SYSTEM.md § 5/6). Le moteur retient l'état.
        override suspend fun apply(tokens: SpaceKaiThemeTokens) = Unit

        override suspend fun setMode(mode: ThemeMode) {
            themeEngine.setMode(mode)
            typedSettings.set(themeModePreference, mode)
        }

        override suspend fun setColorSource(source: ThemeColorSource, customSeedColor: Long?) {
            // Persistance uniquement si le moteur accepte le changement
            // (CUSTOM sans graine → échec propre, état et préférence inchangés).
            themeEngine.setColorSource(source, customSeedColor)
                .onSuccess {
                    typedSettings.set(themeColorSourcePreference, source)
                    if (source == ThemeColorSource.CUSTOM && customSeedColor != null) {
                        typedSettings.set(
                            themeSeedColorPreference,
                            themeSeedColorToPreferenceValue(customSeedColor),
                        )
                    }
                }
        }
    }

    override val settings = object : SettingsApi {
        override suspend fun get(key: String): String? = settingsStore.get(key)

        override suspend fun set(key: String, value: String) {
            settingsStore.set(key, value)
        }
    }

    override val downloads = object : DownloadsApi {
        override suspend fun download(track: UnifiedTrack): Result<Unit> = Result.success(Unit)
    }

    override val network: NetworkApi = networkApi
}
