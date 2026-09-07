package com.sankamusic.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import com.sankamusic.core.api.PlayerOrientationMode
import com.sankamusic.core.api.SettingsKeys
import com.sankamusic.core.api.ThemeColorSource
import com.sankamusic.core.api.ThemeMode
import com.sankamusic.core.api.builtInSpaceKaiFeatures
import com.sankamusic.core.api.parsePlayerOrientationMode
import com.sankamusic.core.api.toPreferenceValue
import com.sankamusic.core.update.CompatibilityStatus
import com.sankamusic.core.update.SimpMusicAdapter
import com.sankamusic.core.update.featureCompatibility
import com.sankamusic.core.settings.SpaceKaiFeatureFlags
import com.sankamusic.core.settings.booleanPreference
import com.sankamusic.core.settings.enumPreference
import com.sankamusic.core.settings.themeSeedColorFromPreferenceValue
import com.sankamusic.core.settings.themeSeedColorToPreferenceValue
import kotlinx.coroutines.launch

/**
 * Sous-écrans du shell Paramètres (Thème, Fonctionnalités, Player, À propos).
 * Chaque contrôle écrit via les API réelles du Core ([DefaultSpaceKaiApi]) :
 *  - thème → [DefaultSpaceKaiApi.theme] (persisté par la couche API) ;
 *  - fonctionnalités → manifest + [SpaceKaiFeatureFlags] (clé par fonctionnalité) ;
 *  - player → préférences typées `player.orientation` et `haptics.enabled` ;
 *  - à propos → faits réels (BuildConfig, Adapter), aucune action décorative.
 */

/** Préférence persistée de l'orientation du player (étape 3 migration). */
private val orientationPreference = enumPreference(
    key = SettingsKeys.PLAYER_ORIENTATION,
    default = PlayerOrientationMode.FOLLOW_SYSTEM,
    parse = { parsePlayerOrientationMode(it) },
    serialize = { it.toPreferenceValue() },
)

/** Préférence persistée du retour haptique (étape 5 migration). */
private val hapticsPreference = booleanPreference(SettingsKeys.HAPTICS_ENABLED, false)

/** Écran de secours partagé quand l'API n'est pas initialisée. */
@Composable
private fun UninitializedApi(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SettingsSubScreenHeader("Paramètres", onBack)
        Text(
            text = "API non initialisée",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Ligne de sélection radio réutilisable. */
@Composable
private fun RadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        RadioButton(selected = selected, onClick = onSelect)
    }
}

/** ── Thème ─────────────────────────────────────────────────────────────── */

@Composable
fun ThemeScreen(api: DefaultSpaceKaiApi?, onBack: () -> Unit) {
    if (api == null) {
        UninitializedApi(onBack)
        return
    }
    val themeState by api.themeEngine.state.collectAsState()
    val scope = rememberCoroutineScope()
    var seedInput by rememberSaveable {
        mutableStateOf(themeState.customSeedColor?.let { themeSeedColorToPreferenceValue(it) } ?: "")
    }
    val parsedSeed = themeSeedColorFromPreferenceValue(seedInput)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSubScreenHeader("Thème", onBack)

        SettingsSectionHeader("Mode")
        SettingsCard {
            ThemeMode.entries.forEachIndexed { index, mode ->
                if (index > 0) SettingsRowDivider()
                RadioRow(
                    title = when (mode) {
                        ThemeMode.LIGHT -> "Clair"
                        ThemeMode.DARK -> "Sombre"
                        ThemeMode.SYSTEM -> "Système"
                    },
                    selected = themeState.mode == mode,
                    onSelect = { scope.launch { api.theme.setMode(mode) } },
                )
            }
        }

        SettingsSectionHeader("Source de couleur")
        SettingsCard {
            ThemeColorSource.entries.forEachIndexed { index, source ->
                if (index > 0) SettingsRowDivider()
                RadioRow(
                    title = when (source) {
                        ThemeColorSource.DEFAULT -> "Défaut"
                        ThemeColorSource.WALLPAPER -> "Dynamic Color (fond d'écran)"
                        ThemeColorSource.CUSTOM -> "Couleur personnalisée"
                    },
                    selected = themeState.colorSource == source,
                    onSelect = {
                        scope.launch {
                            // CUSTOM sans graine valide → échec propre du moteur :
                            // saisir la couleur ci-dessous puis appliquer.
                            if (source == ThemeColorSource.CUSTOM) {
                                parsedSeed?.let { api.theme.setColorSource(ThemeColorSource.CUSTOM, it) }
                            } else {
                                api.theme.setColorSource(source, null)
                            }
                        }
                    },
                )
            }
        }

        // Champ de saisie de la graine — ferme le gap « couleur custom » de
        // MIGRATION.md étape 7 (l'état CUSTOM n'est appliqué que sur graine valide).
        SettingsSectionHeader("Couleur personnalisée")
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = seedInput,
                    onValueChange = { seedInput = it },
                    label = { Text("Graine hexadécimale (ex. #7C4DFF)") },
                    singleLine = true,
                    isError = seedInput.isNotBlank() && parsedSeed == null,
                    supportingText = {
                        Text(
                            when {
                                seedInput.isBlank() ->
                                    "Saisir une couleur puis appliquer pour activer CUSTOM."
                                parsedSeed == null ->
                                    "Hexadécimal invalide (RRGGBB ou AARRGGBB)."
                                else ->
                                    "Appliquer pour utiliser cette graine (persistée)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = parsedSeed != null,
                    onClick = {
                        scope.launch { api.theme.setColorSource(ThemeColorSource.CUSTOM, parsedSeed) }
                    },
                ) {
                    Text("Appliquer")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** ── Fonctionnalités SpaceKai ──────────────────────────────────────────── */

@Composable
fun FeaturesScreen(api: DefaultSpaceKaiApi?, onBack: () -> Unit) {
    if (api == null) {
        UninitializedApi(onBack)
        return
    }
    val typed = api.typedSettings
    val adapter = remember { SimpMusicAdapter() }
    val upstreamVersion = adapter.info.version
    val manifest = builtInSpaceKaiFeatures
    val featureStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            manifest.features.forEach { feature ->
                this[feature.id] = SpaceKaiFeatureFlags.isEnabled(typed, feature.id, upstreamVersion, adapter)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSubScreenHeader("Fonctionnalités SpaceKai", onBack)
        Text(
            text = "Base SimpMusic $upstreamVersion — une fonctionnalité incompatible " +
                "avec la base installée est désactivée, jamais cassée.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        // Sections repliables : la liste des fonctionnalités se replie pour
        // laisser la place aux explications de compatibilité.
        CollapsibleSettingsCard(title = "Fonctionnalités (${manifest.features.size})") {
            manifest.features.forEachIndexed { index, feature ->
                if (index > 0) SettingsRowDivider()
                val compat = featureCompatibility(manifest, feature, upstreamVersion, adapter)
                SettingsRow(
                    title = feature.name,
                    subtitle = when (compat.status) {
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
                    enabled = compat.compatible,
                    trailing = {
                        Switch(
                            checked = compat.compatible && (featureStates[feature.id] ?: false),
                            enabled = compat.compatible,
                            onCheckedChange = { on ->
                                featureStates[feature.id] = on
                                SpaceKaiFeatureFlags.setEnabled(typed, feature.id, on, manifest)
                            },
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** ── Player ────────────────────────────────────────────────────────────── */

@Composable
fun PlayerScreen(api: DefaultSpaceKaiApi?, onBack: () -> Unit) {
    if (api == null) {
        UninitializedApi(onBack)
        return
    }
    val typed = api.typedSettings
    var orientation by rememberSaveable { mutableStateOf(typed.get(orientationPreference)) }
    var hapticsOn by rememberSaveable { mutableStateOf(typed.get(hapticsPreference)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSubScreenHeader("Player", onBack)

        SettingsSectionHeader("Orientation du player")
        SettingsCard {
            PlayerOrientationMode.entries.forEachIndexed { index, mode ->
                if (index > 0) SettingsRowDivider()
                RadioRow(
                    title = when (mode) {
                        PlayerOrientationMode.FOLLOW_SYSTEM -> "Système"
                        PlayerOrientationMode.FORCE_LANDSCAPE -> "Paysage"
                    },
                    selected = orientation == mode,
                    onSelect = {
                        orientation = mode
                        typed.set(orientationPreference, mode)
                    },
                )
            }
        }

        SettingsSectionHeader("Retour haptique")
        SettingsCard {
            SettingsRow(
                title = "Vibration",
                subtitle = "Retour haptique lors des interactions",
                trailing = {
                    Switch(
                        checked = hapticsOn,
                        onCheckedChange = { on ->
                            hapticsOn = on
                            typed.set(hapticsPreference, on)
                        },
                    )
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** ── À propos ──────────────────────────────────────────────────────────── */

@Composable
fun AboutScreen(api: DefaultSpaceKaiApi?, onBack: () -> Unit) {
    if (api == null) {
        UninitializedApi(onBack)
        return
    }
    val adapter = remember { SimpMusicAdapter() }
    val adapterInfo = adapter.info

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSubScreenHeader("À propos", onBack)
        SettingsCard {
            SettingsRow(
                title = "Version",
                subtitle = "Sankamusic ${BuildConfig.SANKAMUSIC_VERSION}",
            )
            SettingsRowDivider()
            SettingsRow(
                title = "Base intégrée",
                subtitle = "${BuildConfig.SANKAMUSIC_UPSTREAM_BASE} ${adapterInfo.version} " +
                    "(Adapter v${adapterInfo.adapterVersion})",
            )
            SettingsRowDivider()
            SettingsRow(
                title = "Couche de personnalisation",
                subtitle = "SpaceKai — thèmes, plugins, paramètres",
            )
            SettingsRowDivider()
            SettingsRow(
                title = "Projet",
                subtitle = "github.com/N7T0-OF/Sankamusic",
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
