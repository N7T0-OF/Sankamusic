package com.sankamusic.core.settings

import com.sankamusic.core.api.PlayerOrientationMode
import com.sankamusic.core.api.toPreferenceValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests de la couche de préférences typées (docs/MIGRATION.md étape 7). */
class PreferenceTest {

    @Test
    fun `boolean preference parses tolerant values`() {
        val pref = booleanPreference("k", false)
        assertEquals(true, pref.parse("true"))
        assertEquals(true, pref.parse("1"))
        assertEquals(true, pref.parse("on"))
        assertEquals(false, pref.parse("OFF"))
        assertEquals(null, pref.parse("peut-être"))
    }

    @Test
    fun `boolean preference serializes and round trips`() {
        val pref = booleanPreference("k", false)
        assertEquals("true", pref.serialize(true))
        assertEquals(true, pref.parse(pref.serialize(true)))
        assertEquals(false, pref.parse(pref.serialize(false)))
    }

    @Test
    fun `string preference ignores blank values`() {
        val pref = stringPreference("k", "défaut")
        assertEquals("valeur", pref.parse("valeur"))
        assertEquals(null, pref.parse("   "))
        assertEquals(null, pref.parse(null))
    }

    @Test
    fun `enum preference uses provided parse and serialize`() {
        val pref = enumPreference(
            key = "player.orientation",
            default = PlayerOrientationMode.FOLLOW_SYSTEM,
            parse = { com.sankamusic.core.api.parsePlayerOrientationMode(it) },
            serialize = { it.toPreferenceValue() },
        )
        assertEquals(PlayerOrientationMode.FORCE_LANDSCAPE, pref.parse("landscape"))
        // Parse strict : valeur inconnue → null (le défaut est appliqué par TypedSettings.get).
        assertEquals(null, pref.parse("inconnu"))
        assertEquals("landscape", pref.serialize(PlayerOrientationMode.FORCE_LANDSCAPE))
    }

    @Test
    fun `all old SpaceKai flags have stable keys and preferences`() {
        assertEquals("spacekai.custom_navigation", SpaceKaiFlag.CUSTOM_NAVIGATION.preferenceKey())
        assertEquals("spacekai.landscape_player", SpaceKaiFlag.LANDSCAPE_PLAYER.preferenceKey())
        assertEquals("spacekai.haptics", SpaceKaiFlag.HAPTICS.preferenceKey())
        assertEquals(8, SpaceKaiFlag.entries.size)
        assertEquals("navigation", SpaceKaiFlag.CUSTOM_NAVIGATION.manifestFeatureId)
        assertEquals(false, SpaceKaiFlag.HAPTICS.default)
    }
}

/** Faux store en mémoire pour les tests. */
class FakeSettings(initial: Map<String, String> = emptyMap()) : StringSettings {
    val map = initial.toMutableMap()
    override fun get(key: String): String? = map[key]
    override fun set(key: String, value: String) {
        map[key] = value
    }
}

class TypedSettingsTest {

    @Test
    fun `get returns stored value`() {
        val settings = FakeSettings(mapOf("k" to "true"))
        val typed = TypedSettings(settings)
        assertTrue(typed.get(booleanPreference("k", false)))
    }

    @Test
    fun `get falls back to default on missing or corrupted value`() {
        val typed = TypedSettings(FakeSettings())
        assertTrue(typed.get(booleanPreference("absent", true)))
        val corrupted = TypedSettings(FakeSettings(mapOf("k" to "n'importe quoi")))
        assertTrue(corrupted.get(booleanPreference("k", true)))
    }

    @Test
    fun `set then get round trips`() {
        val settings = FakeSettings()
        val typed = TypedSettings(settings)
        val pref = booleanPreference("haptics.enabled", false)
        typed.set(pref, true)
        assertTrue(typed.get(pref))
        assertEquals("true", settings.map["haptics.enabled"])
    }

    @Test
    fun `typed settings share the underlying store`() {
        val settings = FakeSettings()
        val typed = TypedSettings(settings)
        val pref = enumPreference("player.orientation", PlayerOrientationMode.FOLLOW_SYSTEM, { com.sankamusic.core.api.parsePlayerOrientationMode(it) }, { it.toPreferenceValue() })
        typed.set(pref, PlayerOrientationMode.FORCE_LANDSCAPE)
        // Lu via le store brut : la valeur est bien persistée sous forme de chaîne.
        assertEquals("landscape", settings.map["player.orientation"])
        val other = TypedSettings(settings)
        assertEquals(PlayerOrientationMode.FORCE_LANDSCAPE, other.get(pref))
    }
}

class SpaceKaiFeatureFlagsTest {

    @Test
    fun `enabled by default from the manifest`() {
        val settings = FakeSettings()
        assertTrue(SpaceKaiFeatureFlags.isEnabled(settings, "navigation", "1.7.2"))
        assertFalse(SpaceKaiFeatureFlags.isEnabled(settings, "haptics", "1.7.2")) // enabledByDefault = false
    }

    @Test
    fun `stored value overrides the default`() {
        val settings = FakeSettings(mapOf(featureFlagKey("haptics") to "true"))
        assertTrue(SpaceKaiFeatureFlags.isEnabled(settings, "haptics", "1.7.2"))
        val off = FakeSettings(mapOf(featureFlagKey("navigation") to "false"))
        assertFalse(SpaceKaiFeatureFlags.isEnabled(off, "navigation", "1.7.2"))
    }

    @Test
    fun `incompatible with upstream disables the feature`() {
        val settings = FakeSettings(mapOf(featureFlagKey("navigation") to "true"))
        // navigation est 1.7.x : une version 1.8.0 la rend incompatible.
        assertFalse(SpaceKaiFeatureFlags.isEnabled(settings, "navigation", "1.8.0"))
        // upstream inconnu → jamais compatible.
        assertFalse(SpaceKaiFeatureFlags.isEnabled(settings, "navigation", null))
        assertFalse(SpaceKaiFeatureFlags.isEnabled(settings, "navigation", "invalide"))
    }

    @Test
    fun `unknown feature is disabled`() {
        assertFalse(SpaceKaiFeatureFlags.isEnabled(FakeSettings(), "inconnu", "2.0.0"))
    }

    @Test
    fun `setEnabled persists the choice`() {
        val settings = FakeSettings()
        SpaceKaiFeatureFlags.setEnabled(settings, "haptics", true)
        assertTrue(SpaceKaiFeatureFlags.isEnabled(settings, "haptics", "2.0.0"))
    }

    @Test
    fun `flag preference key helper matches manifest id`() {
        assertEquals("spacekai.feature.navigation.enabled", featureFlagKey("navigation"))
    }
}
