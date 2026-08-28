package com.sankamusic.core.bridge

import com.sankamusic.core.api.model.UnifiedAlbum
import com.sankamusic.core.api.model.UnifiedPlaylist
import com.sankamusic.core.api.model.UnifiedTrack

/**
 * Pré-câblage des conversions des sous-adaptateurs (Phase 2 —
 * docs/UPSTREAM_SYSTEM.md § 7). Les champs miroitent les classes RÉELLES de la
 * base SimpMusic 2.0.0 (`maxrave-dev/core`, branche multiplatform) :
 *
 * ```kotlin
 * // com.maxrave.domain.data.player (base 2.0.0)
 * data class GenericMediaItem(mediaId, uri, metadata: GenericMediaMetadata, customCacheKey)
 * data class GenericMediaMetadata(title, artist, albumTitle, artworkUri, description)
 *
 * // com.maxrave.domain.data.entities (base 2.0.0)
 * data class SongEntity(videoId, title, artistId, artistName, duration?, thumbs, inLibrary…)
 * data class LocalPlaylistEntity(id: Long, title, thumbnail, tracks: List<String>?, …)
 * ```
 *
 * Cette couche reste PURE (aucune référence GPL à la base) : elle produit des
 * brouillons neutres ([MediaItemDraft], [SongDraft], [LocalPlaylistDraft]) que
 * l'Adapter V2 transformera en entités réelles une fois la dépendance présente.
 * ⚠️ Licence : la base et `maxrave-dev/core` sont **GPL-3.0** (UPSTREAM_SYSTEM.md
 * § 6) — ces conversions NE COPIENT PAS le code de la base, elles déclarent
 * seulement les champs nécessaires à l'échange. Les câblages finaux (≈5 lignes
 * par sous-adapter) restent à écrire dans l'Adapter V2 après intégration.
 */

// ── Player ──────────────────────────────────────────────────────────────────

/** Brouillon neutre d'un item de lecture — miroir exact de `GenericMediaItem`. */
data class MediaItemDraft(
    val mediaId: String,
    val uri: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val albumTitle: String? = null,
    val artworkUri: String? = null,
    val customCacheKey: String? = null,
)

/** Conversion `UnifiedTrack` → brouillon d'item media (champs player 2.0.0). */
fun UnifiedTrack.toMediaItemDraft(uri: String? = null): MediaItemDraft = MediaItemDraft(
    mediaId = id,
    uri = uri,
    title = title,
    artist = artists.joinToString(" • ").ifEmpty { null },
    albumTitle = album?.title,
    artworkUri = artworkUrl,
)

/** Réciproque (ex. `nowPlaying` de la base) → `UnifiedTrack`. */
fun MediaItemDraft.toUnifiedTrack(provider: String): UnifiedTrack = UnifiedTrack(
    id = mediaId,
    title = title ?: mediaId,
    artists = artist?.split(" • ")?.filter { it.isNotBlank() } ?: emptyList(),
    album = albumTitle?.let { UnifiedAlbum(id = mediaId, title = it, provider = provider) },
    artworkUrl = artworkUri,
    provider = provider,
)

// ── Library ─────────────────────────────────────────────────────────────────

/**
 * Brouillon neutre d'un morceau de bibliothèque — miroir des champs de
 * `SongEntity` réellement utiles à Sankamusic (`videoId`, `title`,
 * `artistId`/`artistName`, `durationSeconds`, `thumbnails`).
 */
data class SongDraft(
    val videoId: String,
    val title: String,
    val artistIds: List<String>? = null,
    val artistNames: List<String>? = null,
    val durationSeconds: Int? = null,
    val thumbnails: String? = null,
)

/** Conversion `SongEntity` (via brouillon) → `UnifiedTrack`. Le provider est
 * celui d'origine du morceau (ex. "simpmusic"). */
fun SongDraft.toUnifiedTrack(provider: String): UnifiedTrack = UnifiedTrack(
    id = videoId,
    title = title,
    artists = artistNames ?: emptyList(),
    durationMs = durationSeconds?.let { it * 1000L },
    artworkUrl = thumbnails,
    provider = provider,
)

/**
 * Conversion `UnifiedTrack` → brouillon de morceau de bibliothèque (pour
 * stocker/ajouter à la base locale). Le titre sert de `videoId` par défaut
 * quand le morceau n'a pas d'id réel (ex. prévisionnel).
 */
fun UnifiedTrack.toSongDraft(): SongDraft = SongDraft(
    videoId = id,
    title = title,
    artistIds = null, // résolu par la base à l'insertion
    artistNames = artists,
    durationSeconds = durationMs?.let { (it / 1000L).toInt() },
    thumbnails = artworkUrl,
)

// ── Playlists ───────────────────────────────────────────────────────────────

/**
 * Brouillon neutre d'une playlist locale — miroir des champs de
 * `LocalPlaylistEntity` utiles (`id: Long`, `title`, `thumbnail`,
 * `tracks: List<String>?`).
 */
data class LocalPlaylistDraft(
    val id: Long,
    val title: String,
    val thumbnail: String? = null,
    /** Ids (videoId) des pistes qui composent la playlist. */
    val tracks: List<String>? = null,
)

/** Conversion `LocalPlaylistEntity` (via brouillon) → `UnifiedPlaylist`. */
fun LocalPlaylistDraft.toUnifiedPlaylist(provider: String): UnifiedPlaylist = UnifiedPlaylist(
    id = id.toString(),
    name = title,
    trackIds = tracks ?: emptyList(),
    provider = provider,
)