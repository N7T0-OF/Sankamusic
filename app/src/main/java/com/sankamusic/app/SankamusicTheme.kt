package com.sankamusic.app

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.sankamusic.core.ThemeState
import com.sankamusic.core.api.ColorSchemeStrategy
import com.sankamusic.core.api.SpaceKaiThemeTokens
import com.sankamusic.core.api.ThemeMode
import com.sankamusic.core.api.resolveColorSchemeStrategy
import com.sankamusic.core.api.withOledPinning
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Thème Compose de l'application (docs/THEME_SYSTEM.md § 4/6).
 *
 * Consomme l'état réactif du [com.sankamusic.core.ThemeEngine] (via
 * `DefaultSpaceKaiApi.themeEngine.state`) :
 *  - mode clair / sombre / système ;
 *  - source de couleur : Dynamic Color Android 12+ (WALLPAPER) ou palette
 *    générée depuis les tokens (défaut / thème actif, base + couche) ;
 *  - règle OLED : fond/surfaces noirs purs en sombre.
 *
 * ⚠️ Le mapping des tokens est partiel (couleurs principales) — les surfaces
 * Material 3 dérivées et la typographie seront affinées avec l'UI complète.
 */
@Composable
fun SankamusicTheme(
    api: DefaultSpaceKaiApi?,
    content: @Composable () -> Unit,
) {
    val stateFlow = api?.themeEngine?.state ?: remember { MutableStateFlow(ThemeState()) }
    val state by stateFlow.collectAsState()

    val isDark =
        when (state.mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme =
        if (resolveColorSchemeStrategy(state.colorSource, dynamicColorSupported) == ColorSchemeStrategy.WALLPAPER_DYNAMIC) {
            dynamicWallpaperScheme(isDark)
        } else {
            val base =
                if (isDark) {
                    api?.themeEngine?.darkBaseTokens() ?: SpaceKaiThemeTokens()
                } else {
                    api?.themeEngine?.lightBaseTokens() ?: SpaceKaiThemeTokens()
                }
            tokensToColorScheme(state.activeTokens ?: base, isDark)
        }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** Dynamic Color Android (Material You) — OLED pinning en sombre (port SpaceKai-OLD). */
@Composable
private fun dynamicWallpaperScheme(isDark: Boolean): ColorScheme {
    val context = LocalContext.current
    val scheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return if (isDark) scheme.copy(background = Color.Black, surface = Color.Black) else scheme
}

/** Mappe les tokens (base + couche, OLED appliqué) vers une [ColorScheme] Material 3. */
private fun tokensToColorScheme(tokens: SpaceKaiThemeTokens, isDark: Boolean): ColorScheme {
    val effective = if (isDark) tokens.withOledPinning(true) else tokens
    val primary = Color(effective.primary)
    val onPrimary = Color(effective.onPrimary)
    val secondary = Color(effective.secondary)
    val background = Color(effective.background)
    val surface = Color(effective.surface)
    val onSurface = Color(effective.onSurface)
    val error = Color(effective.error)
    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            background = background,
            surface = surface,
            onSurface = onSurface,
            error = error,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            background = background,
            surface = surface,
            onSurface = onSurface,
            error = error,
        )
    }
}
