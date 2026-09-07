package com.sankamusic.core.settings

import com.sankamusic.core.api.SettingsKeys
import com.sankamusic.core.api.builtInSpaceKaiFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariants du catalogue Paramètres (shell inspiré de Convx) : une entrée par
 * réglage réel, chaque contrôle lié à une clé de préférence existante — jamais
 * d'entrée décorative.
 */
class SettingsCatalogTest {

    @Test
    fun `entries have unique non-blank ids and titles`() {
        val entries = SettingsCatalog.entries
        assertTrue(entries.isNotEmpty())
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        assertTrue(entries.all { it.id.isNotBlank() && it.title.isNotBlank() })
    }

    @Test
    fun `control entries target real preference keys, action and info entries have none`() {
        val knownKeys = buildSet {
            add(SettingsKeys.THEME_MODE)
            add(SettingsKeys.THEME_COLOR_SOURCE)
            add(SettingsKeys.THEME_SEED_COLOR)
            add(SettingsKeys.PLAYER_ORIENTATION)
            add(SettingsKeys.HAPTICS_ENABLED)
            builtInSpaceKaiFeatures.features.forEach { add(featureFlagKey(it.id)) }
        }
        SettingsCatalog.entries.forEach { entry ->
            when (entry.kind) {
                SettingsEntryKind.TOGGLE, SettingsEntryKind.SELECT -> {
                    assertNotNull("entrée '${entry.id}' sans clé réelle", entry.prefKey)
                    assertTrue(
                        "clé inconnue pour '${entry.id}' : ${entry.prefKey}",
                        entry.prefKey!! in knownKeys,
                    )
                }

                SettingsEntryKind.ACTION, SettingsEntryKind.INFO ->
                    assertNull("entrée '${entry.id}' ne devrait pas porter de clé", entry.prefKey)
            }
        }
    }

    @Test
    fun `every manifest feature has exactly one toggle entry`() {
        builtInSpaceKaiFeatures.features.forEach { feature ->
            val matches = SettingsCatalog.entries.filter { it.prefKey == featureFlagKey(feature.id) }
            assertEquals("feature '${feature.id}'", 1, matches.size)
            assertEquals(SettingsEntryKind.TOGGLE, matches.first().kind)
            assertEquals(feature.name, matches.first().title)
        }
    }

    @Test
    fun `every destination is covered by the catalog`() {
        SettingsDestination.entries.forEach { destination ->
            assertTrue(
                "destination ${destination.name} sans entrée",
                SettingsCatalog.entriesFor(destination).isNotEmpty(),
            )
        }
    }

    @Test
    fun `search matches title, description and keywords case-insensitively`() {
        assertTrue(SettingsCatalog.search("vibra").any { it.prefKey == SettingsKeys.HAPTICS_ENABLED })
        assertTrue(SettingsCatalog.search("SOMBRE").any { it.id == "theme.mode" })
        assertTrue(SettingsCatalog.search("paysage").any { it.id == "player.orientation" })
        assertTrue(SettingsCatalog.search("seed").any { it.id == "theme.seed_color" })
        // Les fonctionnalités du manifest sont trouvables par nom et par id.
        builtInSpaceKaiFeatures.features.forEach { feature ->
            assertTrue(
                "feature '${feature.id}' introuvable",
                SettingsCatalog.search(feature.name).any { it.id == "feature.${feature.id}" },
            )
        }
    }

    @Test
    fun `blank search returns the full catalog`() {
        assertEquals(SettingsCatalog.entries, SettingsCatalog.search("   "))
    }

    @Test
    fun `search with no match returns empty`() {
        assertTrue(SettingsCatalog.search("réglage-inexistant-xyz").isEmpty())
    }
}
