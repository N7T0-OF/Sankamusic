package com.sankamusic.core

import com.sankamusic.core.api.SpaceKaiThemeTokens
import com.sankamusic.core.api.ThemeColorSource
import com.sankamusic.core.api.ThemeDefinition
import com.sankamusic.core.api.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeEngineTest {

    private fun theme(
        id: String = "test.theme",
        base: String = "dark",
        tokens: SpaceKaiThemeTokens = SpaceKaiThemeTokens(),
    ) = ThemeDefinition(
        id = id,
        name = "Test Theme",
        version = "1.0.0",
        apiVersion = 1,
        base = base,
        tokens = tokens,
    )

    // ── Enregistrement / activation ─────────────────────────────────────

    @Test
    fun `register valid theme then activate returns its tokens`() {
        val engine = ThemeEngine()
        assertTrue(engine.register(theme()).isSuccess)
        val tokens = engine.activate("test.theme").getOrNull()
        // Thème sans personnalisation sur base sombre → tokens de la base sombre.
        assertEquals(0xFF121212L, tokens?.background)
        assertEquals("test.theme", engine.active()?.id)
    }

    @Test
    fun `register invalid theme is rejected`() {
        val engine = ThemeEngine()
        val bad = ThemeDefinition(
            id = "",
            name = "",
            version = "",
            apiVersion = 0,
            base = "neon",
            tokens = SpaceKaiThemeTokens(),
        )
        assertTrue(engine.register(bad).isFailure)
    }

    @Test
    fun `duplicate register is rejected`() {
        val engine = ThemeEngine()
        assertTrue(engine.register(theme()).isSuccess)
        assertTrue(engine.register(theme()).isFailure)
    }

    @Test
    fun `activate unknown theme fails`() {
        val engine = ThemeEngine()
        assertTrue(engine.activate("unknown").isFailure)
        assertNull(engine.active())
    }

    @Test
    fun `active returns last activated theme`() {
        val engine = ThemeEngine()
        engine.register(theme("a"))
        engine.register(theme("b"))
        engine.activate("b")
        assertEquals("b", engine.active()?.id)
    }

    // ── Modèle « base + couche » (overlay) ──────────────────────────────

    @Test
    fun `activate merges theme tokens over the dark base`() {
        val engine = ThemeEngine()
        engine.register(
            theme(
                id = "amoled",
                tokens = SpaceKaiThemeTokens(background = 0xFF000000, surface = 0xFF000000),
            ),
        )
        val tokens = engine.activate("amoled").getOrNull()!!
        // Personnalisations appliquées…
        assertEquals(0xFF000000L, tokens.background)
        assertEquals(0xFF000000L, tokens.surface)
        // …et le reste de la base sombre est conservé (champs non surchargés).
        assertEquals(0xFFBB86FCL, tokens.primary)
        assertEquals(0xFFE0E0E0L, tokens.onSurface)
        assertEquals(12f, tokens.radiusCard)
    }

    @Test
    fun `activate on light base keeps the light palette`() {
        val engine = ThemeEngine()
        engine.register(
            theme(
                id = "accent",
                base = "light",
                tokens = SpaceKaiThemeTokens(primary = 0xFF000000),
            ),
        )
        val tokens = engine.activate("accent").getOrNull()!!
        assertEquals(0xFF000000L, tokens.primary)
        // Fond de la base claire conservé (l'overlay ne touche que primary).
        assertEquals(0xFFFFFBFEL, tokens.background)
    }

    // ── Réglages : mode et source de couleur (port SpaceKai-OLD) ────────

    @Test
    fun `mode defaults to dark and can be changed`() {
        val engine = ThemeEngine()
        assertEquals(ThemeMode.DARK, engine.mode())
        engine.setMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, engine.mode())
        engine.setMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, engine.mode())
    }

    @Test
    fun `color source defaults to default seed`() {
        val engine = ThemeEngine()
        assertEquals(ThemeColorSource.DEFAULT, engine.colorSource())
        assertNull(engine.customSeedColor())
    }

    @Test
    fun `custom color source stores the seed`() {
        val engine = ThemeEngine()
        val result = engine.setColorSource(ThemeColorSource.CUSTOM, 0xFF8ECAE6)
        assertTrue(result.isSuccess)
        assertEquals(ThemeColorSource.CUSTOM, engine.colorSource())
        assertEquals(0xFF8ECAE6L, engine.customSeedColor())
    }

    @Test
    fun `custom color source without seed fails cleanly`() {
        val engine = ThemeEngine()
        val result = engine.setColorSource(ThemeColorSource.CUSTOM, null)
        assertTrue(result.isFailure)
        // L'état reste inchangé : la source n'a pas basculé sur CUSTOM.
        assertEquals(ThemeColorSource.DEFAULT, engine.colorSource())
        assertNull(engine.customSeedColor())
    }

    @Test
    fun `switching source resets the custom seed`() {
        val engine = ThemeEngine()
        engine.setColorSource(ThemeColorSource.CUSTOM, 0xFF8ECAE6)
        assertTrue(engine.setColorSource(ThemeColorSource.WALLPAPER).isSuccess)
        assertEquals(ThemeColorSource.WALLPAPER, engine.colorSource())
        assertNull(engine.customSeedColor())
    }
}
