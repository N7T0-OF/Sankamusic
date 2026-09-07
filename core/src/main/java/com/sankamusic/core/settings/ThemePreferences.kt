package com.sankamusic.core.settings

import com.sankamusic.core.api.SettingsKeys
import com.sankamusic.core.api.ThemeColorSource
import com.sankamusic.core.api.ThemeMode
import com.sankamusic.core.api.parseThemeColorHex

/**
 * Préférences de thème persistées (port de `AppTheme(themeMode, themeColorSource,
 * customThemeColor)` de SpaceKai-OLD — docs/THEME_SYSTEM.md § 4, MIGRATION.md
 * étape 7). Ferme le gap « persistance réelle » : le [ThemeEngine] conserve un
 * état en mémoire, ces préférences survivent au processus.
 */

/** Mode clair / sombre / système — `theme.mode`. */
val themeModePreference: Preference<ThemeMode> = enumPreference(
    key = SettingsKeys.THEME_MODE,
    default = ThemeMode.DARK,
    parse = { raw ->
        raw?.trim()?.let { value ->
            ThemeMode.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    },
    serialize = { it.name },
)

/** Source de la palette (défaut / Dynamic Color / seed custom) — `theme.color_source`. */
val themeColorSourcePreference: Preference<ThemeColorSource> = enumPreference(
    key = SettingsKeys.THEME_COLOR_SOURCE,
    default = ThemeColorSource.DEFAULT,
    parse = { raw ->
        raw?.trim()?.let { value ->
            ThemeColorSource.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    },
    serialize = { it.name },
)

/** Graine custom, stockée en hexadécimal (`#RRGGBB` ou `#AARRGGBB`) — `theme.seed_color`. */
val themeSeedColorPreference: Preference<String> = stringPreference(SettingsKeys.THEME_SEED_COLOR, "")

/** Sérialise une graine ARGB en valeur de préférence hexadécimale (`#RRGGBB`). */
fun themeSeedColorToPreferenceValue(argb: Long): String = "#%06X".format(argb and 0xFFFFFFL)

/** Parse la valeur de préférence de la graine → Long ARGB (`null` si absente/invalide). */
fun themeSeedColorFromPreferenceValue(raw: String?): Long? =
    raw?.takeIf { it.isNotBlank() }?.let { parseThemeColorHex(it) }
