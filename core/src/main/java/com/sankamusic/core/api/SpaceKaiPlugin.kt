package com.sankamusic.core.api

/**
 * Contrat de base de tout plugin SpaceKai (voir docs/PLUGIN_SYSTEM.md).
 *
 * Cycle de vie : onLoad → onEnable → (exécution) → onDisable → onUnload.
 * Toute exception levée par un hook est capturée par le [com.sankamusic.core.PluginEngine] :
 * le plugin est désactivé automatiquement, l'application continue de fonctionner.
 *
 * ⚠️ Squelette de prototype (Phase 2) — les détails seront affinés après l'audit (Phase 1).
 */
interface SpaceKaiPlugin {

    /** Manifest déclaratif du plugin (id, version, permissions, compatibilité…). */
    val manifest: PluginManifest

    /** Appelé une fois au chargement du plugin (avant activation). Ne doit pas être lourd. */
    fun onLoad() = Unit

    /** Appelé à l'activation. Point d'entrée principal des fonctionnalités. */
    fun onEnable() = Unit

    /** Appelé à la désactivation : libérer les ressources, annuler les abonnements. */
    fun onDisable() = Unit

    /** Appelé au déchargement (suppression / mise à jour du plugin). */
    fun onUnload() = Unit
}

/** État courant d'un plugin tel que suivi par le [com.sankamusic.core.PluginEngine]. */
enum class PluginState {
    /** Plugin enregistré, hooks chargés, non actif. */
    INSTALLED,

    /** Actif : [SpaceKaiPlugin.onEnable] a été exécuté avec succès. */
    ENABLED,

    /** Désactivé (par l'utilisateur ou après une erreur). */
    DISABLED,

    /** A planté pendant un hook → désactivé automatiquement par le moteur. */
    CRASHED,
}
