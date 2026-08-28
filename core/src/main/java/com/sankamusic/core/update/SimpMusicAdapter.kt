package com.sankamusic.core.update

import com.sankamusic.core.api.LibraryAdapter
import com.sankamusic.core.api.MusicPlayerAdapter
import com.sankamusic.core.api.PlaylistAdapter
import com.sankamusic.core.api.SpaceKaiContracts
import com.sankamusic.core.api.UpstreamAdapter
import com.sankamusic.core.api.UpstreamInfo
import com.sankamusic.core.api.model.UnifiedPlaylist
import com.sankamusic.core.api.model.UnifiedTrack

/**
 * Adapter v2 de la base upstream SimpMusic (docs/UPSTREAM_SYSTEM.md).
 *
 * v2 : la source SimpMusic **2.0.0** a été auditée le 2026-08-28 (snapshot
 * local + repo maxrave-dev/core) — docs/UPSTREAM_SYSTEM.md § 8bis. L'audit
 * confirme que les 6 points d'intégration des contrats SpaceKai existent
 * toujours dans l'architecture 2.0.0 (restructuration KMP, moteur déplacé
 * dans le sous-module maxrave-dev/core/media/media3) :
 *
 *   - navigation   : BottomNavScreen (enum) + onglets conditionnels ;
 *   - thème        : AppTheme(themeMode, themeColorSource, customThemeColor,
 *                    liquidGlassEnabled) — même famille + param liquid glass ;
 *   - orientation  : FullscreenPlayer.android.kt force LANDSCAPE + restaure
 *                    l'orientation d'origine (identique à l'ancien code) ;
 *   - player       : FullscreenPlayer/NowPlayingScreen + moteur media3 ;
 *   - haptique     : ajout SpaceKai (la base n'en a pas — rien à casser) ;
 *   - dynamic color: platformDynamicColorScheme + isAmoled (identique).
 *
 * Plage couverte par cet Adapter : 2.0.x (stable). Hors plage → l'Adapter
 * doit être re-vérifié avant toute release (UPSTREAM_SYSTEM.md § 5 — jamais
 * de remplacement automatique non testé).
 *
 * ⚠️ Les sous-adaptateurs (player / library / playlists) ne sont PAS encore
 * reliés : la base SimpMusic n'est pas intégrée comme dépendance (Phase 2,
 * audit des classes réelles — UPSTREAM_SYSTEM.md § 6). Ils lèvent une erreur
 * explicite plutôt que de simuler un comportement.
 */
class SimpMusicAdapter : UpstreamAdapter {

    override val info = UpstreamInfo(
        repository = "maxrave-dev/SimpMusic",
        version = "2.0.0",
        adapterVersion = 2,
        compatibility = "2.0.x",
    )

    override val player: MusicPlayerAdapter = NotLinked("player")
    override val library: LibraryAdapter = NotLinked("library")
    override val playlists: PlaylistAdapter = NotLinked("playlists")

    override fun isCompatibleWith(upstreamVersion: String): Boolean {
        val version = SemVer.parse(upstreamVersion) ?: return false
        // Stable uniquement (jamais de pré-release) et couvert par la plage 2.0.x
        // (version auditée le 2026-08-28 — docs/UPSTREAM_SYSTEM.md § 8bis).
        return version.prerelease == null && version.major == 2 && version.minor == 0
    }

    /**
     * Contrats SpaceKai fournis par cet Adapter (docs/FEATURE_MANIFEST.md § 3) :
     * les 6 fonctionnalités migrées (étapes 1-6). Si une fonctionnalité du
     * manifest déclare un contrat absent d'ici, elle est désactivée — le
     * compilateur et les contract tests restent le garde-fou (jamais de fausse
     * compatibilité silencieuse).
     */
    override fun satisfiesContract(contractId: String): Boolean =
        contractId in SpaceKaiContracts.SIMPMUSIC_ADAPTER_V1

    /** Sous-adaptateur non relié : échec explicite, jamais de comportement simulé. */
    private class NotLinked(private val what: String) :
        MusicPlayerAdapter, LibraryAdapter, PlaylistAdapter {

        override suspend fun play(track: UnifiedTrack): Unit = notLinked()
        override suspend fun pause(): Unit = notLinked()
        override suspend fun resume(): Unit = notLinked()
        override val isPlaying: Boolean get() = notLinked()
        override suspend fun tracks(): List<UnifiedTrack> = notLinked()
        override suspend fun playlists(): List<UnifiedPlaylist> = notLinked()

        private fun notLinked(): Nothing = throw NotImplementedError(
            "Sous-adaptateur « $what » non relié : la base SimpMusic n'est pas encore " +
                "intégrée comme dépendance (Phase 2 — docs/UPSTREAM_SYSTEM.md).",
        )
    }
}