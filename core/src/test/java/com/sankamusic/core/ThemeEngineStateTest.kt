package com.sankamusic.core

import com.sankamusic.core.api.SpaceKaiThemeTokens
import com.sankamusic.core.api.ThemeColorSource
import com.sankamusic.core.api.ThemeDefinition
import com.sankamusic.core.api.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests de l'état réactif du [ThemeEngine] (StateFlow consommé par l'UI). */
class ThemeEngineStateTest {

    private fun theme(id: String = "t", tokens: SpaceKaiThemeTokens = SpaceKaiThemeTokens()) =
        ThemeDefinition(id = id, name = "T", version = "1.0.0", apiVersion = 1, base = "dark", tokens = tokens)

    @Test
    fun `initial state is dark with default color source`() = runBlocking {
        val engine = ThemeEngine()
        val state = engine.state.first()
        assertEquals(ThemeMode.DARK, state.mode)
        assertEquals(ThemeColorSource.DEFAULT, state.colorSource)
        assertNull(state.customSeedColor)
        assertNull(state.activeTokens)
    }

    @Test
    fun `state flow reflects mode and color source changes`() = runBlocking {
        val engine = ThemeEngine()
        engine.setMode(ThemeMode.SYSTEM)
        engine.setColorSource(ThemeColorSource.CUSTOM, 0xFF8ECAE6)
        val state = engine.state.first()
        assertEquals(ThemeMode.SYSTEM, state.mode)
        assertEquals(ThemeColorSource.CUSTOM, state.colorSource)
        assertEquals(0xFF8ECAE6L, state.customSeedColor)
    }

    @Test
    fun `state flow exposes active theme tokens after activate`() = runBlocking {
        val engine = ThemeEngine()
        engine.register(theme(tokens = SpaceKaiThemeTokens(background = 0xFF000000)))
        engine.activate("t")
        val state = engine.state.first()
        // Base sombre + overlay : fond noir appliqué, primary de la base sombre conservée.
        assertEquals(0xFF000000L, state.activeTokens?.background)
        assertEquals(0xFFBB86FCL, state.activeTokens?.primary)
    }
}
