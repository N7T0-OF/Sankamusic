package com.maxrave.simpmusic.spacekai

import com.maxrave.domain.data.model.update.UpdateData

/**
 * SPACEKAI FEATURE: Compatibility Matrix between the installed SpaceKai layer and
 * the latest SimpMusic (upstream) release.
 *
 * Model chosen with the "one app, SpaceKai replaces SimpMusic" architecture:
 *   - SpaceKai IS the installed app (`com.maxrave.simpmusic`, SpaceKai signing key).
 *   - SimpMusic is the upstream base; SpaceKai is rebuilt on top of a specific
 *     upstream version (`SPACEKAI_BASED_ON_UPSTREAM`).
 *   - The upstream APK is NEVER downloaded or installed over SpaceKai (different
 *     signing key → either refused or it would replace SpaceKai). We only report
 *     the newest SimpMusic release and whether the installed layer still runs on it.
 *
 * A SpaceKai build is considered COMPATIBLE with the latest upstream when the
 * upstream release falls within the tested range declared by this build:
 *   [SPACEKAI_BASED_ON_UPSTREAM .. SPACEKAI_MAX_TESTED_UPSTREAM].
 * Use OPT_IN upgrades for a newer base: it only counts once the SpaceKai layer
 * has actually been rebuilt on top of it.
 */
data class UpstreamCompatibility(
    /** The SimpMusic release this SpaceKai build is based on (e.g. "1.7.0"). */
    val basedOnUpstream: String,
    /** Highest SimpMusic release tested against this SpaceKai build. */
    val maxTestedUpstream: String,
    /** Latest SimpMusic release reported by GitHub, without leading "v" (null = not checked / error). */
    val latestUpstream: String?,
    /** True when latestUpstream falls inside the tested range (or is unknown). */
    val compatible: Boolean,
    /** Human label: "✓ Compatible", "⚠ Nouvelle version détectée" or "— Pas encore vérifié". */
    val statusLabel: String,
)

/**
 * Parses a GitHub tag (e.g. "v2.0.0", "1.7.4", "2.0.0-beta.1") into a comparable
 * [revision] triple plus the raw version string. Non-numeric pre-release suffixes
 * are ignored for ordering.
 */
fun parseVersion(tag: String?): Version? {
    if (tag.isNullOrBlank()) return null
    val cleaned = tag.removePrefix("v").trim()
    // Take up to the first non-(digit|.) segment, then split on dots.
    val parts =
        cleaned.split("-", "_", "+").firstOrNull()?.split(".")?.mapNotNull { it.toIntOrNull() }
    if (parts == null || parts.isEmpty()) return null
    return Version(
        major = parts.getOrElse(0) { 0 },
        minor = parts.getOrElse(1) { 0 },
        patch = parts.getOrElse(2) { 0 },
        raw = cleaned,
    )
}

/** Comparable semantic-version-ish triple. */
data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val raw: String,
) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(
            this,
            other,
            { it.major },
            { it.minor },
            { it.patch },
        )
}

/**
 * Computes the compatibility state for the installed SpaceKai build against the
 * latest detected SimpMusic release.
 *
 * @param latestUpstream the latest upstream release tag (as reported by GitHub),
 *   e.g. "v2.0.0". Null when the check hasn't run or failed.
 * @param basedOn the base version declared by this build (defaults to
 *   [SPACEKAI_BASED_ON_UPSTREAM]).
 * @param maxTested highest SimpMusic release tested against this build
 *   (defaults to [SPACEKAI_BASED_ON_UPSTREAM] — a build has only verified its own base).
 */
fun computeUpstreamCompatibility(
    latestUpstream: String?,
    updateData: UpdateData? = null,
    basedOn: String = SPACEKAI_BASED_ON_UPSTREAM,
    maxTested: String = SPACEKAI_BASED_ON_UPSTREAM,
): UpstreamCompatibility {
    val latest = if (latestUpstream.isNullOrBlank()) updateData?.tagName else latestUpstream
    val latestVersion = parseVersion(latest)
    if (latestVersion == null) {
        return UpstreamCompatibility(
            basedOnUpstream = basedOn,
            maxTestedUpstream = maxTested,
            latestUpstream = latest,
            compatible = true,
            statusLabel = "Pas encore vérifié",
        )
    }
    val maxVersion = parseVersion(maxTested)
    val minVersion = parseVersion(basedOn)
    val inRange =
        (minVersion == null || latestVersion >= minVersion) &&
            (maxVersion == null || latestVersion <= maxVersion)
    return UpstreamCompatibility(
        basedOnUpstream = basedOn,
        maxTestedUpstream = maxTested,
        latestUpstream = latest,
        compatible = inRange,
        statusLabel =
            if (inRange) {
                "✓ Compatible avec SpaceKai"
            } else {
                "⚠ Nouvelle version détectée — SpaceKai pas encore compatible"
            },
    )
}

/** Latest upstream version display (strips a leading "v", null-safe). */
fun displayUpstreamVersion(tag: String?): String {
    val p = parseVersion(tag) ?: return tag ?: "—"
    return p.raw
}