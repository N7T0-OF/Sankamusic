package com.sankamusic.themes.exampletheme

import com.sankamusic.core.ThemeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validation du thème d'exemple (Phase 3) : définition valide, couleurs dans
 * la plage ARGB, et activation à travers le ThemeEngine.
 */
class ExampleThemeTest {

    @Test
    fun `definition is valid`() {
        assertTrue(ExampleTheme.definition.validationErrors().isEmpty())
    }

    @Test
    fun `theme activates through the engine`() {
        val engine = ThemeEngine()
        assertTrue(engine.register(ExampleTheme.definition).isSuccess)
        val tokens = engine.activate(ExampleTheme.ID).getOrNull()
        assertEquals(ExampleTheme.definition.tokens, tokens)
        assertEquals("dark", engine.active()?.base)
    }

    @Test
    fun `amoled theme activates and merges over the dark base`() {
        val engine = ThemeEngine()
        assertTrue(engine.register(AmoledTheme.definition).isSuccess)
        val tokens = engine.activate(AmoledTheme.ID).getOrNull()!!
        // Couche AMOLED : fond/surfaces noirs purs.
        assertEquals(0xFF000000L, tokens.background)
        assertEquals(0xFF000000L, tokens.surface)
        // Base sombre conservée sur les champs non surchargés.
        assertEquals(0xFFBB86FCL, tokens.primary)
        assertEquals(12f, tokens.radiusCard)
    }

    @Test
    fun `colors are valid ARGB values`() {
        val t = ExampleTheme.definition.tokens
        val colors = listOf(t.primary, t.onPrimary, t.secondary, t.background, t.surface, t.onSurface, t.error)
        colors.forEach { color ->
            assertTrue("couleur hors plage ARGB : $color", color in 0L..0xFFFFFFFFL)
        }
    }

    @Test
    fun `dimensions are non-negative`() {
        val t = ExampleTheme.definition.tokens
        val dimensions = listOf(t.surfaceElevation, t.surfaceOpacity, t.radiusCard, t.radiusLarge, t.titleSize, t.bodySize, t.playerOverlayOpacity)
        dimensions.forEach { d ->
            assertTrue("dimension négative : $d", d >= 0f)
        }
    }
}
