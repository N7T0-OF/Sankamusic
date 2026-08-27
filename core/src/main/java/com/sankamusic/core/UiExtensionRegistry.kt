package com.sankamusic.core

import com.sankamusic.core.api.HomeSection
import com.sankamusic.core.api.PlayerAction
import com.sankamusic.core.api.SettingsEntry
import com.sankamusic.core.api.UiExtensionApi

/**
 * Registre des extensions UI déclarées par les plugins (prototype Phase 2/3).
 *
 * - Chaque extension vient d'un plugin ACTIF ; un doublon d'id est refusé.
 * - Les listes exposées sont triées par priorité croissante.
 * - L'UI du Core consomme ces listes pour afficher sections/entrées/actions.
 */
class UiExtensionRegistry : UiExtensionApi {

    private val homeSections = mutableMapOf<String, HomeSection>()
    private val settingsEntries = mutableMapOf<String, SettingsEntry>()
    private val playerActions = mutableMapOf<String, PlayerAction>()

    override fun registerHomeSection(section: HomeSection): Unit =
        putUnique(homeSections, section.id, section, "Section Home")

    override fun removeHomeSection(id: String) {
        homeSections.remove(id)
    }

    override fun registerSettingsEntry(entry: SettingsEntry): Unit =
        putUnique(settingsEntries, entry.id, entry, "Entrée Settings")

    override fun removeSettingsEntry(id: String) {
        settingsEntries.remove(id)
    }

    override fun registerPlayerAction(action: PlayerAction): Unit =
        putUnique(playerActions, action.id, action, "Action player")

    override fun removePlayerAction(id: String) {
        playerActions.remove(id)
    }

    fun homeSections(): List<HomeSection> = homeSections.values.sortedBy { it.priority }

    fun settingsEntries(): List<SettingsEntry> = settingsEntries.values.sortedBy { it.priority }

    fun playerActions(): List<PlayerAction> = playerActions.values.sortedBy { it.priority }

    private fun <T> putUnique(
        map: MutableMap<String, T>,
        key: String,
        value: T,
        what: String,
    ): Unit {
        if (map.putIfAbsent(key, value) != null) {
            throw IllegalStateException("$what déjà enregistrée : $key")
        }
    }
}
