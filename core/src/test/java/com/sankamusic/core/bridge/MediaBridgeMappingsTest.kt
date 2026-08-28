package com.sankamusic.core.bridge

import com.sankamusic.core.api.model.UnifiedAlbum
import com.sankamusic.core.api.model.UnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests des conversions pures du pont player (Phase 2 — docs/UPSTREAM_SYSTEM.md
 * § 7) : `UnifiedTrack` ↔ [MediaItemDraft], miroir des classes réelles
 * `GenericMediaItem`/`GenericMediaMetadata` de la base SimpMusic 2.0.0.
 */
class MediaBridgeMappingsTest {

    @Test
    fun `unified track maps to media item draft`() {
        val track = UnifiedTrack(
            id = "abc123",
            title = "Bohemian Rhapsody",
            artists = listOf("Queen"),
            album = UnifiedAlbum(id = "a1", title = "A Night at the Opera", provider = "simpmusic"),
            artworkUrl = "https://example.com/art.jpg",
            durationMs = 354_000,
            provider = "simpmusic",
        )
        val draft = track.toMediaItemDraft()
        assertEquals("abc123", draft.mediaId)
        assertEquals("Bohemian Rhapsody", draft.title)
        assertEquals("Queen", draft.artist)
        assertEquals("A Night at the Opera", draft.albumTitle)
        assertEquals("https://example.com/art.jpg", draft.artworkUri)
        assertNull(draft.uri)
        assertNull(draft.customCacheKey)
    }

    @Test
    fun `multiple artists are joined with a separator`() {
        val track = UnifiedTrack(
            id = "x", title = "T", artists = listOf("A", "B", "C"), provider = "simpmusic",
        )
        assertEquals("A • B • C", track.toMediaItemDraft().artist)
    }

    @Test
    fun `missing optional fields stay null`() {
        val track = UnifiedTrack(id = "x", title = "T", provider = "simpmusic")
        val draft = track.toMediaItemDraft()
        assertNull(draft.artist)
        assertNull(draft.albumTitle)
        assertNull(draft.artworkUri)
    }

    @Test
    fun `draft round-trips back to a unified track`() {
        val draft = MediaItemDraft(
            mediaId = "abc",
            uri = "https://cdn.example.com/stream",
            title = "Song",
            artist = "Artist",
            albumTitle = "Album",
            artworkUri = "https://example.com/a.jpg",
            customCacheKey = "cache-1",
        )
        val track = draft.toUnifiedTrack(provider = "simpmusic")
        assertEquals("abc", track.id)
        assertEquals("Song", track.title)
        assertEquals(listOf("Artist"), track.artists)
        assertEquals("Album", track.album?.title)
        assertEquals("https://example.com/a.jpg", track.artworkUrl)
        assertEquals("simpmusic", track.provider)
    }

    @Test
    fun `round trip preserves the id and title`() {
        val track = UnifiedTrack(id = "id42", title = "La Vie en Rose", artists = listOf("Édith Piaf"), provider = "simpmusic")
        val roundTripped = track.toMediaItemDraft().toUnifiedTrack(provider = "simpmusic")
        assertEquals(track.id, roundTripped.id)
        assertEquals(track.title, roundTripped.title)
        assertEquals(track.artists, roundTripped.artists)
    }
}
