package com.sankamusic.core.api

import com.sankamusic.core.api.model.UnifiedPlaylist
import com.sankamusic.core.api.model.UnifiedTrack

/**
 * Couche d'abstraction isolant Sankamusic des détails internes de SimpMusic
 * (voir docs/UPSTREAM_SYSTEM.md).
 *
 * ```
 * SimpMusic v1 → Adapter v1 → API Sankamusic (stable)
 * SimpMusic v2 → Adapter v2 → API Sankamusic (stable)
 * ```
 *
 * Sankamusic ne connaît QUE l'Adapter. Si l'API upstream change, seul l'Adapter
 * doit être adapté autant que possible.
 */
interface UpstreamAdapter {
    /** Informations de compatibilité (version upstream, version d'Adapter). */
    val info: UpstreamInfo

    val player: MusicPlayerAdapter
    val library: LibraryAdapter
    val playlists: PlaylistAdapter

    /** Vrai si cet Adapter couvre la version upstream donnée. */
    fun isCompatibleWith(upstreamVersion: String): Boolean
}

/** Informations de compatibilité connues du système (docs/UPSTREAM_SYSTEM.md § 3). */
data class UpstreamInfo(
    val repository: String,
    val version: String,
    val adapterVersion: Int,
    val compatibility: String,
)

// ── Sous-adaptateurs (squelettes minimaux — à affiner après l'audit) ──

interface MusicPlayerAdapter {
    suspend fun play(track: UnifiedTrack)
    suspend fun pause()
    suspend fun resume()
    val isPlaying: Boolean
}

interface LibraryAdapter {
    suspend fun tracks(): List<UnifiedTrack>
}

interface PlaylistAdapter {
    suspend fun playlists(): List<UnifiedPlaylist>
}
