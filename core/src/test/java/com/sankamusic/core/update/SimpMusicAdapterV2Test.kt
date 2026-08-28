package com.sankamusic.core.update

import com.sankamusic.core.api.SpaceKaiContracts
import com.sankamusic.core.api.model.UnifiedTrack
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de l'[SimpMusicAdapterV2] — le premier test de résistance de
 * l'architecture (docs/UPSTREAM_SYSTEM.md § 8bis) : la base SimpMusic 2.0.0
 * a été auditée, les points d'intégration des contrats existent, mais les
 * plages du manifest ne sont PAS encore étendues (validation par contract
 * tests + build Phase 2 requise — docs/FEATURE_MANIFEST.md § 3).
 */
class SimpMusicAdapterV2Test {

    private val adapter = SimpMusicAdapterV2()

    @Test
    fun `adapter declares audited 2_0_0 upstream info`() {
        assertEquals("maxrave-dev/SimpMusic", adapter.info.repository)
        assertEquals("2.0.0", adapter.info.version)
        assertEquals(2, adapter.info.adapterVersion)
        assertEquals("2.0.x", adapter.info.compatibility)
    }

    @Test
    fun `compatible with any stable 2_0_x version`() {
        assertTrue(adapter.isCompatibleWith("2.0.0"))
        assertTrue(adapter.isCompatibleWith("2.0.2"))
        assertTrue(adapter.isCompatibleWith("2.0.99"))
    }

    @Test
    fun `not compatible with other majors or minors`() {
        assertFalse(adapter.isCompatibleWith("1.9.0"))
        assertFalse(adapter.isCompatibleWith("2.1.0"))
        assertFalse(adapter.isCompatibleWith("3.0.0"))
        assertFalse(adapter.isCompatibleWith("2.0.0-rc1"))
    }

    @Test
    fun `declares the same six contracts as v1`() {
        SpaceKaiContracts.SIMPMUSIC_ADAPTER_V1.forEach { contract ->
            assertTrue("contrat '$contract' devrait être satisfait", adapter.satisfiesContract(contract))
        }
        assertFalse(adapter.satisfiesContract("unknown-contract"))
    }

    @Test
    fun `sub adapters are not linked yet and fail explicitly`() {
        // Phase 2 (base intégrée comme dépendance) : échec explicite, jamais simulé.
        try {
            runBlocking { adapter.player.play(UnifiedTrack(id = "x", title = "x", provider = "simpmusic")) }
            throw AssertionError("player.play aurait dû lever NotImplementedError")
        } catch (expected: NotImplementedError) {
            assertTrue(expected.message!!.contains("non relié"))
        }
    }
}
