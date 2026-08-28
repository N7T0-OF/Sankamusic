package com.sankamusic.core.api

/**
 * Réglages de thème portés depuis SpaceKai-OLD — `AppTheme(themeMode,
 * themeColorSource, customThemeColor)` (docs/THEME_SYSTEM.md § 4).
 *
 * Le moteur ([com.sankamusic.core.ThemeEngine]) retient ces réglages ; l'UI du
 * Core les consomme pour construire le MaterialTheme Compose (clair / sombre /
 * système, graine de couleur, Dynamic Color Android).
 */

/** Mode clair / sombre / système (suivi de la préférence système). */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

/**
 * Source de la palette de couleurs (portée de SpaceKai-OLD) :
 *
 *  - [DEFAULT] : graine par défaut de l'application ;
 *  - [WALLPAPER] : Dynamic Color Android (Material You, couleurs du fond d'écran) ;
 *  - [CUSTOM] : couleur de graine choisie par l'utilisateur (seed custom).
 */
enum class ThemeColorSource {
    DEFAULT,
    WALLPAPER,
    CUSTOM,
}

/**
 * Parse une couleur hexadécimale en Long ARGB (`0xAARRGGBB`).
 * Accepte `RRGGBB` ou `AARRGGBB`, préfixe `#` optionnel ; `null` si invalide.
 * Porté depuis SpaceKai-OLD (`parseThemeColorHex`, ui/theme/Theme.kt).
 */
fun parseThemeColorHex(hex: String): Long? {
    val clean = hex.trim().removePrefix("#")
    val argb =
        when (clean.length) {
            6 -> "FF$clean"
            8 -> clean
            else -> return null
        }
    return argb.toLongOrNull(16)
}
