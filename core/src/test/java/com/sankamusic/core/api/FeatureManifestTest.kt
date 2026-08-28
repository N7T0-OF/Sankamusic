package com.sankamusic.core.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests du manifest de fonctionnalités SpaceKai (docs/FEATURE_MANIFEST.md). */
class FeatureManifestTest {

    // ── Manifest intégré ────────────────────────────────────────────────

    @Test
    fun `built-in manifest is valid and lists migrated features`() {
        assertTrue(builtInSpaceKaiFeatures.validationErrors().isEmpty())
        assertEquals("com.maxrave.simpmusic", builtInSpaceKaiFeatures.targetPackage)
        assertTrue(builtInSpaceKaiFeatures.features.map { it.id }.containsAll(
            listOf("navigation", "themes", "orientation", "player", "haptics", "dynamic_color"),
        ))
    }

    @Test
    fun `duplicate feature ids are rejected`() {
        val manifest = SpaceKaiFeaturesManifest(
            name = "t",
            version = "1.0.0",
            targetPackage = "com.example",
            features = listOf(
                SpaceKaiFeature(id = "a", name = "A"),
                SpaceKaiFeature(id = "a", name = "B"),
            ),
        )
        assertTrue(manifest.validationErrors().any { it.contains("dupliqués") })
    }

    @Test
    fun `invalid patterns and versions are rejected`() {
        val manifest = SpaceKaiFeaturesManifest(
            name = "t",
            version = "1.0.0",
            targetPackage = "com.example",
            features = listOf(
                SpaceKaiFeature(id = "a", name = "A", upstreamCompatibility = "1.7", minSankamusicVersion = "x.y.z"),
                SpaceKaiFeature(id = "b", name = "B", upstreamCompatibility = "x"),
            ),
        )
        val errors = manifest.validationErrors()
        assertTrue(errors.any { it.contains("upstreamCompatibility") })
        assertTrue(errors.any { it.contains("minSankamusicVersion") })
    }

    // ── Matching de plages ──────────────────────────────────────────────

    @Test
    fun `wildcard matches any valid upstream version`() {
        assertTrue(upstreamMatches("*", "1.7.0"))
        assertTrue(upstreamMatches("*", "2.0.0"))
        assertTrue(upstreamMatches("*", "10.3.1"))
    }

    @Test
    fun `major wildcard matches any minor of the major`() {
        assertTrue(upstreamMatches("1.x", "1.0.0"))
        assertTrue(upstreamMatches("1.x", "1.9.9"))
        assertFalse(upstreamMatches("1.x", "2.0.0"))
        assertFalse(upstreamMatches("1.x", "0.9.0"))
    }

    @Test
    fun `minor wildcard matches the major and minor`() {
        assertTrue(upstreamMatches("1.7.x", "1.7.0"))
        assertTrue(upstreamMatches("1.7.x", "1.7.99"))
        assertFalse(upstreamMatches("1.7.x", "1.8.0"))
        assertFalse(upstreamMatches("1.7.x", "2.7.0"))
    }

    @Test
    fun `exact version matches only itself`() {
        assertTrue(upstreamMatches("1.7.0", "1.7.0"))
        assertFalse(upstreamMatches("1.7.0", "1.7.1"))
    }

    @Test
    fun `git tag prefix is accepted`() {
        assertTrue(upstreamMatches("1.7.x", "v1.7.2"))
    }

    @Test
    fun `null or invalid upstream version never matches`() {
        assertFalse(upstreamMatches("*", null))
        assertFalse(upstreamMatches("*", "pas.une.version"))
        assertFalse(upstreamMatches("1.7.x", ""))
    }

    // ── Compatibilité par fonctionnalité ────────────────────────────────

    @Test
    fun `feature is compatible within its declared range`() {
        val manifest = builtInSpaceKaiFeatures
        assertTrue(manifest.isFeatureCompatible("navigation", "2.0.0"))
        assertTrue(manifest.isFeatureCompatible("themes", "9.9.9")) // "*"
        assertFalse(manifest.isFeatureCompatible("navigation", "2.1.0"))
        assertFalse(manifest.isFeatureCompatible("navigation", null))
    }

    @Test
    fun `unknown feature is never compatible`() {
        assertFalse(builtInSpaceKaiFeatures.isFeatureCompatible("inconnu", "2.0.0"))
    }

    @Test
    fun `compatibleFeatures filters by upstream version`() {
        val compatible = builtInSpaceKaiFeatures.compatibleFeatures("2.0.0").map { it.id }
        assertTrue(compatible.containsAll(listOf("navigation", "orientation", "player", "themes", "haptics", "dynamic_color")))
        val forNewer = builtInSpaceKaiFeatures.compatibleFeatures("2.1.0").map { it.id }
        assertFalse(forNewer.contains("navigation")) // 2.0.x uniquement
        assertTrue(forNewer.contains("themes")) // "*"
    }

    // ── Sérialisation JSON ──────────────────────────────────────────────

    @Test
    fun `manifest round trips through JSON`() {
        val json = Json { prettyPrint = true }
        val encoded = json.encodeToString(SpaceKaiFeaturesManifest.serializer(), builtInSpaceKaiFeatures)
        val decoded = json.decodeFromString(SpaceKaiFeaturesManifest.serializer(), encoded)
        assertEquals(builtInSpaceKaiFeatures, decoded)
    }
}
