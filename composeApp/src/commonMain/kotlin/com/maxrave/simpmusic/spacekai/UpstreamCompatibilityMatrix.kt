package com.maxrave.simpmusic.spacekai

/**
 * SPACEKAI FEATURE: manifest of the SimpMusic (upstream) bases this SpaceKai
 * build supports — the compatibility source of truth, replacing the old
 * hardcoded "max tested upstream" ceiling.
 *
 * A release (stable or pre-release) is COMPATIBLE when its tag matches one of
 * these entries; `isTested` distinguishes "verified by the SpaceKai team on
 * this base" from "declared supported, awaiting device validation". When a
 * release matches no entry, SpaceKai reports "not yet compatible" and the
 * repo page is offered instead of a broken install.
 *
 * To follow a new SimpMusic release: rebuild SpaceKai on it, add the entry
 * here (one line), release. The updater never needs code changes again.
 */
data class UpstreamBase(
    val tag: String,
    val isTested: Boolean,
)

object UpstreamCompatibilityMatrix {

    /**
     * Bases supported by this build. 2.0.0 is tested (the base this code
     * currently ships on); 2.1.0 is declared supported pending device
     * validation of the next release. Re-cut/suffixed tags (e.g.
     * "v2.1.0-beta.2", "v2.0.0-1") resolve to their nearest declared base.
     */
    val bases: List<UpstreamBase> =
        listOf(
            UpstreamBase(tag = "2.0.0", isTested = true),
            UpstreamBase(tag = "2.1.0", isTested = false),
        )

    /**
     * Resolves a GitHub release tag (e.g. "v2.1.0", "2.0.0-beta.1") to the
     * declared base it belongs to, or null when it matches no declared base.
     * Matching is on the version triple only (pre-release suffixes ignored),
     * so a future v2.1.0 pre-release already resolves to the 2.1.0 entry.
     */
    fun resolve(releaseTag: String?): UpstreamBase? {
        val version = parseVersion(releaseTag) ?: return null
        return bases.firstOrNull { base ->
            parseVersion(base.tag)?.let { it.major == version.major && it.minor == version.minor && it.patch == version.patch } == true
        }
    }

    /** True when the release tag matches a declared base. */
    fun isCompatible(releaseTag: String?): Boolean = resolve(releaseTag) != null

    /**
     * True when the release matches a declared base that is still awaiting
     * device validation (used for the "compatible mais non encore validé"
     * nuance in the UI).
     */
    fun isUntested(releaseTag: String?): Boolean = resolve(releaseTag)?.isTested == false
}
