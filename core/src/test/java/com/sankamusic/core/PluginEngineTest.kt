package com.sankamusic.core

import com.sankamusic.core.api.PluginManifest
import com.sankamusic.core.api.PluginState
import com.sankamusic.core.api.SpaceKaiPlugin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires du [PluginEngine] — à exécuter avec `./gradlew :core:test`
 * dès qu'un JDK est disponible (l'environnement de travail n'en dispose pas).
 */
class PluginEngineTest {

    private fun manifest(id: String = "test.plugin") = PluginManifest(
        id = id,
        name = "Test Plugin",
        version = "1.0.0",
        apiVersion = 1,
        minSankamusicVersion = "0.1.0",
    )

    private fun plugin(id: String = "test.plugin") = object : SpaceKaiPlugin {
        override val manifest = manifest(id)
    }

    // ── Enregistrement ────────────────────────────────────────────────

    @Test
    fun `register valid plugin sets INSTALLED`() {
        val engine = PluginEngine()
        assertTrue(engine.register(plugin()).isSuccess)
        assertEquals(PluginState.INSTALLED, engine.state("test.plugin"))
    }

    @Test
    fun `register invalid manifest is rejected`() {
        val engine = PluginEngine()
        val bad = object : SpaceKaiPlugin {
            override val manifest = PluginManifest(
                id = "",
                name = "",
                version = "",
                apiVersion = 0,
                minSankamusicVersion = "",
            )
        }
        assertTrue(engine.register(bad).isFailure)
        assertNull(engine.state(""))
    }

    @Test
    fun `duplicate register is rejected`() {
        val engine = PluginEngine()
        assertTrue(engine.register(plugin()).isSuccess)
        assertTrue(engine.register(plugin()).isFailure)
    }

    @Test
    fun `register failure in onLoad does not crash and removes plugin`() {
        val engine = PluginEngine()
        val crashing = object : SpaceKaiPlugin {
            override val manifest = manifest("crash.load")
            override fun onLoad() {
                throw IllegalStateException("boom")
            }
        }
        assertTrue(engine.register(crashing).isFailure)
        assertNull(engine.state("crash.load"))
    }

    // ── Cycle de vie ──────────────────────────────────────────────────

    @Test
    fun `enable calls onEnable and sets ENABLED`() {
        val engine = PluginEngine()
        var enabled = false
        val p = object : SpaceKaiPlugin {
            override val manifest = manifest()
            override fun onEnable() {
                enabled = true
            }
        }
        engine.register(p)
        assertTrue(engine.enable("test.plugin").isSuccess)
        assertTrue(enabled)
        assertEquals(PluginState.ENABLED, engine.state("test.plugin"))
    }

    @Test
    fun `plugin crash on enable is isolated and state is CRASHED`() {
        val engine = PluginEngine()
        val crashing = object : SpaceKaiPlugin {
            override val manifest = manifest("crash.enable")
            override fun onEnable() {
                throw IllegalStateException("boom")
            }
        }
        engine.register(crashing)
        val result = engine.enable("crash.enable")
        assertTrue(result.isFailure)
        assertEquals(PluginState.CRASHED, engine.state("crash.enable"))
    }

    @Test
    fun `disable calls onDisable and sets DISABLED`() {
        val engine = PluginEngine()
        var disabled = false
        val p = object : SpaceKaiPlugin {
            override val manifest = manifest()
            override fun onDisable() {
                disabled = true
            }
        }
        engine.register(p)
        engine.enable("test.plugin")
        assertTrue(engine.disable("test.plugin").isSuccess)
        assertTrue(disabled)
        assertEquals(PluginState.DISABLED, engine.state("test.plugin"))
    }

    @Test
    fun `unload removes plugin and calls onUnload`() {
        val engine = PluginEngine()
        var unloaded = false
        val p = object : SpaceKaiPlugin {
            override val manifest = manifest()
            override fun onUnload() {
                unloaded = true
            }
        }
        engine.register(p)
        assertTrue(engine.unload("test.plugin").isSuccess)
        assertTrue(unloaded)
        assertNull(engine.state("test.plugin"))
    }

    // ── Erreurs ───────────────────────────────────────────────────────

    @Test
    fun `operations on unknown plugin fail`() {
        val engine = PluginEngine()
        assertTrue(engine.enable("unknown").isFailure)
        assertTrue(engine.disable("unknown").isFailure)
        assertTrue(engine.unload("unknown").isFailure)
    }
}
