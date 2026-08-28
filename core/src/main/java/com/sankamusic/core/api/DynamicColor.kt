package com.sankamusic.core.api

/**
 * Dynamic Color (étape 6 migration SpaceKai — docs/MIGRATION.md).
 *
 * Porté de SimpMusic/SpaceKai-OLD (`AppTheme` : `THEME_COLOR_WALLPAPER` →
 * `platformDynamicColorScheme` Material You, sinon `rememberDynamicColorScheme`
 * généré depuis une graine ; `isWallpaperDynamicColorSupported()` = Android
 * 12+ ; règle OLED : fond/surfaces noirs purs en sombre).
 *
 * ⚠️ Le flag SpaceKai-OLD `dynamic_color` n'était PAS câblé (toggle déclaré
 * sans effet) — l'intention est portée par `ThemeColorSource.WALLPAPER`
 * (étape 2). Le Core reste pur : les décisions ci-dessous sont des fonctions
 * pures ; l'UI (Android) fournit le support réel (`isWallpaperDynamicColorSupported`)
 * et applique la stratégie résolue.
 */

/**
 * Stratégie de palette résolue, consommée par l'UI pour choisir le schéma :
 *
 *  - [WALLPAPER_DYNAMIC] : couleurs dynamiques du système (Material You, fond
 *    d'écran) — `dynamicDarkColorScheme` / `dynamicLightColorScheme` Android 12+ ;
 *  - [SEED_GENERATED] : palette générée depuis une graine (défaut ou custom).
 */
enum class ColorSchemeStrategy {
    WALLPAPER_DYNAMIC,
    SEED_GENERATED,
}

/**
 * Décide la stratégie de palette (port de `AppTheme`) :
 * [ThemeColorSource.WALLPAPER] + support dynamique → [ColorSchemeStrategy.WALLPAPER_DYNAMIC],
 * sinon [ColorSchemeStrategy.SEED_GENERATED]. Repli sûr : WALLPAPER sans support
 * → palette par graine, jamais d'écran cassé.
 */
fun resolveColorSchemeStrategy(
    source: ThemeColorSource,
    dynamicColorSupported: Boolean,
): ColorSchemeStrategy =
    if (source == ThemeColorSource.WALLPAPER && dynamicColorSupported) {
        ColorSchemeStrategy.WALLPAPER_DYNAMIC
    } else {
        ColorSchemeStrategy.SEED_GENERATED
    }

/**
 * Graine effective : [ThemeColorSource.CUSTOM] → la graine custom ; sinon
 * `null` = graine par défaut de l'application (l'UI fournit sa valeur).
 */
fun effectiveSeedColor(source: ThemeColorSource, customSeedColor: Long?): Long? =
    if (source == ThemeColorSource.CUSTOM) customSeedColor else null

/**
 * Règle OLED (port de `platformDynamicColorScheme` : `dynamicDarkColorScheme
 * (context).copy(background = Black, surface = Black)` et `isAmoled = true`) :
 * en mode sombre, fond et surfaces passent au noir pur ; sinon inchangé.
 */
fun SpaceKaiThemeTokens.withOledPinning(isDark: Boolean): SpaceKaiThemeTokens =
    if (isDark) copy(background = 0xFF000000, surface = 0xFF000000) else this
