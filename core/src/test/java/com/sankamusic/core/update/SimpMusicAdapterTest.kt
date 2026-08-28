package com.sankamusic.core.update

import com.sankamusic.core.api.model.UnifiedTrack
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de l'[SimpMusicAdapter] — la déclaration de compatibilité de la base
 * upstream SimpMusic (docs/UPSTREAM_SYSTEM.md § 3).
 */
class SimpMusicAdapterTest {

    private val adapter = SimpMusicAdapter()

    @Test
    fun `adapter declares verified upstream info`() {
        assertEquals("maxrave-dev/SimpMusic", adapter.info.repository)
        assertEquals("1.7.0", adapter.info.version)
        assertEquals(1, adapter.info.adapterVersion)
        assertEquals("1.7.x", adapter.info.compatibility)
    }

    @Test
    fun `compatible with any stable 1_7_x version`() {
        assertTrue(adapter.isCompatibleWith("1.7.0"))
        assertTrue(adapter.isCompatibleWith("1.7.2"))
        assertTrue(adapter.isCompatibleWith("1.7.99"))
    }

    @Test
    fun `not compatible with other majors or minors`() {
        assertFalse(adapter.isCompatibleWith("1.6.0"))
        assertFalse(adapter.isCompatibleWith("1.8.0"))
        assertFalse(adapter.isCompatibleWith("2.0.0"))
    }

    @Test
    fun `not compatible with prereleases or invalid versions`() {
        assertFalse(adapter.isCompatibleWith("1.7.0-rc1"))
        assertFalse(adapter.isCompatibleWith("TBD"))
        assertFalse(adapter.isCompatibleWith(""))
    }

    @Test
    fun `sub adapters are not linked yet and fail explicitly`() {
        // La base SimpMusic n'est pas encore intégrée comme dépendance (Phase 2) :
        // les sous-adaptateurs doivent échouer explicitement, jamais simuler.
        try {
            runBlocking { adapter.player.play(UnifiedTrack(id = "x", title = "x", provider = "simpmusic")) }
            throw AssertionError("player.play aurait dû lever NotImplementedError")
        } catch (expected: NotImplementedError) {
            assertTrue(expected.message!!.contains("non relié"))
        }
    }
}