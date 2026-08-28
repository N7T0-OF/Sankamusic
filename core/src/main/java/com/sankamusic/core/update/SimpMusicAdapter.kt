package com.sankamusic.core.update

import com.sankamusic.core.api.LibraryAdapter
import com.sankamusic.core.api.MusicPlayerAdapter
import com.sankamusic.core.api.PlaylistAdapter
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