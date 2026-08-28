package com.sankamusic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sankamusic.core.api.NavigationTab
import com.sankamusic.core.api.SpaceKaiApi
import com.sankamusic.core.update.SimpMusicAdapter

/**
 * Activité principale — squelette de navigation (Phase 3, étape 1 de la
 * migration SpaceKai — docs/MIGRATION.md).
 *
 * La barre de navigation inférieure est pilotée par les onglets déclarés par
 * les plugins actifs ([UiExtensionRegistry.navigationTabs]) : ordre = priorité,
 * icône résolue par [iconName]. Les onglets par défaut (Accueil, Bibliothèque,
 * Recherche, Paramètres) sont fournis par l'app ; un plugin peut en ajouter.
 *
 * ⚠️ Ce n'est PAS l'architecture cible finale (voir docs/ARCHITECTURE.md) :
 * les écrans Home / Bibliothèque / Recherche / Paramètres seront construits
 * progressivement (migration SpaceKai). L'accès à l'écran « Mises à jour »
 * reste volontairement simple (état local).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showUpdates by rememberSaveable { mutableStateOf(false) }
                    if (showUpdates) {
                        UpdateStatusScreen(
                            updateManager = (application as SankamusicApp).updateEngine,
                            onBack = { showUpdates = false },
                        )
                    } else {
                        MainScreen(onOpenUpdates = { showUpdates = true })
                    }
                }
            }
        }
    }
}

/** Onglet par défaut fourni par l'app (id stable, icône résolue par l'UI). */
private val defaultTabs = listOf(
    NavigationTab(id = "home", label = "Accueil", priority = 0, iconName = "home"),
    NavigationTab(id = "library", label = "Bibliothèque", priority = 10, iconName = "library"),
    NavigationTab(id = "search", label = "Recherche", priority = 20, iconName = "search"),
    NavigationTab(id = "settings", label = "Paramètres", priority = 30, iconName = "settings"),
)

@Composable
private fun MainScreen(onOpenUpdates: () -> Unit) {
    // Onglets par défaut + ceux déclarés par les plugins actifs (triés par priorité).
    val pluginTabs = if (SpaceKaiApi.isInitialized()) {
        SpaceKaiApi.instance.uiExtensions.navigationTabs()
    } else {
        emptyList()
    }
    val tabs = (defaultTabs + pluginTabs).sortedBy { it.priority }

    var selectedId by rememberSaveable { mutableStateOf(tabs.firstOrNull()?.id ?: "home") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Contenu de l'onglet sélectionné.
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (selectedId) {
                "settings" -> SettingsScreen(onOpenUpdates = onOpenUpdates)
                "home" -> HomeScreen(onOpenUpdates = onOpenUpdates)
                else -> PlaceholderTab(tabs.firstOrNull { it.id == selectedId })
            }
        }
        // Barre de navigation inférieure.
        NavigationBar {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = tab.id == selectedId,
                    onClick = { selectedId = tab.id },
                    icon = {
                        Icon(
                            imageVector = iconFor(tab.iconName),
                            contentDescription = tab.label,
                        )
                    },
                    label = { Text(tab.label) },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(onOpenUpdates: () -> Unit) {
    Text(
        text = "Sankamusic ${BuildConfig.SANKAMUSIC_VERSION}",
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(
        text = "Base ${BuildConfig.SANKAMUSIC_UPSTREAM_BASE} ${SimpMusicAdapter().info.version}",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onOpenUpdates) { Text("Mises à jour") }
}

@Composable
private fun SettingsScreen(onOpenUpdates: () -> Unit) {
    Text(
        text = "Paramètres",
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onOpenUpdates) { Text("Mises à jour") }
}

@Composable
private fun PlaceholderTab(tab: NavigationTab?) {
    Text(
        text = tab?.label ?: "Onglet inconnu",
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = "Contenu à construire (migration SpaceKai).",
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Résout le nom d'icône d'un onglet vers une [ImageVector] Material. */
private fun iconFor(name: String): ImageVector = when (name) {
    "home" -> Icons.Filled.Home
    "library" -> Icons.AutoMirrored.Filled.List
    "search" -> Icons.Filled.Search
    "settings" -> Icons.Filled.Settings
    else -> Icons.Filled.Home
}