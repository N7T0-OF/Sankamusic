package com.sankamusic.core.settings

import com.sankamusic.core.api.SettingsKeys
import com.sankamusic.core.api.builtInSpaceKaiFeatures

/**
 * Catalogue des Paramètres (shell inspiré de Convx — pattern réimplémenté,
 * aucune dépendance au code Convx) : **une entrée par réglage réel**, chaque
 * entrée étant liée à la clé de préférence qu'elle pilote. La recherche de
 * l'écran Paramètres filtre ce catalogue et navigue vers le sous-écran
 * propriétaire — jamais d'entrée décorative : les entrées de contrôle
 * ([SettingsEntryKind.TOGGLE]/[SettingsEntryKind.SELECT] ont toutes une
 * [SettingsCatalogEntry.prefKey] réelle (invariant testé).
 */

/** Sous-écran Paramètres propriétaire d'un groupe de réglages. */
enum class SettingsDestination {
    THEME,
    FEATURES,
    PLAYER,
    UPDATES,
    ABOUT,
}

/** Nature de l'entrée : contrôle persisté, action ou information. */
enum class SettingsEntryKind {
    TOGGLE,
    SELECT,
    ACTION,
    INFO,
}

/** Une entrée du catalogue : un réglage réel, son sous-écran et sa clé. */
data class SettingsCatalogEntry(
    val id: String,
    val title: String,
    val description: String,
    val destination: SettingsDestination,
    val kind: SettingsEntryKind,
    /**
     * Clé de préférence réelle pilotée par l'entrée (`SettingsKeys` ou
     * `featureFlagKey`). `null` uniquement pour ACTION/INFO (aucun état).
     */
    val prefKey: String? = null,
    val keywords: List<String> = emptyList(),
)

object SettingsCatalog {

    /** Catalogue complet (entrées fixes + une entrée par fonctionnalité du manifest). */
    val entries: List<SettingsCatalogEntry> = buildList {
        add(
            SettingsCatalogEntry(
                id = "theme.mode",
                title = "Thème",
                description = "Mode clair, sombre ou système",
                destination = SettingsDestination.THEME,
                kind = SettingsEntryKind.SELECT,
                prefKey = SettingsKeys.THEME_MODE,
                keywords = listOf("apparence", "dark", "light", "sombre", "clair", "systeme"),
            ),
        )
        add(
            SettingsCatalogEntry(
                id = "theme.color_source",
                title = "Source de couleur",
                description = "Palette par défaut, Dynamic Color ou couleur personnalisée",
                destination = SettingsDestination.THEME,
                kind = SettingsEntryKind.SELECT,
                prefKey = SettingsKeys.THEME_COLOR_SOURCE,
                keywords = listOf("couleur", "palette", "dynamique", "wallpaper", "material you"),
            ),
        )
        add(
            SettingsCatalogEntry(
                id = "theme.seed_color",
                title = "Couleur personnalisée",
                description = "Graine hexadécimale de la palette (ex. #7C4DFF)",
                destination = SettingsDestination.THEME,
                kind = SettingsEntryKind.SELECT,
                prefKey = SettingsKeys.THEME_SEED_COLOR,
                keywords = listOf("seed", "graine", "hex", "couleur"),
            ),
        )
        // Une entrée par fonctionnalité du manifest — le catalogue reste
        // automatiquement synchronisé avec le manifest intégré.
        builtInSpaceKaiFeatures.features.forEach { feature ->
            add(
                SettingsCatalogEntry(
                    id = "feature.${feature.id}",
                    title = feature.name,
                    description = feature.description.ifBlank { "Fonctionnalité SpaceKai" },
                    destination = SettingsDestination.FEATURES,
                    kind = SettingsEntryKind.TOGGLE,
                    prefKey = featureFlagKey(feature.id),
                    keywords = listOf("fonctionnalite", "feature", "spacekai", feature.id),
                ),
            )
        }
        add(
            SettingsCatalogEntry(
                id = "player.orientation",
                title = "Orientation du player",
                description = "Suivre le système ou forcer le paysage",
                destination = SettingsDestination.PLAYER,
                kind = SettingsEntryKind.SELECT,
                prefKey = SettingsKeys.PLAYER_ORIENTATION,
                keywords = listOf("paysage", "portrait", "ecran", "landscape"),
            ),
        )
        add(
            SettingsCatalogEntry(
                id = "player.haptics",
                title = "Vibration",
                description = "Retour haptique lors des interactions",
                destination = SettingsDestination.PLAYER,
                kind = SettingsEntryKind.TOGGLE,
                prefKey = SettingsKeys.HAPTICS_ENABLED,
                keywords = listOf("haptique", "vibreur", "haptics"),
            ),
        )
        add(
            SettingsCatalogEntry(
                id = "updates.check",
                title = "Vérifier les mises à jour",
                description = "Sankamusic, base SimpMusic et plugins (SHA-256 vérifié)",
                destination = SettingsDestination.UPDATES,
                kind = SettingsEntryKind.ACTION,
                keywords = listOf("update", "version", "release", "maj"),
            ),
        )
        add(
            SettingsCatalogEntry(
                id = "about.version",
                title = "À propos",
                description = "Version de Sankamusic et base intégrée",
                destination = SettingsDestination.ABOUT,
                kind = SettingsEntryKind.INFO,
                keywords = listOf("version", "licence", "sankamusic"),
            ),
        )
    }

    /** Entrées d'un sous-écran donné. */
    fun entriesFor(destination: SettingsDestination): List<SettingsCatalogEntry> =
        entries.filter { it.destination == destination }

    /**
     * Recherche insensible à la casse sur titre, description et mots-clés.
     * Requête vide/blanche → catalogue complet.
     */
    fun search(query: String): List<SettingsCatalogEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return entries
        return entries.filter { entry ->
            entry.title.lowercase().contains(q) ||
                entry.description.lowercase().contains(q) ||
                entry.keywords.any { it.lowercase().contains(q) }
        }
    }
}
