package com.sankamusic.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sankamusic.core.api.PlayerOrientationMode
import com.sankamusic.core.api.SettingsKeys
import com.sankamusic.core.api.ThemeColorSource
import com.sankamusic.core.api.ThemeMode
import com.sankamusic.core.api.builtInSpaceKaiFeatures
import com.sankamusic.core.api.parsePlayerOrientationMode
import com.sankamusic.core.api.toPreferenceValue
import com.sankamusic.core.settings.SpaceKaiFeatureFlags
import com.sankamusic.core.settings.booleanPreference
import com.sankamusic.core.settings.enumPreference
import com.sankamusic.core.update.CompatibilityStatus
import com.sankamusic.core.update.SimpMusicAdapter
import com.sankamusic.core.update.featureCompatibility
import kotlinx.coroutines.launch

/** Préférence persistée de l'orientation du player (étape 3). */
private val orientationPreference = enumPreference(
    key = SettingsKeys.PLAYER_ORIENTATION,
    default = PlayerOrientationMode.FOLLOW_SYSTEM,
    parse = { parsePlayerOrientationMode(it) },
    serialize = { it.toPreferenceValue() },
)

/** Préférence persistée du retour haptique (étape 5). */
private val hapticsPreference = booleanPreference(SettingsKeys.HAPTICS_ENABLED, false)

/**
 * Écran Paramètres (étape 7 migration SpaceKai — docs/MIGRATION.md).
 *
 * Branché sur `DefaultSpaceKaiApi.typedSettings` + `themeEngine` et le manifest
 * des fonctionnalités (`builtInSpaceKaiFeatures`) : chaque fonctionnalité a un
 * toggle individuel, désactivé si elle est incompatible avec la version de la
 * base SimpMusic installée (jamais d'APK cassée). Sélecteurs thème (mode +
 * source de couleur), orientation du player et haptique.
 */
@Composable
fun SettingsScreen(
    api: DefaultSpaceKaiApi?,
    onOpenUpdates: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    if (api == null) {
        Text(
            text = "API non initialisée",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    val typed = api.typedSettings
    val adapter = remember { SimpMusicAdapter() }
    val upstreamVersion = adapter.info.version
    val manifest = builtInSpaceKaiFeatures

    // États locaux (réactivité Compose) initialisés depuis l'API.
    var selectedMode by rememberSaveable { mutableStateOf(api.themeEngine.mode()) }
    var selectedSource by rememberSaveable { mutableStateOf(api.themeEngine.colorSource()) }
    var orientation by rememberSaveable { mutableStateOf(typed.get(orientationPreference)) }
    var hapticsOn by rememberSaveable { mutableStateOf(typed.get(hapticsPreference)) }
    val featureStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            manifest.features.forEach { feature ->
                this[feature.id] = SpaceKaiFeatureFlags.isEnabled(typed, feature.id, upstreamVersion, adapter)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ── Fonctionnalités SpaceKai ────────────────────────────────────
        item {
            Text("Fonctionnalités SpaceKai", style = MaterialTheme.typography.titleMedium)
        }
        manifest.features.forEach { feature ->
            val compat = featureCompatibility(manifest, feature, upstreamVersion, adapter)
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(feature.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = when (compat.status) {
                                CompatibilityStatus.COMPATIBLE ->
                                    "Compatible SimpMusic $upstreamVersion"
                                CompatibilityStatus.VERSION_OUT_OF_RANGE ->
                                    "Incompatible : SimpMusic $upstreamVersion hors de ${feature.upstreamCompatibility}"
                                CompatibilityStatus.CONTRACT_NOT_SATISFIED ->
                                    "Incompatible : contrat '${feature.contract}' non fourni par l'Adapter"
                                CompatibilityStatus.UNKNOWN_UPSTREAM ->
                                    "Version upstream inconnue — désactivée"
                                CompatibilityStatus.FEATURE_UNKNOWN ->
                                    "Fonctionnalité inconnue du manifest"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = compat.compatible && (featureStates[feature.id] ?: false),
                        enabled = compat.compatible,
                        onCheckedChange = { on ->
                            featureStates[feature.id] = on
                            SpaceKaiFeatureFlags.setEnabled(typed, feature.id, on, manifest)
                        },
                    )
                }
            }
        }
        item { HorizontalDivider() }

        // ── Thème ───────────────────────────────────────────────────────
        item { Text("Thème", style = MaterialTheme.typography.titleMedium) }
        item { Text("Mode", style = MaterialTheme.typography.bodyMedium) }
        ThemeMode.entries.forEach { mode ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedMode == mode,
                        onClick = {
                            selectedMode = mode
                            scope.launch { api.theme.setMode(mode) }
                        },
                    )
                    Text(
                        text = when (mode) {
                            ThemeMode.LIGHT -> "Clair"
                            ThemeMode.DARK -> "Sombre"
                            ThemeMode.SYSTEM -> "Système"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        item { Text("Couleur", style = MaterialTheme.typography.bodyMedium) }
        ThemeColorSource.entries.forEach { source ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedSource == source,
                        onClick = {
                            selectedSource = source
                            scope.launch { api.theme.setColorSource(source) }
                        },
                    )
                    Text(
                        text = when (source) {
                            ThemeColorSource.DEFAULT -> "Défaut"
                            ThemeColorSource.WALLPAPER -> "Dynamic Color (fond d'écran)"
                            ThemeColorSource.CUSTOM -> "Couleur personnalisée"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        item { HorizontalDivider() }

        // ── Player ──────────────────────────────────────────────────────
        item { Text("Player", style = MaterialTheme.typography.titleMedium) }
        item { Text("Orientation", style = MaterialTheme.typography.bodyMedium) }
        PlayerOrientationMode.entries.forEach { mode ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = orientation == mode,
                        onClick = {
                            orientation = mode
                            typed.set(orientationPreference, mode)
                        },
                    )
                    Text(
                        text = when (mode) {
                            PlayerOrientationMode.FOLLOW_SYSTEM -> "Système"
                            PlayerOrientationMode.FORCE_LANDSCAPE -> "Paysage"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Vibration",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = hapticsOn,
                    onCheckedChange = { on ->
                        hapticsOn = on
                        typed.set(hapticsPreference, on)
                    },
                )
            }
        }
        item { HorizontalDivider() }

        // ── Mises à jour ────────────────────────────────────────────────
        item {
            Button(onClick = onOpenUpdates) { Text("Mises à jour") }
        }
    }
}
