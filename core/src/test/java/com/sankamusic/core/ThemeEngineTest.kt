package com.sankamusic.core

import com.sankamusic.core.api.SpaceKaiThemeTokens
import com.sankamusic.core.api.ThemeDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeEngineTest {

    private fun theme(id: String = "test.theme") = ThemeDefinition(
        id = id,
        name = "Test Theme",
        version = "1.0.0",
        apiVersion = 1,
        base = "dark",
        tokens = SpaceKaiThemeTokens(),
    )

    @Test
    fun `register valid theme then activate returns its tokens`() {
        val engine = ThemeEngine()
        assertTrue(engine.register(theme()).isSuccess)
        val tokens = engine.activate("test.theme").getOrNull()
        assertEquals(SpaceKaiThemeTokens(), tokens)
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
}
