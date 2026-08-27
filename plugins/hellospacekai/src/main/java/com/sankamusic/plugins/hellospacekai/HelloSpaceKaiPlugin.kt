package com.sankamusic.plugins.hellospacekai

import com.sankamusic.core.api.HomeSection
import com.sankamusic.core.api.Permissions
import com.sankamusic.core.api.PluginManifest
import com.sankamusic.core.api.SettingsEntry
import com.sankamusic.core.api.SpaceKaiApi
import com.sankamusic.core.api.SpaceKaiPlugin

/**
 * Plugin d'exemple (Phase 3 — voir docs/ROADMAP.md et docs/PLUGIN_SYSTEM.md § 8).
 *
 * Démonstrations :
 *  - un manifest complet (id, version, compatibilité Sankamusic, permissions) ;
 *  - le cycle de vie onLoad / onEnable / onDisable / onUnload géré par le
 *    [com.sankamusic.core.PluginEngine] (installation, activation,
 *    désactivation, déchargement) ;
 *  - l'utilisation des points d'extension UI : en onEnable, le plugin ajoute
 *    une section Home et une entrée Settings ; en onDisable, il les retire ;
 *  - l'isolation : ce plugin ne peut pas casser l'application.
 *
 * ⚠️ La vérification des permissions (NAVIGATION_MODIFY) avant transmission à
 * l'UI sera assurée par le Core (Phase 2/3) ; le rendu réel des extensions
 * sera fait par l'UI du Core.
 */
class HelloSpaceKaiPlugin : SpaceKaiPlugin {

    override val manifest = PluginManifest(
        id = ID,
        name = "Hello SpaceKai",
        author = "Souanpt",
        version = "1.0.0",
        apiVersion = 1,
        minSankamusicVersion = "0.1.0",
        permissions = setOf(Permissions.NAVIGATION_MODIFY),
    )

    override fun onLoad() {
        // Chargement : ne rien faire de lourd ici.
    }

    override fun onEnable() {
        // L'API n'est injectée qu'après le démarrage du Core : on se garde d'y
        // accéder si ce n'est pas encore le cas (évite tout crash en isolation).
        if (!SpaceKaiApi.isInitialized()) return

        val ui = SpaceKaiApi.instance.uiExtensions
        ui.registerHomeSection(
            HomeSection(id = HOME_SECTION_ID, title = "Hello SpaceKai", priority = 10),
        )
        ui.registerSettingsEntry(
            SettingsEntry(
                id = SETTINGS_ENTRY_ID,
                title = "Hello SpaceKai",
                summary = "Plugin d'exemple v${manifest.version}",
                priority = 10,
            ),
        )
    }

    override fun onDisable() {
        if (!SpaceKaiApi.isInitialized()) return

        val ui = SpaceKaiApi.instance.uiExtensions
        ui.removeHomeSection(HOME_SECTION_ID)
        ui.removeSettingsEntry(SETTINGS_ENTRY_ID)
    }

    override fun onUnload() = Unit

    companion object {
        /** Identifiant unique du plugin (reverse-DNS). */
        const val ID = "com.souanpt.spacekai.hellospacekai"

        const val HOME_SECTION_ID = "$ID.home"
        const val SETTINGS_ENTRY_ID = "$ID.settings"
    }
}
