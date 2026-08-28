package com.sankamusic.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests du Dynamic Color (docs/MIGRATION.md étape 6). */
class DynamicColorTest {

    // ── Décision de stratégie de palette ────────────────────────────────

    @Test
    fun `wallpaper source with platform support uses dynamic colors`() {
        assertEquals(
            ColorSchemeStrategy.WALLPAPER_DYNAMIC,
            resolveColorSchemeStrategy(ThemeColorSource.WALLPAPER, dynamicColorSupported = true),
        )
    }

    @Test
    fun `wallpaper source without support falls back to seed palette`() {
        // Android < 12 : jamais d'écran cassé, repli sûr sur la palette par graine.
        assertEquals(
            ColorSchemeStrategy.SEED_GENERATED,
            resolveColorSchemeStrategy(ThemeColorSource.WALLPAPER, dynamicColorSupported = false),
        )
    }

    @Test
    fun `default and custom sources always use seed palette`() {
        assertEquals(
            ColorSchemeStrategy.SEED_GENERATED,
            resolveColorSchemeStrategy(ThemeColorSource.DEFAULT, dynamicColorSupported = true),
        )
        assertEquals(
            ColorSchemeStrategy.SEED_GENERATED,
            resolveColorSchemeStrategy(ThemeColorSource.CUSTOM, dynamicColorSupported = true),
        )
    }

    // ── Graine effective ────────────────────────────────────────────────

    @Test
    fun `custom source exposes the custom seed`() {
        assertEquals(0xFF8ECAE6L, effectiveSeedColor(ThemeColorSource.CUSTOM, 0xFF8ECAE6L))
    }

    @Test
    fun `default and wallpaper sources leave seed to the app default`() {
        assertNull(effectiveSeedColor(ThemeColorSource.DEFAULT, 0xFF8ECAE6L))
        assertNull(effectiveSeedColor(ThemeColorSource.WALLPAPER, 0xFF8ECAE6L))
    }

    // ── Règle OLED ──────────────────────────────────────────────────────

    @Test
    fun `oled pinning pins background and surface to pure black in dark`() {
        val dark = SpaceKaiThemeTokens().withOledPinning(isDark = true)
        assertEquals(0xFF000000L, dark.background)
        assertEquals(0xFF000000L, dark.surface)
        // Les autres tokens sont conservés.
        assertEquals(0xFF6750A4L, dark.primary)
        assertEquals(12f, dark.radiusCard)
    }

    @Test
    fun `oled pinning leaves light tokens unchanged`() {
        val light = SpaceKaiThemeTokens(background = 0xFFFFFBFE, surface = 0xFFFFFBFE)
        assertEquals(light, light.withOledPinning(isDark = false))
    }
}
