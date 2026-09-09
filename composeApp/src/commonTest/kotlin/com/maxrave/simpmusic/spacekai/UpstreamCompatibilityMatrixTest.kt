package com.maxrave.simpmusic.spacekai

import com.maxrave.domain.data.model.update.UpdateData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SPACEKAI FEATURE: tests for the manifest-driven upstream compatibility
 * matrix — the replacement for the hardcoded max-tested ceiling. A release
 * matching a declared base is compatible even when newer than this build's
 * own base; an unknown future base is not.
 */
class UpstreamCompatibilityMatrixTest {

    @Test
    fun `declared bases resolve exactly`() {
        assertEquals("2.0.0", UpstreamCompatibilityMatrix.resolve("v2.0.0")?.tag)
        assertEquals("2.1.0", UpstreamCompatibilityMatrix.resolve("v2.1.0")?.tag)
        assertEquals("2.0.0", UpstreamCompatibilityMatrix.resolve("2.0.0")?.tag)
    }

    @Test
    fun `pre-release and re-cut tags resolve to their nearest declared base`() {
        assertEquals("2.1.0", UpstreamCompatibilityMatrix.resolve("v2.1.0-beta.1")?.tag)
        assertEquals("2.0.0", UpstreamCompatibilityMatrix.resolve("v2.0.0-1")?.tag)
    }

    @Test
    fun `beta channel prefers a newer prerelease`() {
        val stable = UpdateData(tagName = "v1.0.0", releaseTime = null, body = "")
        val beta = UpdateData(tagName = "v1.1.0-beta.1", releaseTime = null, body = "", isPrerelease = true)
        assertEquals(
            beta,
            selectSpaceKaiUpdate(SpaceKaiUpdateChannel.BETA, "v1.0.0", beta, stable),
        )
    }

    @Test
    fun `beta channel falls back to stable when prerelease is not newer`() {
        val stable = UpdateData(tagName = "v1.1.0", releaseTime = null, body = "")
        val beta = UpdateData(tagName = "v1.0.0-beta.1", releaseTime = null, body = "", isPrerelease = true)
        assertEquals(
            stable,
            selectSpaceKaiUpdate(SpaceKaiUpdateChannel.BETA, "v1.0.0", beta, stable),
        )
    }

    @Test
    fun `beta channel does not hide a newer stable release`() {
        val stable = UpdateData(tagName = "v1.2.0", releaseTime = null, body = "")
        val beta = UpdateData(tagName = "v1.1.0-beta.1", releaseTime = null, body = "", isPrerelease = true)
        assertEquals(
            stable,
            selectSpaceKaiUpdate(SpaceKaiUpdateChannel.BETA, "v1.0.0", beta, stable),
        )
    }

    @Test
    fun `stable channel ignores a beta candidate`() {
        val beta = UpdateData(tagName = "v1.1.0-beta.1", releaseTime = null, body = "", isPrerelease = true)
        val stable = UpdateData(tagName = "v1.0.0", releaseTime = null, body = "")
        assertEquals(
            stable,
            selectSpaceKaiUpdate(SpaceKaiUpdateChannel.STABLE, "v1.0.0", beta, stable),
        )
    }

    @Test
    fun `beta channel chooses stable over an equal-version prerelease`() {
        val stable = UpdateData(tagName = "v1.1.0", releaseTime = null, body = "")
        val beta = UpdateData(tagName = "v1.1.0-beta.1", releaseTime = null, body = "", isPrerelease = true)
        assertEquals(
            stable,
            selectSpaceKaiUpdate(SpaceKaiUpdateChannel.BETA, "v1.0.0", beta, stable),
        )
    }

    @Test
    fun `unknown future base resolves to null and is not compatible`() {
        assertNull(UpstreamCompatibilityMatrix.resolve("v2.2.0"))
        assertFalse(UpstreamCompatibilityMatrix.isCompatible("v2.2.0"))
    }

    @Test
    fun `null blank or garbage tags are never compatible`() {
        assertNull(UpstreamCompatibilityMatrix.resolve(null))
        assertNull(UpstreamCompatibilityMatrix.resolve(""))
        assertNull(UpstreamCompatibilityMatrix.resolve("  "))
        assertNull(UpstreamCompatibilityMatrix.resolve("not-a-version"))
        assertFalse(UpstreamCompatibilityMatrix.isCompatible("garbage"))
    }

    @Test
    fun `declared bases are compatible and testedness is distinguishable`() {
        assertTrue(UpstreamCompatibilityMatrix.isCompatible("v2.0.0"))
        assertTrue(UpstreamCompatibilityMatrix.isCompatible("v2.1.0"))
        assertFalse(UpstreamCompatibilityMatrix.isUntested("v2.0.0"), "2.0.0 is the tested base")
        assertTrue(UpstreamCompatibilityMatrix.isUntested("v2.1.0"), "2.1.0 is declared but not yet device-validated")
    }

    @Test
    fun `computeUpstreamCompatibility follows the manifest for a newer declared base`() {
        // Build based on 2.0.0, latest official release is 2.1.0: the OLD ceiling
        // said "not compatible"; the manifest says compatible (declared base).
        val c =
            computeUpstreamCompatibility(
                latestUpstream = "v2.1.0",
                basedOn = "2.0.0",
                maxTested = "2.0.0",
            )
        assertTrue(c.compatible, "a declared base is compatible even when newer than the build's base")
        assertTrue(c.statusLabel.contains("supportée"), "label must reflect the manifest verdict")
        assertFalse(c.statusLabel.contains("pas encore compatible"))
    }

    @Test
    fun `computeUpstreamCompatibility keeps the untested nuance`() {
        val c =
            computeUpstreamCompatibility(
                latestUpstream = "v2.1.0",
                basedOn = "2.0.0",
                maxTested = "2.0.0",
            )
        assertTrue(c.statusLabel.contains("validation appareil"), "declared-but-untested base keeps the honest nuance")
    }

    @Test
    fun `computeUpstreamCompatibility still rejects an unknown future base`() {
        val c =
            computeUpstreamCompatibility(
                latestUpstream = "v2.2.0",
                basedOn = "2.0.0",
                maxTested = "2.0.0",
            )
        assertFalse(c.compatible, "an undeclared base stays incompatible")
        assertTrue(c.statusLabel.contains("pas encore compatible"))
    }

    @Test
    fun `same-version release stays up to date`() {
        val c =
            computeUpstreamCompatibility(
                latestUpstream = "v2.0.0",
                basedOn = "2.0.0",
                maxTested = "2.0.0",
            )
        assertTrue(c.compatible)
        assertTrue(c.statusLabel.contains("À jour"))
    }
}
