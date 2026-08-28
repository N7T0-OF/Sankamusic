package com.sankamusic.core.bridge

import com.sankamusic.core.api.model.UnifiedAlbum
import com.sankamusic.core.api.model.UnifiedTrack

/**
 * Pré-câblage des conversions du sous-adaptateur player (Phase 2 —
 * docs/UPSTREAM_SYSTEM.md § 7). Les champs miroitent les classes RÉELLES de la
 * base SimpMusic 2.0.0 (`maxrave-dev/core`, branche multiplatform) :
 *
 * ```kotlin
 * // com.maxrave.domain.data.player (base 2.0.0)
 * data class GenericMediaItem(
 *     val mediaId: String,
 *     val uri: String?,
 *     val metadata: GenericMediaMetadata,
 *     val customCacheKey: String? = null,
 * )
 * data class GenericMediaMetadata(
 *     val title: String?, val artist: String?, val albumTitle: String?,
 *     val artworkUri: String?, val description: String? = null,
 * )
 * ```
 *
 * Cette couche reste PURE (aucune référence à la base) : elle produit un
 * [MediaItemDraft] neutre que l'Adapter V2 transformera en
 * `GenericMediaItem`/`GenericMediaMetadata` une fois la dépendance présente :
 *
 * ```kotlin
 * // Phase 2 — dans l'Adapter V2 (base en dépendance) :
 * GenericMediaItem(
 *     mediaId = draft.mediaId,
 *     uri = draft.uri,
 *     metadata = GenericMediaMetadata(
 *         title = draft.title, artist = draft.artist,
 *         albumTitle = draft.albumTitle, artworkUri = draft.artworkUri,
 *     ),
 *     customCacheKey = draft.customCacheKey,
 * )
 * ```
 *
 * La conversion est testée ici, indépendamment de la base — le jour où la
 * base est intégrée, seul le câblage final (5 lignes ci-dessus) reste à faire.
 */

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

/**
 * Conversion `UnifiedTrack` → brouillon d'item media (champs réels de la base
 * 2.0.0). `uri` optionnel : la base le résout côté moteur quand il est absent.
 * `mediaId` = `track.id` (identifiant stable du provider).
 */
fun UnifiedTrack.toMediaItemDraft(uri: String? = null): MediaItemDraft = MediaItemDraft(
    mediaId = id,
    uri = uri,
    title = title,
    artist = artists.joinToString(" • ").ifEmpty { null },
    albumTitle = album?.title,
    artworkUri = artworkUrl,
)

/**
 * Réciproque : brouillon d'item media (ex. `nowPlaying` de la base) → `UnifiedTrack`.
 * `provider` = provider d'origine du morceau (ex. "simpmusic").
 */
fun MediaItemDraft.toUnifiedTrack(provider: String): UnifiedTrack = UnifiedTrack(
    id = mediaId,
    title = title ?: mediaId,
    artists = artist?.split(" • ")?.filter { it.isNotBlank() } ?: emptyList(),
    album = albumTitle?.let { UnifiedAlbum(id = mediaId, title = it, provider = provider) },
    artworkUrl = artworkUri,
    provider = provider,
)
