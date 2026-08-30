package com.maxrave.simpmusic.spacekai.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.spacekai.SPACEKAI_VERSION
import com.maxrave.simpmusic.spacekai.computeUpstreamCompatibility
import com.maxrave.simpmusic.spacekai.displayUpstreamVersion
import com.maxrave.simpmusic.spacekai.isSpaceKaiAvailable
import com.maxrave.simpmusic.ui.component.SettingItem
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.utils.VersionManager
import com.maxrave.simpmusic.viewModel.SharedViewModel

// SPACEKAI FEATURE: "Mises à jour" section with two distinct blocks, matching the
// "one app — SpaceKai replaces SimpMusic" architecture:
//
//   SpaceKai .......... the INSTALLED app (com.maxrave.simpmusic, SpaceKai signing key).
//                       "Mettre à jour" downloads the newest SPACEKAI release and installs
//                       it in place through the Android package installer.
//   SimpMusic/Upstream  the base SpaceKai is rebuilt on. Reported INFO-ONLY with a
//                       compatibility status. The upstream APK is NEVER downloaded or
//                       installed over SpaceKai (different signing key would be refused or
//                       replace SpaceKai) — we only show that a new base exists and that a
//                       compatible SpaceKai build isn't out yet.
@Composable
fun SpaceKaiUpdatesSection(
    sharedViewModel: SharedViewModel,
) {
    if (!isSpaceKaiAvailable()) return

    val updateResponse by sharedViewModel.updateResponse.collectAsState()
    val upstreamResponse by sharedViewModel.upstreamResponse.collectAsState()
    val isCheckingUpstream by sharedViewModel.isCheckingUpstream.collectAsState()

    // Kick the upstream (SimpMusic) check once when the section appears, so the
    // compatibility line is fresh without requiring the user to tap anything.
    LaunchedEffect(Unit) {
        sharedViewModel.checkForUpstreamRelease()
    }

    val compatibility =
        computeUpstreamCompatibility(
            latestUpstream = upstreamResponse?.tagName,
            updateData = upstreamResponse,
        )

    Column {
        Text(
            text = "Mises à jour",
            style = typo().labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        // ---------- SpaceKai block (the installed app) ----------
        SettingItem(
            title = "SpaceKai",
            subtitle = "Installée v$SPACEKAI_VERSION · dernière : v${displayUpstreamVersion(updateResponse?.tagName)}",
            onClick = {
                // Re-check; the update dialog that opens downloads and installs the
                // newest SPACEKAI release in place, entirely in-app.
                sharedViewModel.checkForUpdate()
            },
        )
        SettingItem(
            title = "SimpMusic — Upstream",
            subtitle =
                buildString {
                    append("Base utilisée : v${compatibility.basedOnUpstream}\n")
                    val latest =
                        if (isCheckingUpstream) {
                            "…"
                        } else {
                            "v${displayUpstreamVersion(upstreamResponse?.tagName)}"
                        }
                    append("Dernière base disponible : $latest\n")
                    append(compatibility.statusLabel)
                },
            onClick = {
                sharedViewModel.checkForUpstreamRelease()
            },
        )
        SettingItem(
            title = "Vérifier les mises à jour",
            subtitle = "Rechercher la dernière version SpaceKai",
            onClick = {
                sharedViewModel.checkForUpdate()
            },
        )
    }
}