package com.sankamusic.core.api

/**
 * Retour haptique (étape 5 migration SpaceKai — docs/MIGRATION.md).
 *
 * Porté depuis SpaceKai-OLD (`spacekai/features/haptics/HapticsSpaceKai.kt`) :
 * le flag `haptics` (défaut off) et `onClick` qui ne déclenche le retour
 * haptique que si le flag est actif — no-op sinon. Côté Core, la décision est
 * pure et la préférence est persistable via `SettingsApi` ; l'UI (Android)
 * déclenchera la vibration réelle (`LocalHapticFeedback`) selon
 * [shouldFireHaptic].
 */

/**
 * Type de retour haptique (port des valeurs Compose `HapticFeedbackType`
 * utilisées par SpaceKai-OLD — l'usage réel est [LONG_PRESS] sur les clics).
 */
enum class HapticType {
    /** Vibration standard de confirmation (clic/bascule) — usage SpaceKai-OLD. */
    LONG_PRESS,

    /** Confirmation d'une action (ex. toggle activé). */
    CONFIRM,

    /** Retour de déplacement de poignée de texte (champ de saisie). */
    TEXT_HANDLE_MOVE,
}

/**
 * Réglage du retour haptique. Désactivé par défaut (comme le flag SpaceKai-OLD
 * `haptics = false`). Clé : [SettingsKeys.HAPTICS_ENABLED].
 */
data class HapticsSettings(val enabled: Boolean = false)

/**
 * Porte le flag SpaceKai-OLD `haptics` (booléen) vers le réglage — parité de
 * migration (docs/MIGRATION.md étape 5).
 */
fun hapticsEnabledFromFeatureFlag(haptics: Boolean): Boolean = haptics

/**
 * Parse la valeur de préférence persistée (`"on"` | `"off"`, variantes
 * `"true"/"false"/"1"/"0"`, espaces et casse tolérés) ; `null` si inconnue.
 */
fun parseHapticsEnabled(value: String?): Boolean? =
    when (value?.trim()?.lowercase()) {
        "on", "true", "1" -> true
        "off", "false", "0" -> false
        else -> null
    }

/**
 * Valeur effective depuis une préférence brute : inconnue / absente → désactivé
 * (défaut sûr, l'app ne casse jamais sur une valeur corrompue).
 */
fun effectiveHapticsEnabled(value: String?): Boolean =
    parseHapticsEnabled(value) ?: false

/** Valeur de préférence stable (`"on"` / `"off"`), persistée via SettingsApi. */
fun hapticsPreferenceValue(enabled: Boolean): String = if (enabled) "on" else "off"

/**
 * Décision pure portée de `HapticsSpaceKai.onClick` : le retour haptique n'est
 * déclenché que si le réglage est actif (no-op sinon). L'UI appelle le
 * `HapticFeedback` de la plateforme uniquement quand ceci retourne vrai.
 */
fun shouldFireHaptic(enabled: Boolean): Boolean = enabled
