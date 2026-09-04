package com.maxrave.simpmusic.spacekai

/**
 * SpaceKai feature flags.
 *
 * SpaceKai is an add-on layer over SimpMusic. Every SpaceKai customization is
 * gated behind one of these flags so features stay isolated, can be toggled
 * per build, and survive upstream merges (a flag that is off is dead code that
 * never conflicts with upstream).
 *
 * Flags are handed in at startup via `configSpaceKai(...)` — the same shape as
 * `configLastfm` / `configCrashlytics`. A FOSS-style build (or a build that
 * does not call `configSpaceKai`) gets the [defaults], i.e. vanilla SimpMusic
 * behaviour with every SpaceKai feature off.
 *
 * To add a feature:
 *   1. Add a flag here (with a sensible default).
 *   2. Guard the SpaceKai code behind `isSpaceKaiFeatureEnabled(SpaceKaiFeatures::flag)`.
 *   3. Mark the hook point in `docs/SPACEKAI-ARCHITECTURE.md`.
 */
data class SpaceKaiFeatures(
    /** Custom bottom navigation (tabs, order, icons). */
    val customNavigation: Boolean = false,

    /** Minimalistic navigation variant (icons-only, compact — same destinations). */
    val minimalisticNavigation: Boolean = false,

    /** SpaceKai dynamic colour overrides on top of the upstream theme. */
    val dynamicColor: Boolean = false,

    /** Haptic feedback on player / navigation interactions. */
    val haptics: Boolean = false,

    /** Custom player info line (artist · title · album) rendering. */
    val customPlayerInfo: Boolean = false,

    /** Landscape-aware Now Playing layout (side-by-side composition). */
    val landscapePlayer: Boolean = false,
) {
    companion object {
        /** Vanilla SimpMusic: every SpaceKai feature off. */
        val defaults = SpaceKaiFeatures()

        /** Build-time "everything on" for local development. */
        val allEnabled =
            SpaceKaiFeatures(
                customNavigation = true,
                minimalisticNavigation = true,
                dynamicColor = true,
                haptics = true,
                customPlayerInfo = true,
                landscapePlayer = true,
            )
    }
}

/**
 * Whether a specific feature flag is on.
 *
 * `feature: KProperty1<SpaceKaiFeatures, Boolean>` lets callers write
 * `isSpaceKaiFeatureEnabled(SpaceKaiFeatures::haptics)`. Passing a flag whose
 * default is `true` is fine — the configured value always wins.
 */
fun isSpaceKaiFeatureEnabled(
    feature: kotlin.reflect.KProperty1<SpaceKaiFeatures, Boolean>,
): Boolean = feature.get(currentFeatures())
