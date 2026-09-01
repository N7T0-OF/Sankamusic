package com.maxrave.simpmusic.spacekai

import com.maxrave.domain.data.model.update.UpdateData

/**
 * SPACEKAI FEATURE: Compatibility Matrix between the installed SpaceKai layer and
 * the latest SimpMusic (upstream) OFFICIAL release.
 *
 * Model chosen with the "one app, SpaceKai replaces SimpMusic" architecture:
 *   - SpaceKai IS the installed app (`com.maxrave.simpmusic`, SpaceKai signing key).
 *   - SimpMusic is the upstream base; SpaceKai is rebuilt on top of a specific
 *     upstream version (`SPACEKAI_BASED_ON_UPSTREAM`).
 *   - The upstream APK is NEVER downloaded or installed over SpaceKai (different
 *     signing key → either refused or it would replace SpaceKai). We only report
 *     the newest SimpMusic release and whether the installed layer still runs on it.
 *
 * "Base intégrée" and "Dernière release officielle" are TWO DIFFERENT things:
 *   - `basedOnUpstream` is the upstream version this SpaceKai build was compiled
 *     against (a build-time constant, e.g. "2.0.0").
 *   - `latestUpstream` is what GitHub's `/releases/latest` reports TODAY (fetched
 *     dynamically — never hardcoded).
 *
 * A SpaceKai build is considered COMPATIBLE with the latest upstream when the
 * upstream release falls within the tested range declared by this build:
 *   [SPACEKAI_BASED_ON_UPSTREAM .. SPACEKAI_MAX_TESTED_UPSTREAM].
 * Use OPT_IN upgrades for a newer base: it only counts once the SpaceKai layer
 * has actually been rebuilt on top of it.
 *
 * HONESTY RULES (see scripts/audit-updater-flow.sh + docs/UPSTREAM.md):
 *   - An unknown / errored / not-yet-run check NEVER claims "compatible" or
 *     "up to date". Network failure, rate limit (403/429) and timeout are shown
 *     as "Impossible de vérifier actuellement" — never as a false "à jour".
 *   - When the integrated base is NEWER than the official release (e.g. base
 *     2.0.0 vs release 1.7.0) SpaceKai is "already newer" — NO update offered,
 *     NO downgrade suggested, and above all NOT a false "⚠ new version".
 *   - Only GitHub PUBLISHED releases count. Branches, milestones, commits,
 *     pull requests, workflows and nightlies are never treated as releases.
 */
enum class UpstreamCheckState {
    /** No check has run yet in this session and no cached value exists. */
    NOT_CHECKED,

    /** A check is currently in flight. */
    CHECKING,

    /** GitHub answered successfully; [UpstreamCompatibility.latestUpstream] is fresh. */
    OK,

    /** The last check failed (offline, rate limit, timeout, API unavailable). */
    ERROR,
}

data class UpstreamCompatibility(
    /** The SimpMusic release this SpaceKai build is based on (e.g. "2.0.0"). */
    val basedOnUpstream: String,
    /** Highest SimpMusic release tested against this SpaceKai build. */
    val maxTestedUpstream: String,
    /** Latest SimpMusic release reported by GitHub, without leading "v" (null = not checked / error). */
    val latestUpstream: String?,
    /** How the latest check ended (drives the honest label). */
    val checkState: UpstreamCheckState,
    /** True ONLY when a successful check found latestUpstream inside the tested range. */
    val compatible: Boolean,
    /** Human label: "✓ À jour", "⚠ Nouvelle release détectée", "✓ base plus récente" or "Impossible de vérifier". */
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
 * True when [candidate] is a NEWER release than [reference], compared semantically
 * (not by string inequality). "v0.3.1" vs "v0.3.1-1" (a re-cut tag) compare EQUAL
 * so a re-cut never triggers a false update; "v0.3.0" < "v0.3.1" does.
 *
 * Used by the SpaceKai update row so the installed/latest comparison is by
 * version, not by tag string.
 */
fun isVersionNewer(candidate: String?, reference: String?): Boolean {
    val c = parseVersion(candidate) ?: return false
    val r = parseVersion(reference) ?: return true // unparseable reference ⇒ treat as older
    return c > r
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
 * @param checkState how the last check ended (defaults to OK when a tag is
 *   present, NOT_CHECKED otherwise).
 */
fun computeUpstreamCompatibility(
    latestUpstream: String?,
    updateData: UpdateData? = null,
    basedOn: String = SPACEKAI_BASED_ON_UPSTREAM,
    maxTested: String = SPACEKAI_BASED_ON_UPSTREAM,
    checkState: UpstreamCheckState =
        if (latestUpstream.isNullOrBlank()) UpstreamCheckState.NOT_CHECKED else UpstreamCheckState.OK,
): UpstreamCompatibility {
    val latest = if (latestUpstream.isNullOrBlank()) updateData?.tagName else latestUpstream
    // SPACEKAI FIX: `latest` keeps its raw GitHub form ("v2.1.0" — the field's own
    // tests pin that), but the statusLabel must not double-prefix it: "(v$latest)"
    // rendered "(v v2.1.0)". Strip the leading "v" for display only.
    val latestDisplay = latest?.removePrefix("v")?.trim()
    val latestVersion = parseVersion(latest)

    // UNKNOWN / ERROR: never claim "compatible" or "à jour". A missing answer is
    // not a green light — it is an unanswered question.
    if (latestVersion == null || checkState == UpstreamCheckState.ERROR) {
        return UpstreamCompatibility(
            basedOnUpstream = basedOn,
            maxTestedUpstream = maxTested,
            latestUpstream = latest,
            checkState = checkState,
            compatible = false,
            statusLabel =
                when (checkState) {
                    UpstreamCheckState.ERROR ->
                        "Impossible de vérifier actuellement (réseau ou API GitHub)"
                    UpstreamCheckState.CHECKING -> "Vérification…"
                    else -> "Pas encore vérifié"
                },
        )
    }

    val baseVersion = parseVersion(basedOn)
    val maxVersion = parseVersion(maxTested)

    // BASE NEWER THAN RELEASE (e.g. base 2.0.0 vs release 1.7.0): SpaceKai is
    // already ahead of the official line. No update, no downgrade, and NOT a
    // "new version detected" warning.
    if (baseVersion != null && latestVersion < baseVersion) {
        return UpstreamCompatibility(
            basedOnUpstream = basedOn,
            maxTestedUpstream = maxTested,
            latestUpstream = latest,
            checkState = checkState,
            compatible = true,
            statusLabel =
                "✓ SpaceKai utilise déjà une base plus récente que la dernière release officielle",
        )
    }

    val inRange = maxVersion == null || latestVersion <= maxVersion
    return UpstreamCompatibility(
        basedOnUpstream = basedOn,
        maxTestedUpstream = maxTested,
        latestUpstream = latest,
        checkState = checkState,
        compatible = inRange,
        statusLabel =
            if (inRange) {
                "✓ À jour avec la dernière release officielle"
            } else {
                "⚠ Nouvelle release officielle détectée (v$latestDisplay) — SpaceKai pas encore compatible"
            },
    )
}

/** Latest upstream version display (strips a leading "v", null-safe). */
fun displayUpstreamVersion(tag: String?): String {
    val p = parseVersion(tag) ?: return tag ?: "—"
    return p.raw
}
