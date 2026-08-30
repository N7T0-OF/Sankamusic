package com.maxrave.simpmusic.expect.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** Wallpaper-based (Material You) color scheme, or null when the platform can't provide one. */
@Composable
expect fun platformDynamicColorScheme(isDark: Boolean): ColorScheme?

/**
 * SPACEKAI FEATURE: dynamicColor — unpinned variant of [platformDynamicColorScheme].
 * Same system palette but WITHOUT dark background/surface being forced to black.
 * Only used when the SpaceKai dynamicColor flag is on.
 */
@Composable
expect fun platformDynamicColorSchemeUnpinned(isDark: Boolean): ColorScheme?

/** Whether this platform can derive a color scheme from the system wallpaper. */
expect fun isWallpaperDynamicColorSupported(): Boolean

/** Keeps the system bar icon appearance in sync with the app theme (Android only; no-op elsewhere). */
@Composable
expect fun SystemBarAppearanceEffect(isDark: Boolean)
