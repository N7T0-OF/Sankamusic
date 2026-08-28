package com.sankamusic.core.bridge

import com.sankamusic.core.api.model.UnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests des conversions pures library (SongDraft) et playlists
 * (LocalPlaylistDraft) — miroirs des classes réelles `SongEntity` /
 * `LocalPlaylistEntity` de la base SimpMusic 2.0.0 (UPSTREAM_SYSTEM.md § 7).
 */
class LibraryPlaylistBridgeTest {

    // ── Library ─────────────────────────────────────────────────────────

    @Test
    fun `song draft maps to a unified track`() {
        val draft = SongDraft(
            videoId = "vid123",
            title = "Imagine",
            artistNames = listOf("John Lennon"),
            durationSeconds = 183,
            thumbnails = "https://example.com/imagine.jpg",
        )
        val track = draft.toUnifiedTrack(provider = "simpmusic")
        assertEquals("vid123", track.id)
        assertEquals("Imagine", track.title)
        assertEquals(listOf("John Lennon"), track.artists)
        assertEquals(183_000L, track.durationMs)
        assertEquals("https://example.com/imagine.jpg", track.artworkUrl)
        assertEquals("simpmusic", track.provider)
    }

    @Test
    fun `song draft without optional fields keeps them null`() {
        val track = SongDraft(videoId = "v", title = "Only Title").toUnifiedTrack(provider = "simpmusic")
        assertEquals(emptyList<String>(), track.artists)
        assertNull(track.durationMs)
        assertNull(track.artworkUrl)
    }

    @Test
    fun `unified track maps back to a song draft`() {
        val track = UnifiedTrack(
            id = "id9", title = "Bohemian Rhapsody", artists = listOf("Queen"),
            durationMs = 354_000, artworkUrl = "https://e.com/art.jpg", provider = "simpmusic",
        )
        val draft = track.toSongDraft()
        assertEquals("id9", draft.videoId)
        assertEquals("Bohemian Rhapsody", draft.title)
        assertEquals(listOf("Queen"), draft.artistNames)
        assertEquals(354, draft.durationSeconds)
        assertEquals("https://e.com/art.jpg", draft.thumbnails)
    }

    // ── Playlists ────────────────────────────────────────────────────────

    @Test
    fun `local playlist draft maps to a unified playlist`() {
        val draft = LocalPlaylistDraft(
            id = 42L,
            title = "Road Trip",
            thumbnail = "https://e.com/rt.jpg",
            tracks = listOf("t1", "t2", "t3"),
        )
        val playlist = draft.toUnifiedPlaylist(provider = "simpmusic")
        assertEquals("42", playlist.id)
        assertEquals("Road Trip", playlist.name)
        assertEquals(listOf("t1", "t2", "t3"), playlist.trackIds)
        assertEquals("simpmusic", playlist.provider)
    }

    @Test
    fun `local playlist draft without tracks yields empty list`() {
        val playlist = LocalPlaylistDraft(id = 1L, title = "Empty").toUnifiedPlaylist(provider = "simpmusic")
        assertEquals(emptyList<String>(), playlist.trackIds)
    }
}