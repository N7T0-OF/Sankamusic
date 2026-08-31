package com.maxrave.simpmusic.spacekai.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.spacekai.features.haptics.HapticIntensity
import com.maxrave.simpmusic.spacekai.features.haptics.HapticManager
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.SharedViewModel

// SPACEKAI FEATURE: haptics intensity selector. Shown inside the SpaceKai settings section
// when the haptics flag is ON. Faible / Moyenne / Forte — persisted through the generic
// string store (HapticManager.KEY) and restored at startup by applyPersistedSpaceKaiFeatures.
// Choosing a chip fires a haptic with the new intensity, so the strength is felt immediately.
@Composable
fun SpaceKaiHapticIntensityRow(
    sharedViewModel: SharedViewModel,
) {
    val persisted by
        produceState(initialValue = HapticIntensity.MEDIUM) {
            value = HapticManager.applyPersisted { key -> sharedViewModel.getString(key) }
        }
    var selected by remember { mutableStateOf(persisted) }
    val hapticFeedback = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = "Intensité des vibrations",
            style = typo().labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Text(
            text = "Faible · Moyenne · Forte — appliqué aux interactions.",
            style = typo().bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            HapticIntensity.entries.forEach { intensity ->
                ElevatedFilterChip(
                    selected = selected == intensity,
                    onClick = {
                        selected = intensity
                        HapticManager.setIntensity(intensity)
                        sharedViewModel.putString(HapticManager.KEY, intensity.storage)
                        // Feel the newly selected strength immediately.
                        HapticManager.onClick(hapticFeedback)
                    },
                    label = { Text(intensity.label) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}
