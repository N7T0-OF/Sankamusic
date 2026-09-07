package com.sankamusic.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sankamusic.core.api.UpdateManager
import com.sankamusic.core.api.UpdateState
import com.sankamusic.core.settings.SettingsCatalog
import com.sankamusic.core.settings.SettingsDestination

/**
 * Écran Paramètres — shell inspiré de Convx (réimplémenté, zéro dépendance au
 * code Convx) : titre large, recherche sur le [SettingsCatalog] réel (une
 * entrée par réglage existant), cartes groupées par sous-écran (Thème,
 * Fonctionnalités, Player, Mises à jour, À propos), badge de mise à jour.
 *
 * Chaque contrôle est routé vers les API réelles du Core via
 * [DefaultSpaceKaiApi] — aucun réglage décoratif ; les préférences de la base
 * SimpMusic non intégrées (Phase 2) ne sont pas affichées plutôt que simulées.
 */
@Composable
fun SettingsScreen(
    api: DefaultSpaceKaiApi?,
    updateManager: UpdateManager?,
    onOpenUpdates: () -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf<SettingsDestination?>(null) }
    var updateAvailable by rememberSaveable { mutableStateOf(false) }

    // Vérification unique au premier affichage — badge uniquement si une mise
    // à jour stable est CONFIRMÉE (jamais de badge décoratif sur échec réseau).
    LaunchedEffect(updateManager) {
        val status = updateManager?.let { runCatching { it.checkSankamusicUpdate() }.getOrNull() }
        if (status?.state == UpdateState.UPDATE_AVAILABLE) {
            updateAvailable = true
        }
    }
    BackHandler(enabled = destination != null) { destination = null }

    val onNavigate: (SettingsDestination) -> Unit = { dest ->
        if (dest == SettingsDestination.UPDATES) onOpenUpdates() else destination = dest
    }

    when (val target = destination) {
        null -> SettingsHome(api = api, updateAvailable = updateAvailable, onNavigate = onNavigate)
        SettingsDestination.THEME -> ThemeScreen(api) { destination = null }
        SettingsDestination.FEATURES -> FeaturesScreen(api) { destination = null }
        SettingsDestination.PLAYER -> PlayerScreen(api) { destination = null }
        SettingsDestination.ABOUT -> AboutScreen(api) { destination = null }
        // Mises à jour : écran existant à l'échelle de l'activité, via onOpenUpdates.
        SettingsDestination.UPDATES -> Unit
    }
}

@Composable
private fun SettingsHome(
    api: DefaultSpaceKaiApi?,
    updateAvailable: Boolean,
    onNavigate: (SettingsDestination) -> Unit,
) {
    if (api == null) {
        Text(
            text = "API non initialisée",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    var query by rememberSaveable { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Titre large (style iOS de Convx).
        item(key = "title") {
            Text(
                text = "Paramètres",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        item(key = "search") {
            SettingsSearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (query.isBlank()) {
            item(key = "general_header") { SettingsSectionHeader("Général") }
            item(key = "general_card") {
                SettingsCard {
                    SettingsRow(
                        title = "Thème",
                        subtitle = "Mode, source de couleur, couleur personnalisée",
                        icon = destinationIcon(SettingsDestination.THEME),
                        iconTint = destinationTint(SettingsDestination.THEME),
                        showChevron = true,
                        onClick = { onNavigate(SettingsDestination.THEME) },
                    )
                    SettingsRowDivider()
                    SettingsRow(
                        title = "Player",
                        subtitle = "Orientation, vibration",
                        icon = destinationIcon(SettingsDestination.PLAYER),
                        iconTint = destinationTint(SettingsDestination.PLAYER),
                        showChevron = true,
                        onClick = { onNavigate(SettingsDestination.PLAYER) },
                    )
                }
            }

            item(key = "spacekai_header") { SettingsSectionHeader("SpaceKai") }
            item(key = "spacekai_card") {
                SettingsCard {
                    SettingsRow(
                        title = "Fonctionnalités SpaceKai",
                        subtitle = "${SettingsCatalog.entriesFor(SettingsDestination.FEATURES).size} " +
                            "fonctionnalités — compatibilité SimpMusic vérifiée",
                        icon = destinationIcon(SettingsDestination.FEATURES),
                        iconTint = destinationTint(SettingsDestination.FEATURES),
                        showChevron = true,
                        onClick = { onNavigate(SettingsDestination.FEATURES) },
                    )
                }
            }

            item(key = "system_header") { SettingsSectionHeader("Système") }
            item(key = "system_card") {
                SettingsCard {
                    SettingsRow(
                        title = "Mises à jour",
                        subtitle = "Sankamusic, base SimpMusic et plugins",
                        icon = destinationIcon(SettingsDestination.UPDATES),
                        iconTint = destinationTint(SettingsDestination.UPDATES),
                        badge = if (updateAvailable) "Disponible" else null,
                        showChevron = true,
                        onClick = { onNavigate(SettingsDestination.UPDATES) },
                    )
                    SettingsRowDivider()
                    SettingsRow(
                        title = "À propos",
                        subtitle = "Version, base intégrée",
                        icon = destinationIcon(SettingsDestination.ABOUT),
                        iconTint = destinationTint(SettingsDestination.ABOUT),
                        showChevron = true,
                        onClick = { onNavigate(SettingsDestination.ABOUT) },
                    )
                }
            }
        } else {
            val results = SettingsCatalog.search(query)
            if (results.isEmpty()) {
                item(key = "search_empty") {
                    Text(
                        text = "Aucun réglage pour « $query »",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 32.dp),
                    )
                }
            } else {
                item(key = "search_header") { SettingsSectionHeader("Résultats") }
                item(key = "search_results") {
                    SettingsCard {
                        results.forEachIndexed { index, entry ->
                            if (index > 0) SettingsRowDivider()
                            SettingsRow(
                                title = entry.title,
                                subtitle = entry.description,
                                icon = destinationIcon(entry.destination),
                                iconTint = destinationTint(entry.destination),
                                showChevron = true,
                                onClick = { onNavigate(entry.destination) },
                            )
                        }
                    }
                }
            }
        }

        item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

/** Icône d'un sous-écran (jeu material-icons-core, thème-adaptatif). */
@Composable
private fun destinationIcon(destination: SettingsDestination): ImageVector = when (destination) {
    SettingsDestination.THEME -> Icons.Filled.Star
    SettingsDestination.FEATURES -> Icons.Filled.Build
    SettingsDestination.PLAYER -> Icons.Filled.PlayArrow
    SettingsDestination.UPDATES -> Icons.Filled.Refresh
    SettingsDestination.ABOUT -> Icons.Filled.Info
}

/** Teinte de la puce d'icône — dérivée du thème, jamais figée. */
@Composable
private fun destinationTint(destination: SettingsDestination): Color = when (destination) {
    SettingsDestination.THEME -> MaterialTheme.colorScheme.primary
    SettingsDestination.FEATURES -> MaterialTheme.colorScheme.tertiary
    SettingsDestination.PLAYER -> MaterialTheme.colorScheme.secondary
    SettingsDestination.UPDATES -> MaterialTheme.colorScheme.primary
    SettingsDestination.ABOUT -> MaterialTheme.colorScheme.onSurfaceVariant
}
