package com.maxrave.simpmusic.spacekai

import androidx.compose.runtime.mutableStateOf
import com.maxrave.simpmusic.BuildKonfig
import com.maxrave.simpmusic.utils.VersionManager
import com.maxrave.simpmusic.spacekai.features.haptics.HapticManager
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.logger.Logger

// SPACEKAI FEATURE
//
// SpaceKai is an add-on layer over SimpMusic. This file is the single startup
// hook: the app hands the layer its feature flags via `configSpaceKai(...)`
// exactly like `configLastfm(key, secret)` / `configCrashlytics(context, dsn)`.
// Everything SpaceKai does is gated behind these flags, so an upstream merge
// that never calls `configSpaceKai` simply behaves as vanilla SimpMusic.

private const val TAG = "SpaceKai"

/**
 * The SimpMusic upstream release this SpaceKai build is BASED ON (the integrated
 * base — a build-time constant, NOT the "latest available"). The latest OFFICIAL
 * SimpMusic release is fetched dynamically from GitHub at runtime by
 * `checkForUpstreamRelease()` — never hardcode it here.
 * Bump only when SpaceKai is actually rebuilt on top of a newer upstream.
 */
/**
 * The SimpMusic upstream release this SpaceKai build is BASED ON — DERIVED from
 * upstream.lock (the single source of truth maintained by scripts/update-upstream.sh)
 * via BuildKonfig.upstreamBaseVersion, never a hardcoded constant. Falls back to the
 * last known base (2.0.0) only when the lock/build field is absent (vanilla build).
 * Last sync: 2026-08-27, upstream v2.0.0 — see upstream.lock and docs/UPSTREAM.md.
 */
val SPACEKAI_BASED_ON_UPSTREAM: String =
    try {
        BuildKonfig.upstreamBaseVersion.ifBlank { "2.0.0" }
    } catch (_: Exception) {
        "2.0.0"
    }

/**
 * The SpaceKai release version, DERIVED from the real build (BuildKonfig.versionName,
 * i.e. `version-name` in libs.versions.toml) — never a hardcoded constant. A hardcoded
 * value drifted from the build before (0.3.1 vs a 0.3.2 build) and made the Updates
 * screen show a wrong "Installée" version and offer a phantom update to itself.
 * VersionManager strips the "-dev" suffix so a dev build compares against GitHub tags.
 */
val SPACEKAI_VERSION: String = VersionManager.getVersionName()

// The configured feature set. `null` = no config was handed in (vanilla).
//
// REACTIVITY: `configured` is a plain module-level variable, so writing it does
// NOT notify Compose — a `@Composable` that reads `isSpaceKaiFeatureEnabled`
// would keep the stale value until an unrelated recomposition, which made the
// Settings toggles look like they did nothing. `configurationEpoch` is a
// snapshot-backed counter bumped by `configSpaceKai`; `isSpaceKaiFeatureEnabled`
// reads it inside a Compose read, so every flag reader is subscribed to config
// changes and recomposes the moment a toggle flips.
private var configured: SpaceKaiFeatures? = null
private var configurationEpoch = mutableStateOf(0)

/**
 * Hands the SpaceKai layer its feature flags.
 *
 * Call once at startup, before any SpaceKai-gated UI is composed. Calling it
 * again replaces the set (useful for tests / per-build overrides).
 */
fun configSpaceKai(
    features: SpaceKaiFeatures = SpaceKaiFeatures.defaults,
) {
    configured = features
    configurationEpoch.value += 1
    Logger.d(
        TAG,
        "SpaceKai configured: ${features.toString().removePrefix("SpaceKaiFeatures(").removeSuffix(")")}",
    )
}

/**
 * Whether the SpaceKai layer is active at all.
 *
 * False only when `configSpaceKai` was never called (upstream vanilla build).
 * The section behind this must stay reachable even with every flag off:
 * flags are now persisted, so a user who disables all of them would otherwise
 * make the Settings section vanish with no way back.
 */
fun isSpaceKaiAvailable(): Boolean {
    // Read the epoch inside a Compose read so flag readers subscribe to config changes.
    configurationEpoch.value
    return configured != null
}

fun currentFeatures(): SpaceKaiFeatures {
    configurationEpoch.value
    return configured ?: SpaceKaiFeatures.defaults
}

/**
 * DataStore key prefix for SpaceKai feature flags (generic string store).
 * Kept here (not in the UI section) so both the startup merge and the
 * settings section read/write the same keys.
 */
const val SPACEKAI_FLAG_PREFIX = "spacekai_"

/**
 * Merges the persisted SpaceKai feature flags over [base].
 *
 * The merge rule: a stored "true"/"false" for a flag (the user's explicit
 * choice) wins over the build-time default; an absent key keeps the default.
 * This is the single source of truth used by both the startup hook and the
 * Settings section, so they can never drift apart.
 *
 * @param getString reads a generic DataStore string key (SharedViewModel.getString).
 */
fun mergePersistedSpaceKaiFeatures(
    base: SpaceKaiFeatures,
    getString: (String) -> String?,
): SpaceKaiFeatures {
    fun flag(key: String, fallback: Boolean): Boolean =
        getString("$SPACEKAI_FLAG_PREFIX$key")?.let { it == DataStoreManager.TRUE } ?: fallback

    return SpaceKaiFeatures(
        customNavigation = flag("custom_navigation", base.customNavigation),
        minimalisticNavigation = flag("minimalistic_navigation", base.minimalisticNavigation),
        dynamicColor = flag("dynamic_color", base.dynamicColor),
        haptics = flag("haptics", base.haptics),
        customPlayerInfo = flag("custom_player_info", base.customPlayerInfo),
        landscapePlayer = flag("landscape_player", base.landscapePlayer),
    )
}

/**
 * Loads the persisted SpaceKai feature flags and re-issues `configSpaceKai`
 * with them merged over the build-time defaults.
 *
 * Call once at startup (App/DesktopApp). This makes user toggles survive a
 * restart without the SpaceKai layer needing typed DataStore keys.
 *
 * @param getString reads a generic DataStore string key (SharedViewModel.getString).
 */
fun applyPersistedSpaceKaiFeatures(
    getString: (String) -> String?,
) {
    configSpaceKai(mergePersistedSpaceKaiFeatures(currentFeatures(), getString))
    // Restore the persisted haptics intensity (separate from the Boolean flags).
    HapticManager.applyPersisted(getString)
}