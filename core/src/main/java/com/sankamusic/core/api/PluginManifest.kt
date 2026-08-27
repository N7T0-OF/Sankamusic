package com.sankamusic.core.api

/**
 * Manifest déclaratif d'un plugin (voir docs/PLUGIN_SYSTEM.md § 3).
 * Équivalent Kotlin du manifest JSON documenté :
 *
 * ```json
 * {
 *   "id": "com.souanpt.spacekai.spotify",
 *   "name": "Spotify Sync",
 *   "version": "1.0.0",
 *   "apiVersion": 1,
 *   "permissions": ["playlist.read", "playlist.write"]
 * }
 * ```
 */
data class PluginManifest(
    /** Identifiant unique du plugin (reverse-DNS recommandé). */
    val id: String,
    val name: String,
    val author: String = "",
    val version: String,
    /** Version de l'API SpaceKai avec laquelle le plugin a été écrit. */
    val apiVersion: Int,
    /** Version minimale de Sankamusic requise. */
    val minSankamusicVersion: String,
    /** Version maximale compatible (null = aucune limite déclarée). */
    val maxSankamusicVersion: String? = null,
    /** Permissions demandées (voir [Permissions]). */
    val permissions: Set<String> = emptySet(),
    /** IDs des plugins requis. */
    val dependencies: Set<String> = emptySet(),
) {
    /**
     * Retourne la liste des erreurs de validation ; vide si le manifest est valide.
     * Un manifest invalide est refusé par le [com.sankamusic.core.PluginEngine].
     */
    fun validationErrors(): List<String> = buildList {
        if (id.isBlank()) add("id ne doit pas être vide")
        if (name.isBlank()) add("name ne doit pas être vide")
        if (version.isBlank()) add("version ne doit pas être vide")
        if (apiVersion <= 0) add("apiVersion doit être > 0")
        if (minSankamusicVersion.isBlank()) add("minSankamusicVersion ne doit pas être vide")
        val unknown = permissions - Permissions.ALL
        if (unknown.isNotEmpty()) add("permissions inconnues : $unknown")
    }
}
