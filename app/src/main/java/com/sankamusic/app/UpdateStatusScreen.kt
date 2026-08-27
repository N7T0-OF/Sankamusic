package com.sankamusic.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sankamusic.core.api.PluginUpdate
import com.sankamusic.core.api.UpdateManager
import com.sankamusic.core.api.UpdateState
import com.sankamusic.core.api.UpdateStatus
import com.sankamusic.core.api.UpstreamCompatibilityState
import com.sankamusic.core.api.UpstreamStatus

/**
 * Écran « Mises à jour » (docs/UPDATE_SYSTEM.md § 4) — trois catégories
 * distinctes (Core Sankamusic, base upstream, plugins), comme exigé par ADR-004.
 *
 * Aucune vérification ne fait planter l'écran : une source injoignable ou une
 * absence de release produit un message propre (état ERROR).
 *
 * ⚠️ Code Compose : non compilé dans l'environnement de travail (SDK Android
 * requis). La logique appelée (UpdateEngine) est, elle, testée unitairement.
 */
@Composable
fun UpdateStatusScreen(updateManager: UpdateManager, onBack: () -> Unit) {
    var sankamusic by remember { mutableStateOf<UpdateStatus?>(null) }
    var upstream by remember { mutableStateOf<UpstreamStatus?>(null) }
    var pluginUpdates by remember { mutableStateOf<List<PluginUpdate>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(updateManager) {
        loading = true
        sankamusic = updateManager.checkSankamusicUpdate()
        upstream = updateManager.checkUpstreamCompatibility()
        pluginUpdates = updateManager.checkPluginUpdates()
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mises à jour", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onBack) { Text("Retour") }
        }

        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                Text("Vérification en cours…")
            }
        }

        sankamusic?.let { SankamusicStatusCard(it) }
        upstream?.let { UpstreamStatusCard(it) }
        PluginUpdatesCard(pluginUpdates)
    }
}

@Composable
private fun SankamusicStatusCard(status: UpdateStatus) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Sankamusic", style = MaterialTheme.typography.titleMedium)
            Text("Version installée : ${status.installedVersion}")
            when (status.state) {
                UpdateState.UP_TO_DATE -> Text("✓ Vous utilisez la dernière version")
                UpdateState.UPDATE_AVAILABLE -> {
                    Text("Mise à jour disponible : ${status.availableVersion ?: "?"}")
                    status.changelog?.let {
                        Text("Nouveautés : ${it.take(200)}", style = MaterialTheme.typography.bodySmall)
                    }
                    status.downloadSizeBytes?.let { Text("Taille : ${it / 1_000_000} Mo") }
                }
                UpdateState.INCOMPATIBLE -> Text("Version disponible non compatible")
                UpdateState.ERROR -> Text(
                    "Impossible de vérifier (source injoignable ou aucune release publiée)",
                )
            }
        }
    }
}

@Composable
private fun UpstreamStatusCard(status: UpstreamStatus) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Base ${status.upstreamName}", style = MaterialTheme.typography.titleMedium)
            Text("Base intégrée : ${status.installedUpstreamVersion}")
            status.availableUpstreamVersion?.let { Text("Disponible : $it") }
            when (status.state) {
                UpstreamCompatibilityState.COMPATIBLE -> Text("✓ Base compatible")
                UpstreamCompatibilityState.NEEDS_ADAPTER_UPDATE ->
                    Text("Nouvelle version upstream détectée — l'Adapter doit être vérifié/mis à jour avant toute nouvelle release Sankamusic")
                UpstreamCompatibilityState.INCOMPATIBLE ->
                    Text("Compatibilité non vérifiable ou base non intégrée — aucune mise à jour upstream ne sera proposée")
            }
        }
    }
}

@Composable
private fun PluginUpdatesCard(updates: List<PluginUpdate>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Plugins / thèmes", style = MaterialTheme.typography.titleMedium)
            if (updates.isEmpty()) {
                Text("Aucune mise à jour de plugin disponible")
            } else {
                updates.forEach {
                    Text("${it.pluginId} : ${it.fromVersion} → ${it.toVersion}")
                }
            }
        }
    }
}
