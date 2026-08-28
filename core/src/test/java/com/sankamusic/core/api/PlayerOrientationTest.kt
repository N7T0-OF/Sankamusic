package com.sankamusic.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests du modèle d'orientation du player (docs/MIGRATION.md étape 3). */
class PlayerOrientationTest {

    // ── Résolution (fonction pure) ──────────────────────────────────────

    @Test
    fun `follow system keeps the current orientation`() {
        assertEquals(Orientation.PORTRAIT, resolvePlayerOrientation(PlayerOrientationMode.FOLLOW_SYSTEM, Orientation.PORTRAIT))
        assertEquals(Orientation.LANDSCAPE, resolvePlayerOrientation(PlayerOrientationMode.FOLLOW_SYSTEM, Orientation.LANDSCAPE))
        assertEquals(Orientation.UNSPECIFIED, resolvePlayerOrientation(PlayerOrientationMode.FOLLOW_SYSTEM, Orientation.UNSPECIFIED))
    }

    @Test
    fun `force landscape always resolves to landscape`() {
        assertEquals(Orientation.LANDSCAPE, resolvePlayerOrientation(PlayerOrientationMode.FORCE_LANDSCAPE, Orientation.PORTRAIT))
        assertEquals(Orientation.LANDSCAPE, resolvePlayerOrientation(PlayerOrientationMode.FORCE_LANDSCAPE, Orientation.LANDSCAPE))
        assertEquals(Orientation.LANDSCAPE, resolvePlayerOrientation(PlayerOrientationMode.FORCE_LANDSCAPE, Orientation.UNSPECIFIED))
    }

    // ── Parse de la préférence persistée ────────────────────────────────

    @Test
    fun `parses stored values`() {
        assertEquals(PlayerOrientationMode.FOLLOW_SYSTEM, parsePlayerOrientationMode("system"))
        assertEquals(PlayerOrientationMode.FORCE_LANDSCAPE, parsePlayerOrientationMode("landscape"))
    }

    @Test
    fun `parse tolerates case and surrounding whitespace`() {
        assertEquals(PlayerOrientationMode.FORCE_LANDSCAPE, parsePlayerOrientationMode("  LANDSCAPE  "))
        assertEquals(PlayerOrientationMode.FOLLOW_SYSTEM, parsePlayerOrientationMode("Follow_System"))
    }

    @Test
    fun `parse rejects unknown or empty values`() {
        assertNull(parsePlayerOrientationMode(null))
        assertNull(parsePlayerOrientationMode(""))
        assertNull(parsePlayerOrientationMode("portrait"))
        assertNull(parsePlayerOrientationMode("42"))
    }

    @Test
    fun `effective mode falls back to follow system on unknown value`() {
        assertEquals(PlayerOrientationMode.FOLLOW_SYSTEM, effectivePlayerOrientationMode(null))
        assertEquals(PlayerOrientationMode.FOLLOW_SYSTEM, effectivePlayerOrientationMode("corrompu"))
        assertEquals(PlayerOrientationMode.FORCE_LANDSCAPE, effectivePlayerOrientationMode("landscape"))
    }

    // ── Round-trip préférence ↔ mode ────────────────────────────────────

    @Test
    fun `preference value round trips`() {
        PlayerOrientationMode.entries.forEach { mode ->
            assertEquals(mode, parsePlayerOrientationMode(mode.toPreferenceValue()))
        }
    }

    // ── Parité avec le flag SpaceKai-OLD landscape_player ───────────────

    @Test
    fun `feature flag maps to mode`() {
        assertEquals(PlayerOrientationMode.FORCE_LANDSCAPE, playerOrientationModeFromFeatureFlag(true))
        assertEquals(PlayerOrientationMode.FOLLOW_SYSTEM, playerOrientationModeFromFeatureFlag(false))
    }

    @Test
    fun `settings key is stable`() {
        assertEquals("player.orientation", SettingsKeys.PLAYER_ORIENTATION)
    }
}
