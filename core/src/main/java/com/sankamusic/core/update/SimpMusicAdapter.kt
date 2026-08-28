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
 * Adapter v1 de la base upstream SimpMusic (docs/UPSTREAM_SYSTEM.md).
 *
 * Déclare la version de base INTÉGRÉE (v1.7.0, vérifiée le 2026-08-27 contre
 * l'API GitHub réelle — docs/UPSTREAM_SYSTEM.md § 8) et la plage de
 * compatibilité couverte par cet Adapter (1.7.x). C'est la source unique de
 * vérité de l'état upstream : l'[UpdateEngine] ne connaît QUE cet Adapter.
 *
 * ⚠️ La sortie de SimpMusic 2.0.0 (2026-08-28) a été détectée par le workflow
 * upstream ; son architecture a été auditée ([SimpMusicAdapterV2]) mais les
 * plages du manifest n'ont PAS encore été étendues : l'extension ne se fera
 * qu'après validation des contract tests V2 (docs/UPSTREAM_SYSTEM.md § 8bis).
 *
 * La compatibilité est VÉRIFIABLE : [isCompatibleWith] répond si une version
 * SimpMusic donnée est couverte. Hors plage → l'Adapter doit être vérifié/mis
 * à jour avant toute nouvelle release Sankamusic (règle conservatrice
 * UPSTREAM_SYSTEM.md § 5 — jamais de remplacement automatique non testé).
 *
 * ⚠️ Les sous-adaptateurs (player / library / playlists) ne sont PAS encore
 * reliés : la base SimpMusic n'est pas intégrée comme dépendance (Phase 2,
 * audit des classes réelles — UPSTREAM_SYSTEM.md § 6). Ils lèvent une erreur
 * explicite plutôt que de simuler un comportement.
 */
class SimpMusicAdapter : UpstreamAdapter {

    override val info = UpstreamInfo(
        repository = "maxrave-dev/SimpMusic",
        version = "1.7.0",
        adapterVersion = 1,
        compatibility = "1.7.x",
    )

    override val player: MusicPlayerAdapter = NotLinked("player")
    override val library: LibraryAdapter = NotLinked("library")
    override val playlists: PlaylistAdapter = NotLinked("playlists")

    override fun isCompatibleWith(upstreamVersion: String): Boolean {
        val version = SemVer.parse(upstreamVersion) ?: return false
        // Stable uniquement (jamais de pré-release) et couvert par la plage 1.7.x.
        return version.prerelease == null && version.major == 1 && version.minor == 7
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

/**
 * Adapter v2 de la base upstream SimpMusic 2.x (docs/UPSTREAM_SYSTEM.md § 8bis).
 *
 * Créé le 2026-08-28 comme premier TEST DE RÉSISTANCE de l'architecture : la
 * sortie de SimpMusic 2.0.0 (restructuration KMP, moteur audio extrait dans le
 * sous-module `maxrave-dev/core` — media/media3) a été auditée et les 6 points
 * d'intégration des contrats SpaceKai existent toujours :
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
 * ⚠️ RÈGLE (docs/FEATURE_MANIFEST.md § 3) : les plages du manifest ne sont
 * PAS étendues par la simple existence de cet Adapter — elles ne le seront
 * qu'après validation des CONTRACT TESTS ([AdapterContractIntegrityTest] :
 * un contrat déclaré doit avoir ses opérations réellement implémentées) et du
 * build Phase 2 contre la source 2.0.0. Jusque-là, `navigation`/`orientation`/
 * `player` restent en `1.7.x` et le workflow upstream reste vigilant.
 *
 * Les sous-adaptateurs (player / library / playlists) ne sont pas encore
 * reliés (Phase 2) — échec explicite, jamais de comportement simulé.
 */
class SimpMusicAdapterV2 : UpstreamAdapter {

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
        // Stable uniquement (jamais de pré-release) et couvert par la plage 2.0.x.
        return version.prerelease == null && version.major == 2 && version.minor == 0
    }

    /** Mêmes 6 contrats que v1 (les points d'intégration existent dans 2.0.0 — § 8bis). */
    override fun satisfiesContract(contractId: String): Boolean =
        contractId in SpaceKaiContracts.SIMPMUSIC_ADAPTER_V1

    /** Sous-adaptateur non relié : échec explicite (Phase 2). */
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