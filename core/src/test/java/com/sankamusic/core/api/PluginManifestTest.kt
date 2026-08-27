package com.sankamusic.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManifestTest {

    private fun valid() = PluginManifest(
        id = "com.example.plugin",
        name = "Example Plugin",
        version = "1.0.0",
        apiVersion = 1,
        minSankamusicVersion = "0.1.0",
    )

    @Test
    fun `valid manifest has no errors`() {
        assertTrue(valid().validationErrors().isEmpty())
    }

    @Test
    fun `blank id and version are rejected`() {
        val errors = valid().copy(id = "", version = " ").validationErrors()
        assertTrue(errors.any { it.contains("id") })
        assertTrue(errors.any { it.contains("version") })
    }

    @Test
    fun `blank name and min version are rejected`() {
        val errors = valid().copy(name = "", minSankamusicVersion = " ").validationErrors()
        assertTrue(errors.any { it.contains("name") })
        assertTrue(errors.any { it.contains("minSankamusicVersion") })
    }

    @Test
    fun `apiVersion must be positive`() {
        assertTrue(valid().copy(apiVersion = 0).validationErrors().any { it.contains("apiVersion") })
    }

    @Test
    fun `unknown permissions are rejected`() {
        val errors = valid().copy(permissions = setOf("totally.unknown")).validationErrors()
        assertEquals(1, errors.size)
        assertTrue(errors.first().contains("totally.unknown"))
    }

    @Test
    fun `known permissions pass validation`() {
        val errors = valid().copy(
            permissions = setOf(Permissions.PLAYER_READ, Permissions.PLAYLIST_WRITE),
        ).validationErrors()
        assertTrue(errors.isEmpty())
    }
}
