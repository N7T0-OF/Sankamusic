package com.sankamusic.core

import com.sankamusic.core.api.PluginManifest
import com.sankamusic.core.api.PluginState
import com.sankamusic.core.api.SpaceKaiPlugin

/**
 * Moteur de plugins minimal (prototype Phase 2 — voir docs/PLUGIN_SYSTEM.md § 2 et 6).
 *
 * Responsabilités :
 *  - enregistrer/désenregistrer des plugins avec validation du manifest ;
 *  - gérer le cycle de vie (register → enable → disable → unload) ;
 *  - ISOLATION : toute exception levée par un hook est capturée, le plugin passe
 *    à l'état [PluginState.CRASHED] et est désactivé — l'application continue.
 *
 * ⚠️ Squelette volontairement minimal : la persistance, la permission check et le
 * chargement dynamique (fichiers/dépôt) seront ajoutés après l'audit (Phase 1).
 */
class PluginEngine {

    private val registry = mutableMapOf<String, RegisteredPlugin>()

    private class RegisteredPlugin(
        val plugin: SpaceKaiPlugin,
        var state: PluginState,
    )

    /** Enregistre un plugin après validation de son manifest. */
    fun register(plugin: SpaceKaiPlugin): Result<Unit> {
        val errors = plugin.manifest.validationErrors()
        if (errors.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Manifest invalide : $errors"))
        }
        val id = plugin.manifest.id
        if (registry.containsKey(id)) {
            return Result.failure(IllegalStateException("Plugin déjà enregistré : $id"))
        }
        return runCatching {
            plugin.onLoad()
            registry[id] = RegisteredPlugin(plugin, PluginState.INSTALLED)
        }.onFailure {
            // Un plugin qui échoue au chargement ne doit pas casser l'application.
            registry.remove(id)
        }
    }

    /** Active un plugin ; échec → état [PluginState.CRASHED], jamais de propagation. */
    fun enable(id: String): Result<Unit> {
        val entry = registry[id] ?: return Result.failure(NoSuchElementException("Plugin inconnu : $id"))
        val result = runCatching { entry.plugin.onEnable() }
        return if (result.isSuccess) {
            entry.state = PluginState.ENABLED
            Result.success(Unit)
        } else {
            entry.state = PluginState.CRASHED
            result
        }
    }

    /** Désactive un plugin ; toute exception est capturée. */
    fun disable(id: String): Result<Unit> {
        val entry = registry[id] ?: return Result.failure(NoSuchElementException("Plugin inconnu : $id"))
        val result = runCatching { entry.plugin.onDisable() }
        entry.state = PluginState.DISABLED
        return result
    }

    /** Décharge un plugin (suppression / mise à jour). */
    fun unload(id: String): Result<Unit> {
        val entry = registry.remove(id) ?: return Result.failure(NoSuchElementException("Plugin inconnu : $id"))
        val result = runCatching { entry.plugin.onUnload() }
        return result
    }

    fun state(id: String): PluginState? = registry[id]?.state

    fun registeredIds(): Set<String> = registry.keys

    /** Manifest d'un plugin enregistré. */
    fun manifest(id: String): PluginManifest? = registry[id]?.plugin?.manifest
}
