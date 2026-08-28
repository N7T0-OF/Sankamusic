package com.sankamusic.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests du retour haptique (docs/MIGRATION.md étape 5). */
class HapticsTest {

    // ── Parse de la préférence persistée ────────────────────────────────

    @Test
    fun `parses on and off values`() {
        assertTrue(parseHapticsEnabled("on")!!)
        assertFalse(parseHapticsEnabled("off")!!)
        assertTrue(parseHapticsEnabled("true")!!)
        assertFalse(parseHapticsEnabled("false")!!)
        assertTrue(parseHapticsEnabled("1")!!)
        assertFalse(parseHapticsEnabled("0")!!)
    }

    @Test
    fun `parse tolerates case and whitespace`() {
        assertTrue(parseHapticsEnabled("  ON  ")!!)
        assertFalse(parseHapticsEnabled("Off")!!)
    }

    @Test
    fun `parse rejects unknown or empty values`() {
        assertNull(parseHapticsEnabled(null))
        assertNull(parseHapticsEnabled(""))
        assertNull(parseHapticsEnabled("oui"))
        assertNull(parseHapticsEnabled("2"))
    }

    // ── Valeur effective (défaut sûr) ───────────────────────────────────

    @Test
    fun `effective defaults to off on unknown or missing value`() {
        assertFalse(effectiveHapticsEnabled(null))
        assertFalse(effectiveHapticsEnabled("corrompu"))
        assertTrue(effectiveHapticsEnabled("on"))
    }

    @Test
    fun `settings default is off like the SpaceKai flag`() {
        assertFalse(HapticsSettings().enabled)
    }

    // ── Round-trip préférence ↔ valeur ──────────────────────────────────

    @Test
    fun `preference value round trips`() {
        assertTrue(parseHapticsEnabled(hapticsPreferenceValue(true))!!)
        assertFalse(parseHapticsEnabled(hapticsPreferenceValue(false))!!)
        assertEquals("on", hapticsPreferenceValue(true))
        assertEquals("off", hapticsPreferenceValue(false))
    }

    // ── Parité avec le flag SpaceKai-OLD haptics ────────────────────────

    @Test
    fun `feature flag maps to settings`() {
        assertTrue(hapticsEnabledFromFeatureFlag(true))
        assertFalse(hapticsEnabledFromFeatureFlag(false))
    }

    // ── Décision (gate) ─────────────────────────────────────────────────

    @Test
    fun `haptic fires only when enabled`() {
        assertTrue(shouldFireHaptic(true))
        assertFalse(shouldFireHaptic(false))
    }

    @Test
    fun `settings key is stable`() {
        assertEquals("haptics.enabled", SettingsKeys.HAPTICS_ENABLED)
    }
}
