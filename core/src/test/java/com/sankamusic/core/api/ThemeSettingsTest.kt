package com.sankamusic.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests de [parseThemeColorHex] — port de SpaceKai-OLD (ui/theme/Theme.kt). */
class ParseThemeColorHexTest {

    @Test
    fun `parses 6 digit RGB with alpha FF`() {
        assertEquals(0xFFFF0000L, parseThemeColorHex("FF0000"))
    }

    @Test
    fun `parses hash prefixed hex`() {
        assertEquals(0xFF000000L, parseThemeColorHex("#000000"))
    }

    @Test
    fun `parses 8 digit ARGB`() {
        assertEquals(0x808ECAE6L, parseThemeColorHex("808ECAE6"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(0xFF8ECAE6L, parseThemeColorHex("  8ECAE6  "))
    }

    @Test
    fun `rejects malformed input`() {
        assertNull(parseThemeColorHex(""))
        assertNull(parseThemeColorHex("xyz"))
        assertNull(parseThemeColorHex("12345"))
        assertNull(parseThemeColorHex("1234567"))
        assertNull(parseThemeColorHex("#GGGGGG"))
    }
}

/** Tests de la fusion [SpaceKaiThemeTokens.overlay] — modèle « base + couche ». */
class SpaceKaiThemeTokensOverlayTest {

    @Test
    fun `empty overlay returns the base unchanged`() {
        val base = SpaceKaiThemeTokens(background = 0xFF121212, primary = 0xFFBB86FC)
        assertEquals(base, base.overlay(SpaceKaiThemeTokens()))
    }

    @Test
    fun `overlay applies only explicitly set fields`() {
        val base = SpaceKaiThemeTokens(background = 0xFF121212, surface = 0xFF1E1E1E)
        val overlay = SpaceKaiThemeTokens(background = 0xFF000000)
        val merged = base.overlay(overlay)
        assertEquals(0xFF000000L, merged.background)
        assertEquals(0xFF1E1E1EL, merged.surface) // non touché → base conservée
    }

    @Test
    fun `overlay is not commutative and the layer wins`() {
        val base = SpaceKaiThemeTokens(primary = 0xFF000000)
        val theme = SpaceKaiThemeTokens(primary = 0xFFFFFFFF)
        // `base.overlay(couche)` : les valeurs explicites de la couche l'emportent.
        assertEquals(0xFFFFFFFFL, base.overlay(theme).primary)
        assertEquals(0xFF000000L, theme.overlay(base).primary)
    }

    @Test
    fun `overlay keeps non-overridden navigation and typography`() {
        val base = SpaceKaiThemeTokens(navigationStyle = "minimalistic", titleSize = 20f)
        val theme = SpaceKaiThemeTokens(radiusCard = 16f)
        val merged = base.overlay(theme)
        assertEquals("minimalistic", merged.navigationStyle)
        assertEquals(20f, merged.titleSize)
        assertEquals(16f, merged.radiusCard)
    }
}
