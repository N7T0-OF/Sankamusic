package com.sankamusic.core.api

import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeDefinitionTest {

    private fun valid() = ThemeDefinition(
        id = "com.example.theme",
        name = "Example Theme",
        version = "1.0.0",
        apiVersion = 1,
        base = "dark",
        tokens = SpaceKaiThemeTokens(),
    )

    @Test
    fun `valid theme has no errors`() {
        assertTrue(valid().validationErrors().isEmpty())
    }

    @Test
    fun `blank id and version are rejected`() {
        val errors = valid().copy(id = "", version = " ").validationErrors()
        assertTrue(errors.any { it.contains("id") })
        assertTrue(errors.any { it.contains("version") })
    }

    @Test
    fun `invalid base is rejected`() {
        assertTrue(valid().copy(base = "neon").validationErrors().any { it.contains("base") })
    }

    @Test
    fun `apiVersion must be positive`() {
        assertTrue(valid().copy(apiVersion = 0).validationErrors().any { it.contains("apiVersion") })
    }
}
