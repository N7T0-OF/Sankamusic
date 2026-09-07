package com.sankamusic.core.settings

import com.sankamusic.core.api.SettingsKeys
import com.sankamusic.core.api.ThemeColorSource
import com.sankamusic.core.api.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests des préférences de thème persistées (MIGRATION.md étape 7 — persistance réelle). */
class ThemePreferencesTest {

    @Test
    fun `mode and color source round trip through typed settings`() {
        val typed = TypedSettings(FakeSettings())
        typed.set(themeModePreference, ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, typed.get(themeModePreference))
        typed.set(themeColorSourcePreference, ThemeColorSource.WALLPAPER)
        assertEquals(ThemeColorSource.WALLPAPER, typed.get(themeColorSourcePreference))
    }

    @Test
    fun `parse is tolerant to case and corrupted values fall back to defaults`() {
        val store = FakeSettings(
            mapOf(
                SettingsKeys.THEME_MODE to "dark",
                SettingsKeys.THEME_COLOR_SOURCE to "N'importe quoi",
            ),
        )
        val typed = TypedSettings(store)
        assertEquals(ThemeMode.DARK, typed.get(themeModePreference))
        assertEquals(ThemeColorSource.DEFAULT, typed.get(themeColorSourcePreference))
    }

    @Test
    fun `seed color serializes and parses round trip`() {
        val seed = 0xFF7C4DFFL
        val stored = themeSeedColorToPreferenceValue(seed)
        assertEquals("#7C4DFF", stored)
        assertEquals(seed, themeSeedColorFromPreferenceValue(stored))
        // Le canal alpha éventuel n'altère pas la relecture.
        assertEquals(0x80123456L and 0xFFFFFFL, themeSeedColorFromPreferenceValue(themeSeedColorToPreferenceValue(0x80123456L)))
    }

    @Test
    fun `seed color parse is safe on absent or invalid values`() {
        assertNull(themeSeedColorFromPreferenceValue(null))
        assertNull(themeSeedColorFromPreferenceValue(""))
        assertNull(themeSeedColorFromPreferenceValue("   "))
        assertNull(themeSeedColorFromPreferenceValue("#couleur"))
        assertEquals(0xFF7C4DFFL, themeSeedColorFromPreferenceValue("7C4DFF"))
        assertEquals(0xFF7C4DFFL, themeSeedColorFromPreferenceValue("#7C4DFF"))
    }

    @Test
    fun `seed preference persists through typed settings`() {
        val store = FakeSettings()
        val typed = TypedSettings(store)
        typed.set(themeSeedColorPreference, themeSeedColorToPreferenceValue(0xFF00AAEFL))
        assertEquals("#00AAEF", store.map[SettingsKeys.THEME_SEED_COLOR])
        assertEquals(0xFF00AAEFL, themeSeedColorFromPreferenceValue(typed.get(themeSeedColorPreference)))
    }
}
