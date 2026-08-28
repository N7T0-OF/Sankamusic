package com.sankamusic.core.update

import com.sankamusic.core.api.SpaceKaiContracts
import com.sankamusic.core.api.builtInSpaceKaiFeatures
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
    }

    @Test
    fun `not compatible with prereleases or invalid versions`() {
        assertFalse(adapter.isCompatibleWith("2.0.0-rc1"))
        assertFalse(adapter.isCompatibleWith("TBD"))
        assertFalse(adapter.isCompatibleWith(""))
    }

    @Test
    fun `satisfies all migrated SpaceKai contracts`() {
        // L'Adapter v1 fournit les 6 contrats des fonctionnalités migrées (étapes 1-6).
        SpaceKaiContracts.SIMPMUSIC_ADAPTER_V1.forEach { contract ->
            assertTrue("contrat '$contract' devrait être satisfait", adapter.satisfiesContract(contract))
        }
    }

    @Test
    fun `does not satisfy unknown contracts`() {
        assertFalse(adapter.satisfiesContract("unknown-contract"))
        assertFalse(adapter.satisfiesContract(""))
    }

    @Test
    fun `built-in manifest contracts are all covered by adapter v1`() {
        // Invariant anti-dérive : tout contrat déclaré dans le manifest intégré
        // doit être fourni par l'Adapter, sinon la fonctionnalité serait
        // silencieusement désactivée au runtime.
        builtInSpaceKaiFeatures.features.forEach { feature ->
            val contract = feature.contract
            if (contract != null) {
                assertTrue(
                    "contrat '$contract' de '${feature.id}' non satisfait par l'Adapter v1",
                    adapter.satisfiesContract(contract),
                )
            }
        }
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