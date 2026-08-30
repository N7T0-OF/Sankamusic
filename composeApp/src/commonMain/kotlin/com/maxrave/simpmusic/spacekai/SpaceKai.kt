package com.maxrave.simpmusic.spacekai

import androidx.compose.runtime.mutableStateOf
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

/** The SimpMusic upstream release this SpaceKai build is based on. */
// Verified 2026-08-26: upstream maxrave-dev/SimpMusic latest tag is v1.7.0 — the
// SpaceKai 1.7.x/1.8.x releases all sit on top of it. Bump when a newer upstream ships.
const val SPACEKAI_BASED_ON_UPSTREAM: String = "1.7.0"

/** The SpaceKai release version (aligned with `version-name` in libs.versions.toml). */
const val SPACEKAI_VERSION: String = "0.2.1"

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
}