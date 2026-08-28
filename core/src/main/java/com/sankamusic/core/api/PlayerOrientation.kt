package com.sankamusic.core.api

/**
 * Orientation du player (étape 3 migration SpaceKai — docs/MIGRATION.md).
 *
 * Porté depuis SpaceKai-OLD :
 *  - l'enum `Orientation` (`expect/ui/Orientation.kt`) ;
 *  - le flag `landscape_player` (`SpaceKaiFeatures`) ;
 *  - le plein écran du player qui force `SCREEN_ORIENTATION_LANDSCAPE` et
 *    restaure l'orientation d'origine à la sortie (`FullscreenPlayer.android.kt`).
 *
 * Le Core reste pur (aucune API Android ici) : la décision est une fonction
 * pure, la préférence est une chaîne persistable via `SettingsApi` ; l'UI du
 * player (étape 4) appliquera l'orientation résolue.
 */

/** Orientation de l'écran (port de SpaceKai-OLD `expect/ui/Orientation.kt`). */
enum class Orientation {
    PORTRAIT,
    LANDSCAPE,
    UNSPECIFIED,
}

/**
 * Mode d'orientation du player :
 *
 *  - [FOLLOW_SYSTEM] : le player suit l'orientation du système (défaut) ;
 *  - [FORCE_LANDSCAPE] : le player force le paysage — équivalent du flag
 *    SpaceKai-OLD `landscape_player = true` et du plein écran qui demande
 *    `SCREEN_ORIENTATION_LANDSCAPE`.
 */
enum class PlayerOrientationMode {
    FOLLOW_SYSTEM,
    FORCE_LANDSCAPE,
}

/**
 * Résout l'orientation effective du player. En [PlayerOrientationMode.FORCE_LANDSCAPE]
 * le player est toujours en [Orientation.LANDSCAPE] ; sinon il suit l'orientation
 * courante. Fonction pure, testée (docs/MIGRATION.md étape 3).
 */
fun resolvePlayerOrientation(mode: PlayerOrientationMode, current: Orientation): Orientation =
    when (mode) {
        PlayerOrientationMode.FOLLOW_SYSTEM -> current
        PlayerOrientationMode.FORCE_LANDSCAPE -> Orientation.LANDSCAPE
    }

/**
 * Parse la valeur de préférence persistée (`"system"` | `"landscape"`,
 * espaces et casse tolérés) ; `null` si la valeur est inconnue.
 * Clé : [SettingsKeys.PLAYER_ORIENTATION] (SettingsApi).
 */
fun parsePlayerOrientationMode(value: String?): PlayerOrientationMode? =
    when (value?.trim()?.lowercase()) {
        "system", "follow_system" -> PlayerOrientationMode.FOLLOW_SYSTEM
        "landscape", "force_landscape" -> PlayerOrientationMode.FORCE_LANDSCAPE
        else -> null
    }

/**
 * Mode effectif depuis une préférence brute : inconnue / absente → défaut
 * [PlayerOrientationMode.FOLLOW_SYSTEM] (l'app ne casse jamais sur une valeur
 * corrompue).
 */
fun effectivePlayerOrientationMode(value: String?): PlayerOrientationMode =
    parsePlayerOrientationMode(value) ?: PlayerOrientationMode.FOLLOW_SYSTEM

/** Valeur de préférence stable d'un mode (persistée via SettingsApi). */
fun PlayerOrientationMode.toPreferenceValue(): String =
    when (this) {
        PlayerOrientationMode.FOLLOW_SYSTEM -> "system"
        PlayerOrientationMode.FORCE_LANDSCAPE -> "landscape"
    }

/**
 * Porte le flag SpaceKai-OLD `landscape_player` (booléen) vers le mode
 * d'orientation — parité de migration (docs/MIGRATION.md étape 3).
 */
fun playerOrientationModeFromFeatureFlag(landscapePlayer: Boolean): PlayerOrientationMode =
    if (landscapePlayer) PlayerOrientationMode.FORCE_LANDSCAPE else PlayerOrientationMode.FOLLOW_SYSTEM

/** Clés de préférences de la plateforme (SettingsApi). */
object SettingsKeys {
    /** Mode d'orientation du player : valeur de [PlayerOrientationMode.toPreferenceValue]. */
    const val PLAYER_ORIENTATION = "player.orientation"
}
