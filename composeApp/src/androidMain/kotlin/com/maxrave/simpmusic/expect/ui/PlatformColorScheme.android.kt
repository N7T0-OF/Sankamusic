package com.maxrave.simpmusic.expect.ui

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.maxrave.simpmusic.extension.getActivityOrNull

@Composable
actual fun platformDynamicColorScheme(isDark: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return if (isDark) {
        // Let the system dynamic scheme drive the background too. Overriding background/surface
        // with pure black here is what made "Couleurs du téléphone" never change the page
        // background — the dynamic palette was applied to the surfaces but the background stayed
        // forced to black. dynamicDarkColorScheme already provides a dark, wallpaper-derived
        // background.
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
}

actual fun isWallpaperDynamicColorSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
actual fun SystemBarAppearanceEffect(isDark: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = context.getActivityOrNull()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            // Light theme → dark icons; dark theme → light icons.
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }
}
