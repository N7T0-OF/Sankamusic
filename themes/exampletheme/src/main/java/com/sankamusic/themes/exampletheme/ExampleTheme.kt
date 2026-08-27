package com.sankamusic.themes.exampletheme

import com.sankamusic.core.api.SpaceKaiThemeTokens
import com.sankamusic.core.api.ThemeDefinition

/**
 * Thème d'exemple (Phase 3 — voir docs/THEME_SYSTEM.md).
 *
 * Démonstrations :
 *  - une définition complète (id, version, apiVersion, base, tokens) ;
 *  - des tokens pour les couleurs, surfaces, formes, typographie, navigation
 *    et l'overlay du player ;
 *  - la validation et l'activation à travers le [com.sankamusic.core.ThemeEngine].
 *
 * ⚠️ Le mapping vers MaterialTheme Compose (couleurs Material 3, Dynamic Color,
 * live editing) sera fait par l'UI du Core en Phase 2/3.
 */
object ExampleTheme {

    const val ID = "com.souanpt.spacekai.theme.example"

    val definition = ThemeDefinition(
        id = ID,
        name = "ExampleTheme",
        version = "1.0.0",
        apiVersion = 1,
        base = "dark",
        tokens = SpaceKaiThemeTokens(
            // Couleurs (Material 3 dark)
            primary = 0xFFBB86FC,
            onPrimary = 0xFF000000,
            secondary = 0xFF03DAC6,
            background = 0xFF121212,
            surface = 0xFF1E1E1E,
            onSurface = 0xFFE0E0E0,
            error = 0xFFCF6679,
            // Surfaces
            surfaceElevation = 0f,
            surfaceOpacity = 1f,
            // Formes
            radiusCard = 16f,
            radiusLarge = 28f,
            // Navigation
            navigationStyle = "minimalistic",
            // Typographie (sp)
            titleSize = 20f,
            bodySize = 14f,
            // Player
            playerOverlayOpacity = 0.2f,
        ),
    )
}
