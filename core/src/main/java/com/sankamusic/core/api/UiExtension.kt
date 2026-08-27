package com.sankamusic.core.api

/**
 * Points d'extension UI de la plateforme (voir docs/THEME_SYSTEM.md § 7 et
 * docs/PLUGIN_SYSTEM.md § 8) : sections Home, entrées Settings, actions player.
 *
 * Les plugins déclarent des extensions (via [UiExtensionApi]) ; l'UI du Core
 * les affiche selon la priorité. Aucune dépendance Compose ici : ce sont des
 * déclarations pures, le rendu est assuré par l'UI (Phase 2/3).
 */

/** Section affichée sur l'écran d'accueil (ex. "Most Played", "Spotify Playlists"). */
data class HomeSection(
    val id: String,
    val title: String,
    /** Ordre d'affichage (croissant ; les plus petits d'abord). */
    val priority: Int = 100,
)

/** Entrée ajoutée dans les paramètres de l'application. */
data class SettingsEntry(
    val id: String,
    val title: String,
    val summary: String? = null,
    val priority: Int = 100,
)

/** Action/bouton ajouté dans l'écran player. */
data class PlayerAction(
    val id: String,
    val label: String,
    val priority: Int = 100,
)

/**
 * API d'enregistrement des extensions UI. Les méthodes sont non-suspend :
 * il s'agit de registres en mémoire, appelables depuis les hooks de cycle de
 * vie des plugins (onEnable / onDisable). Le Core vérifie les permissions
 * (NAVIGATION_MODIFY, THEME_MODIFY…) avant de transmettre à l'UI.
 */
interface UiExtensionApi {
    fun registerHomeSection(section: HomeSection)
    fun removeHomeSection(id: String)

    fun registerSettingsEntry(entry: SettingsEntry)
    fun removeSettingsEntry(id: String)

    fun registerPlayerAction(action: PlayerAction)
    fun removePlayerAction(id: String)
}
