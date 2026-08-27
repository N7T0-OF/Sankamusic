package com.sankamusic.core.api.model

/**
 * Modèles musicaux UNIFIÉS (voir prompt d'architecture § 16) : l'interface et les
 * plugins ne manipulent jamais un modèle spécifique à un service. Chaque provider
 * (SimpMusic/YouTube Music, Spotify, Apple Music, Deezer, Local) convertit ses
 * données vers ces modèles via son adaptateur.
 */
data class UnifiedTrack(
    val id: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val album: UnifiedAlbum? = null,
    val durationMs: Long? = null,
    val artworkUrl: String? = null,
    /** Identifiant du provider d'origine (ex. "simpmusic", "spotify", "local"). */
    val provider: String,
)

data class UnifiedAlbum(
    val id: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val artworkUrl: String? = null,
    val provider: String,
)

data class UnifiedArtist(
    val id: String,
    val name: String,
    val provider: String,
)

data class UnifiedPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val provider: String,
)
