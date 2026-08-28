package com.sankamusic.core.api

/**
 * Points d'extension UI de la plateforme (voir docs/THEME_SYSTEM.md § 7 et
 * docs/PLUGIN_SYSTEM.md § 8) : onglets de navigation, sections Home,
 * entrées Settings, actions player.
 *
 * Les plugins déclarent des extensions (via [UiExtensionApi]) ; l'UI du Core
 * les affiche selon la priorité. Aucune dépendance Compose ici : ce sont des
 * déclarations pures, le rendu est assuré par l'UI (Phase 2/3).
 *
 * La navigation reprend le modèle de l'ancien SpaceKai (customNavigation,
 * docs/MIGRATION.md étape 1) : onglets extensibles, ordonnés par priorité,
 * icône résolue par l'UI via [NavigationTab.iconName].
 */

/**
 * Onglet de la barre de navigation inférieure (ex. "Accueil", "Bibliothèque").
 * L'ordre d'affichage est la priorité croissante (les plus petits d'abord).
 */
data class NavigationTab(
    val id: String,
    val label: String,
    /** Ordre d'affichage (croissant ; les plus petits d'abord). */
    val priority: Int = 100,
    /** Nom d'icône résolu par l'UI (convention : "home", "library", "search", "settings"…). */
    val iconName: String = "default",
)

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
    fun registerNavigationTab(tab: NavigationTab)
    fun removeNavigationTab(id: String)
    /** Onglets déclarés par les plugins actifs, triés par priorité croissante. */
    fun navigationTabs(): List<NavigationTab>

    fun registerHomeSection(section: HomeSection)
    fun removeHomeSection(id: String)
    /** Sections Home déclarées par les plugins actifs, triées par priorité. */
    fun homeSections(): List<HomeSection>

    fun registerSettingsEntry(entry: SettingsEntry)
    fun removeSettingsEntry(id: String)
    /** Entrées Settings déclarées par les plugins actifs, triées par priorité. */
    fun settingsEntries(): List<SettingsEntry>

    fun registerPlayerAction(action: PlayerAction)
    fun removePlayerAction(id: String)
    /** Actions player déclarées par les plugins actifs, triées par priorité. */
    fun playerActions(): List<PlayerAction>
}
