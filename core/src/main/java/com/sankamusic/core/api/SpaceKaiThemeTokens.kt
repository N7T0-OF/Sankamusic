package com.sankamusic.core.api

/**
 * Tokens de thème de la plateforme (voir docs/THEME_SYSTEM.md § 2).
 *
 * ⚠️ Pas de CSS ni de dépendance Compose ici : le module core reste pur.
 * Le Theme Engine (Phase 2) mapperait ces tokens vers le MaterialTheme Compose.
 * Les couleurs sont des ARGB (`0xAARRGGBB`) ; les dimensions des Float en dp.
 */
data class SpaceKaiThemeTokens(
    // Couleurs
    val primary: Long = 0xFF6750A4,
    val onPrimary: Long = 0xFFFFFFFF,
    val secondary: Long = 0xFF625B71,
    val background: Long = 0xFFFFFBFE,
    val surface: Long = 0xFFFFFBFE,
    val onSurface: Long = 0xFF1C1B1F,
    val error: Long = 0xFFB3261E,
    // Surfaces
    val surfaceElevation: Float = 0f,
    val surfaceOpacity: Float = 1f,
    // Formes
    val radiusCard: Float = 12f,
    val radiusLarge: Float = 24f,
    // Navigation
    val navigationStyle: String = "classic", // classic | translucent | glass | minimalistic
    // Typographie (tailles en sp)
    val titleSize: Float = 22f,
    val bodySize: Float = 14f,
    // Player
    val playerOverlayOpacity: Float = 0f,
) {
    /**
     * Applique un autre jeu de tokens par-dessus celui-ci (fusion).
     *
     * Modèle « base + couche » (BetterDiscord) : un thème n'écrase que les
     * champs qu'il modifie explicitement (≠ valeurs par défaut) ; le reste de
     * la base est conservé. `base.overlay(thème)` = base avec les
     * personnalisations du thème.
     */
    fun overlay(other: SpaceKaiThemeTokens): SpaceKaiThemeTokens {
        val d = SpaceKaiThemeTokens()
        fun <T> pick(base: T, override: T, default: T): T = if (override != default) override else base
        return copy(
            primary = pick(primary, other.primary, d.primary),
            onPrimary = pick(onPrimary, other.onPrimary, d.onPrimary),
            secondary = pick(secondary, other.secondary, d.secondary),
            background = pick(background, other.background, d.background),
            surface = pick(surface, other.surface, d.surface),
            onSurface = pick(onSurface, other.onSurface, d.onSurface),
            error = pick(error, other.error, d.error),
            surfaceElevation = pick(surfaceElevation, other.surfaceElevation, d.surfaceElevation),
            surfaceOpacity = pick(surfaceOpacity, other.surfaceOpacity, d.surfaceOpacity),
            radiusCard = pick(radiusCard, other.radiusCard, d.radiusCard),
            radiusLarge = pick(radiusLarge, other.radiusLarge, d.radiusLarge),
            navigationStyle = pick(navigationStyle, other.navigationStyle, d.navigationStyle),
            titleSize = pick(titleSize, other.titleSize, d.titleSize),
            bodySize = pick(bodySize, other.bodySize, d.bodySize),
            playerOverlayOpacity = pick(playerOverlayOpacity, other.playerOverlayOpacity, d.playerOverlayOpacity),
        )
    }
}
