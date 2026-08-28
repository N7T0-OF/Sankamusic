package com.sankamusic.core.update

import com.sankamusic.core.api.LibraryAdapter
import com.sankamusic.core.api.MusicPlayerAdapter
import com.sankamusic.core.api.PlaylistAdapter
import com.sankamusic.core.api.SpaceKaiFeature
import com.sankamusic.core.api.SpaceKaiFeaturesManifest
import com.sankamusic.core.api.UpstreamAdapter
import com.sankamusic.core.api.UpstreamInfo
import com.sankamusic.core.api.builtInSpaceKaiFeatures
import com.sankamusic.core.api.model.UnifiedPlaylist
import com.sankamusic.core.api.model.UnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du rapport de compatibilité (docs/FEATURE_MANIFEST.md § 3) : la
 * compatibilité d'une fonctionnalité exige la version upstream DANS la plage
 * ET le contrat fourni par l'Adapter. Jamais de fausse compatibilité.
 */
class CompatibilityReportTest {

    private val manifest = SpaceKaiFeaturesManifest(
        name = "Test",
        version = "1.0.0",
        targetPackage = "com.maxrave.simpmusic",
        features = listOf(
            SpaceKaiFeature(id = "a", name = "A", upstreamCompatibility = "1.7.x", contract = "alpha-api"),
            SpaceKaiFeature(id = "b", name = "B", upstreamCompatibility = "*"),
        ),
    )

    /** Adapter minimal satisfaisant exactement les contrats donnés. */
    private class FakeAdapter(
        private val contracts: Set<String>,
        adapterVersion: Int = 1,
    ) : UpstreamAdapter {
        override val info = UpstreamInfo(
            repository = "maxrave-dev/SimpMusic",
            version = "1.7.0",
            adapterVersion = adapterVersion,
            compatibility = "1.7.x",
        )
        override val player: MusicPlayerAdapter = object : MusicPlayerAdapter {
            override suspend fun play(track: UnifiedTrack) {}
            override suspend fun pause() {}
            override suspend fun resume() {}
            override val isPlaying: Boolean get() = false
        }
        override val library: LibraryAdapter = object : LibraryAdapter {
            override suspend fun tracks(): List<UnifiedTrack> = emptyList()
        }
        override val playlists: PlaylistAdapter = object : PlaylistAdapter {
            override suspend fun playlists(): List<UnifiedPlaylist> = emptyList()
        }
        override fun isCompatibleWith(upstreamVersion: String): Boolean = true
        override fun satisfiesContract(contractId: String): Boolean = contractId in contracts
    }

    @Test
    fun `all compatible when in range and contracts satisfied`() {
        val report = CompatibilityReporter.report(
            manifest,
            "1.7.2",
            FakeAdapter(contracts = setOf("alpha-api")),
        )
        assertEquals(2, report.totalCount)
        assertEquals(2, report.compatibleCount)
        assertEquals(0, report.incompatibleCount)
        assertEquals("2/2 features compatible", report.summary())
        assertEquals(CompatibilityStatus.COMPATIBLE, report.feature("a")!!.status)
        assertEquals(CompatibilityStatus.COMPATIBLE, report.feature("b")!!.status)
    }

    @Test
    fun `missing contract disables only that feature`() {
        val report = CompatibilityReporter.report(
            manifest,
            "1.7.2",
            FakeAdapter(contracts = emptySet()),
        )
        assertEquals(1, report.compatibleCount)
        assertEquals("1/2 features compatible", report.summary())
        val a = report.feature("a")!!
        assertFalse(a.compatible)
        assertEquals(CompatibilityStatus.CONTRACT_NOT_SATISFIED, a.status)
        assertTrue(a.reason.contains("alpha-api"))
        assertTrue(report.feature("b")!!.compatible) // sans contrat, la plage suffit
    }

    @Test
    fun `version out of range disables only that feature`() {
        val report = CompatibilityReporter.report(
            manifest,
            "1.8.0",
            FakeAdapter(contracts = setOf("alpha-api")),
        )
        val a = report.feature("a")!!
        assertFalse(a.compatible)
        assertEquals(CompatibilityStatus.VERSION_OUT_OF_RANGE, a.status)
        assertTrue(a.reason.contains("1.8.0"))
        assertTrue(report.feature("b")!!.compatible) // "*" couvre tout
        assertEquals("1/2 features compatible", report.summary())
    }

    @Test
    fun `unknown upstream disables everything - never a false positive`() {
        val report = CompatibilityReporter.report(
            manifest,
            null,
            FakeAdapter(contracts = setOf("alpha-api")),
        )
        assertEquals(0, report.compatibleCount)
        assertEquals("0/2 features compatible", report.summary())
        report.features.forEach {
            assertFalse(it.compatible)
            assertEquals(CompatibilityStatus.UNKNOWN_UPSTREAM, it.status)
        }
    }

    @Test
    fun `unknown feature id reports FEATURE_UNKNOWN`() {
        val result = featureCompatibility(
            manifest,
            SpaceKaiFeature(id = "nope", name = "Nope"),
            "1.7.2",
            FakeAdapter(contracts = emptySet()),
        )
        assertFalse(result.compatible)
        assertEquals(CompatibilityStatus.FEATURE_UNKNOWN, result.status)
    }

    @Test
    fun `report captures adapter version`() {
        val report = CompatibilityReporter.report(
            manifest,
            "1.7.2",
            FakeAdapter(contracts = setOf("alpha-api"), adapterVersion = 3),
        )
        assertEquals(3, report.adapterVersion)
    }

    @Test
    fun `null adapter with declared contract is conservative`() {
        val result = featureCompatibility(manifest, manifest.featureById("a")!!, "1.7.2", null)
        assertFalse(result.compatible)
        assertEquals(CompatibilityStatus.CONTRACT_NOT_SATISFIED, result.status)
    }

    @Test
    fun `built-in manifest with real adapter is fully compatible at 2_0_0`() {
        val report = CompatibilityReporter.report(builtInSpaceKaiFeatures, "2.0.0", SimpMusicAdapter())
        assertEquals(6, report.totalCount)
        assertEquals(6, report.compatibleCount)
        assertEquals("6/6 features compatible", report.summary())
    }

    @Test
    fun `built-in manifest with real adapter out of range at 2_1_0`() {
        val report = CompatibilityReporter.report(builtInSpaceKaiFeatures, "2.1.0", SimpMusicAdapter())
        // navigation, orientation, player sont en 2.0.x → désactivées ;
        // themes, haptics, dynamic_color sont "*" → toujours compatibles.
        assertEquals(3, report.compatibleCount)
        assertEquals("3/6 features compatible", report.summary())
        assertEquals(CompatibilityStatus.VERSION_OUT_OF_RANGE, report.feature("navigation")!!.status)
        assertEquals(CompatibilityStatus.COMPATIBLE, report.feature("themes")!!.status)
    }
}
