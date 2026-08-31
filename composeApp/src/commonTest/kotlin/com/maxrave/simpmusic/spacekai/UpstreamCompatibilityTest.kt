package com.maxrave.simpmusic.spacekai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SPACEKAI FEATURE: tests for the upstream (SimpMusic) version detection, per the
 * "base intégrée ≠ dernière release officielle" spec. All five spec cases plus the
 * semantic-comparison guard for re-cut tags.
 */
class UpstreamCompatibilityTest {

    // ---------- Spec test 1: integrated base NEWER than the official release ----------
    // integrated = 2.0.0, latest = 1.7.0 => NO UPDATE, "base plus récente".
    @Test
    fun `integrated newer than release means no update and no downgrade`() {
        val c =
            computeUpstreamCompatibility(
                latestUpstream = "v1.7.0",
                basedOn = "2.0.0",
                maxTested = "2.0.0",
            )
        assertTrue(c.compatible, "already-newer base is still compatible")
        assertTrue(c.statusLabel.contains("plus récente"), "label must say base is newer")
        assertFalse(c.statusLabel.contains("Nouvelle release"), "must NOT claim a new version")
    }

    // ---------- Spec test 2: a genuinely newer official release exists ----------
    // integrated = 1.7.0, latest = 2.0.0 => UPDATE AVAILABLE (not compatible yet).
    @Test
    fun `newer official release is reported as detected`() {
        val c =
            computeUpstreamCompatibility(
                latestUpstream = "v2.0.0",
                basedOn = "1.7.0",
                maxTested = "1.7.0",
            )
        assertFalse(c.compatible, "newer release outside tested range is not compatible")
        assertTrue(c.statusLabel.contains("Nouvelle release officielle détectée"))
        // The raw GitHub tag is kept as-is (leading "v" preserved); display strips it.
        assertEquals("v2.0.0", c.latestUpstream)
    }

    // ---------- Spec test 3: integrated == latest => UP TO DATE ----------
    @Test
    fun `integrated equals latest is up to date`() {
        val c =
            computeUpstreamCompatibility(
                latestUpstream = "v2.0.0",
                basedOn = "2.0.0",
                maxTested = "2.0.0",
            )
        assertTrue(c.compatible)
        assertTrue(c.statusLabel.contains("À jour"))
    }

    // ---------- Spec test 4: GitHub unavailable => UNKNOWN, never "à jour" ----------
    @Test
    fun `check failure is unknown and never claims up to date`() {
        val c =
            computeUpstreamCompatibility(
                latestUpstream = null,
                updateData = null,
                checkState = UpstreamCheckState.ERROR,
            )
        assertFalse(c.compatible, "an errored check must not be compatible")
        assertTrue(c.statusLabel.contains("Impossible de vérifier"))
        assertFalse(c.statusLabel.contains("À jour"))
        assertFalse(c.statusLabel.contains("Compatible"))
    }

    @Test
    fun `not yet checked is also unknown`() {
        val c =
            computeUpstreamCompatibility(
                latestUpstream = null,
                updateData = null,
                checkState = UpstreamCheckState.NOT_CHECKED,
            )
        assertFalse(c.compatible)
        assertEquals("Pas encore vérifié", c.statusLabel)
    }

    // ---------- Spec test 5: dev/nightly tags are ignored for ordering ----------
    @Test
    fun `pre-release and nightly tags sort after their release`() {
        // parseVersion strips "-beta.1" / "-nightly" for ordering, so a nightly of
        // 2.0.0 never sorts as "newer than 2.0.0" and never beats a real 2.1.0.
        // Compare the numeric triple, not the whole Version (raw keeps the suffix).
        val v200 = parseVersion("v2.0.0")
        val v200Nightly = parseVersion("v2.0.0-nightly.123")
        val v210 = parseVersion("v2.1.0")
        assertTrue(v200 != null && v200Nightly != null && v210 != null)
        assertEquals(
            0,
            v200!!.compareTo(v200Nightly!!),
            "nightly of the same release equals the release numerically",
        )
        assertTrue(v210!! > v200Nightly, "a real newer release beats the nightly")
    }

    // ---------- Semantic compare: re-cut tag must NOT trigger a false update ----------
    @Test
    fun `re-cut tag is not newer than the same version`() {
        assertFalse(isVersionNewer("v0.3.1-1", "v0.3.1"), "re-cut tag is the same version")
        assertFalse(isVersionNewer("v0.3.1", "v0.3.1"))
        assertTrue(isVersionNewer("v0.3.1", "v0.3.0"), "a real bump is newer")
        assertFalse(isVersionNewer(null, "v0.3.1"))
        // An unparseable/absent reference is treated as older: the candidate may
        // still be offered (never a silent downgrade in the other direction).
        assertTrue(isVersionNewer("v0.3.1", null))
        assertTrue(isVersionNewer("v0.3.1", "not-a-version"))
    }

    // ---------- Base/Release must never be conflated ----------
    @Test
    fun `latest release reported is the dynamic value not the base`() {
        val c =
            computeUpstreamCompatibility(
                latestUpstream = "v2.0.0",
                basedOn = "2.0.0",
            )
        assertEquals("2.0.0", c.basedOnUpstream)
        assertEquals("v2.0.0", c.latestUpstream)
        // When they differ (future: release 2.1.0), the base stays put:
        val d =
            computeUpstreamCompatibility(
                latestUpstream = "v2.1.0",
                basedOn = "2.0.0",
            )
        assertEquals("2.0.0", d.basedOnUpstream)
        assertEquals("v2.1.0", d.latestUpstream)
        assertFalse(d.compatible)
    }
}
