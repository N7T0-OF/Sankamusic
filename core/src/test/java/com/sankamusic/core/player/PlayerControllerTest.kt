package com.sankamusic.core.player

import com.sankamusic.core.api.model.UnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests de la machine à états pure du player (docs/MIGRATION.md étape 4). */
class PlayerControllerTest {

    private fun track(id: String = "t1", durationMs: Long? = 180_000) =
        UnifiedTrack(id = id, title = "Titre $id", provider = "test", durationMs = durationMs)

    private val tracks = listOf(track("a"), track("b"), track("c"))

    // ── Lecture simple ──────────────────────────────────────────────────

    @Test
    fun `play a track starts playing at index 0`() {
        val c = PlayerController()
        assertTrue(c.play(track("x")).isSuccess)
        val s = c.snapshot()
        assertEquals(PlayerStatus.PLAYING, s.status)
        assertEquals("x", s.currentTrack?.id)
        assertEquals(0, s.currentIndex)
        assertEquals(listOf("x"), s.queue.map { it.id })
    }

    @Test
    fun `playQueue starts at requested index`() {
        val c = PlayerController()
        assertTrue(c.playQueue(tracks, startIndex = 1).isSuccess)
        val s = c.snapshot()
        assertEquals("b", s.currentTrack?.id)
        assertEquals(1, s.currentIndex)
        assertEquals(3, s.queue.size)
    }

    @Test
    fun `playQueue rejects empty queue and out of bounds start`() {
        val c = PlayerController()
        assertTrue(c.playQueue(emptyList()).isFailure)
        assertTrue(c.playQueue(tracks, startIndex = 5).isFailure)
        assertEquals(PlayerStatus.IDLE, c.snapshot().status)
    }

    // ── Pause / reprise / bascule ───────────────────────────────────────

    @Test
    fun `pause then resume transitions`() {
        val c = PlayerController()
        c.play(track())
        assertTrue(c.pause().isSuccess)
        assertEquals(PlayerStatus.PAUSED, c.snapshot().status)
        assertTrue(c.resume().isSuccess)
        assertEquals(PlayerStatus.PLAYING, c.snapshot().status)
    }

    @Test
    fun `pause when idle fails cleanly without changing state`() {
        val c = PlayerController()
        assertTrue(c.pause().isFailure)
        assertEquals(PlayerStatus.IDLE, c.snapshot().status)
    }

    @Test
    fun `resume when playing fails cleanly`() {
        val c = PlayerController()
        c.play(track())
        assertTrue(c.resume().isFailure)
        assertEquals(PlayerStatus.PLAYING, c.snapshot().status)
    }

    @Test
    fun `toggle switches between playing and paused`() {
        val c = PlayerController()
        c.play(track())
        assertTrue(c.togglePlayPause().isSuccess)
        assertEquals(PlayerStatus.PAUSED, c.snapshot().status)
        assertTrue(c.togglePlayPause().isSuccess)
        assertEquals(PlayerStatus.PLAYING, c.snapshot().status)
    }

    @Test
    fun `toggle when idle fails cleanly`() {
        assertTrue(PlayerController().togglePlayPause().isFailure)
    }

    // ── Navigation dans la file ─────────────────────────────────────────

    @Test
    fun `next advances and resets position`() {
        val c = PlayerController()
        c.playQueue(tracks)
        c.seekTo(42_000)
        assertTrue(c.next().isSuccess)
        val s = c.snapshot()
        assertEquals("b", s.currentTrack?.id)
        assertEquals(1, s.currentIndex)
        assertEquals(0, s.positionMillis)
        assertEquals(PlayerStatus.PLAYING, s.status)
    }

    @Test
    fun `next at end of queue fails cleanly`() {
        val c = PlayerController()
        c.playQueue(tracks, startIndex = 2)
        assertTrue(c.next().isFailure)
        assertEquals("c", c.snapshot().currentTrack?.id)
        assertEquals(PlayerStatus.PLAYING, c.snapshot().status)
    }

    @Test
    fun `previous goes back and fails at head`() {
        val c = PlayerController()
        c.playQueue(tracks, startIndex = 2)
        assertTrue(c.previous().isSuccess)
        assertEquals("b", c.snapshot().currentTrack?.id)
        assertTrue(c.previous().isSuccess)
        assertEquals("a", c.snapshot().currentTrack?.id)
        assertTrue(c.previous().isFailure)
        assertEquals("a", c.snapshot().currentTrack?.id)
    }

    @Test
    fun `seekToIndex moves within bounds`() {
        val c = PlayerController()
        c.playQueue(tracks)
        assertTrue(c.seekToIndex(2).isSuccess)
        assertEquals("c", c.snapshot().currentTrack?.id)
        assertTrue(c.seekToIndex(9).isFailure)
    }

    // ── Position / durée ────────────────────────────────────────────────

    @Test
    fun `seekTo sets position and rejects negatives`() {
        val c = PlayerController()
        c.play(track(durationMs = 100_000))
        assertTrue(c.seekTo(55_000).isSuccess)
        assertEquals(55_000, c.snapshot().positionMillis)
        assertTrue(c.seekTo(-1).isFailure)
        assertEquals(55_000, c.snapshot().positionMillis)
    }

    @Test
    fun `duration comes from the track and can be updated`() {
        val c = PlayerController()
        c.play(track(durationMs = 120_000))
        assertEquals(120_000L, c.snapshot().durationMillis)
        c.setDuration(121_000)
        assertEquals(121_000L, c.snapshot().durationMillis)
    }

    // ── File d'attente ──────────────────────────────────────────────────

    @Test
    fun `enqueue appends without interrupting playback`() {
        val c = PlayerController()
        c.playQueue(tracks)
        c.enqueue(track("d"))
        c.enqueueAll(listOf(track("e"), track("f")))
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), c.snapshot().queue.map { it.id })
        assertEquals("a", c.snapshot().currentTrack?.id)
    }

    @Test
    fun `removeAt adjusts the current index`() {
        val c = PlayerController()
        c.playQueue(tracks, startIndex = 1) // courant : b (index 1)
        assertTrue(c.removeAt(0).isSuccess) // retire a → b devient index 0
        val s = c.snapshot()
        assertEquals(listOf("b", "c"), s.queue.map { it.id })
        assertEquals("b", s.currentTrack?.id)
        assertEquals(0, s.currentIndex)
    }

    @Test
    fun `removeAt of current track moves to next or stops`() {
        val c = PlayerController()
        c.playQueue(tracks, startIndex = 0)
        assertTrue(c.removeAt(0).isSuccess)
        assertEquals("b", c.snapshot().currentTrack?.id)
        // Retire b puis c → file vide → IDLE.
        assertTrue(c.removeAt(0).isSuccess)
        assertTrue(c.removeAt(0).isSuccess)
        assertEquals(PlayerStatus.IDLE, c.snapshot().status)
        assertNull(c.snapshot().currentTrack)
        assertTrue(c.removeAt(0).isFailure)
    }

    @Test
    fun `clear resets everything`() {
        val c = PlayerController()
        c.playQueue(tracks)
        c.clear()
        val s = c.snapshot()
        assertEquals(PlayerStatus.IDLE, s.status)
        assertTrue(s.queue.isEmpty())
        assertNull(s.currentIndex)
        assertNull(s.currentTrack)
        assertEquals(0, s.positionMillis)
    }

    // ── Erreur ──────────────────────────────────────────────────────────

    @Test
    fun `reportError sets ERROR and carries the message`() {
        val c = PlayerController()
        c.play(track())
        assertTrue(c.reportError("stream 404").isFailure)
        val s = c.snapshot()
        assertEquals(PlayerStatus.ERROR, s.status)
        assertEquals("stream 404", s.errorMessage)
    }
}
