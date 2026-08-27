package com.sankamusic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Activité minimale — squelette de build (Phase 2/3).
 *
 * ⚠️ Ce n'est PAS l'architecture cible (voir docs/ARCHITECTURE.md) : la vraie
 * navigation (sections Home, Settings, Player) sera construite avec le Core UI.
 * L'accès à l'écran « Mises à jour » est volontairement simple (état local).
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
                        StartupScreen(onOpenUpdates = { showUpdates = true })
                    }
                }
            }
        }
    }
}

@Composable
private fun StartupScreen(onOpenUpdates: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Sankamusic ${BuildConfig.SANKAMUSIC_VERSION}",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Base ${BuildConfig.SANKAMUSIC_UPSTREAM_BASE} ${BuildConfig.SANKAMUSIC_UPSTREAM_VERSION}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenUpdates) { Text("Mises à jour") }
    }
}
