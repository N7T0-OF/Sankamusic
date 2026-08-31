package com.maxrave.simpmusic.spacekai.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.spacekai.SPACEKAI_VERSION
import com.maxrave.simpmusic.spacekai.UpstreamCheckState
import com.maxrave.simpmusic.spacekai.computeUpstreamCompatibility
import com.maxrave.simpmusic.spacekai.displayUpstreamVersion
import com.maxrave.simpmusic.spacekai.isSpaceKaiAvailable
import com.maxrave.simpmusic.spacekai.isVersionNewer
import com.maxrave.simpmusic.spacekai.update.SpaceKaiUpdateManager
import com.maxrave.simpmusic.spacekai.update.SpaceKaiUpdatePhase
import com.maxrave.simpmusic.spacekai.update.SpaceKaiUpdateState
import com.maxrave.simpmusic.ui.component.SettingItem
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.SharedViewModel
import kotlinx.coroutines.launch

// SPACEKAI FEATURE: "Mises à jour" section with two distinct blocks, matching the
// "one app — SpaceKai replaces SimpMusic" architecture:
//
//   SpaceKai .......... the INSTALLED app (com.maxrave.simpmusic, SpaceKai signing key).
//                       "Mettre à jour" drives the full download → SHA-256 verify →
//                       package-install pipeline with real progress, and refuses a
//                       corrupted/tampered APK before installing (never a fake install).
//   SimpMusic/Upstream  the base SpaceKai is rebuilt on. Reported INFO-ONLY with a
//                       compatibility status. The upstream APK is NEVER downloaded or
//                       installed over SpaceKai (different signing key would be refused or
//                       replace SpaceKai) — we only show that a new base exists and that a
//                       compatible SpaceKai build isn't out yet.
//
//   TWO DIFFERENT FACTS, never conflated:
//     "Base intégrée"           = the upstream version THIS build was compiled against
//                                 (build-time constant SPACEKAI_BASED_ON_UPSTREAM).
//     "Dernière release officielle" = what GitHub /releases/latest reports TODAY
//                                 (fetched dynamically — never hardcoded).
@Composable
fun SpaceKaiUpdatesSection(
    sharedViewModel: SharedViewModel,
) {
    if (!isSpaceKaiAvailable()) return

    val updateResponse by sharedViewModel.updateResponse.collectAsState()
    val upstreamResponse by sharedViewModel.upstreamResponse.collectAsState()
    val isCheckingUpstream by sharedViewModel.isCheckingUpstream.collectAsState()
    val upstreamCheckError by sharedViewModel.upstreamCheckError.collectAsState()
    val lastUpstreamCheckAt by sharedViewModel.lastUpstreamCheckAt.collectAsState()
    val updateUi by SpaceKaiUpdateManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    // Kick the upstream (SimpMusic) check once when the section appears, so the
    // compatibility line is fresh without requiring the user to tap anything.
    LaunchedEffect(Unit) {
        sharedViewModel.checkForUpstreamRelease()
    }

    val compatibility =
        computeUpstreamCompatibility(
            latestUpstream = upstreamResponse?.tagName,
            updateData = upstreamResponse,
            checkState =
                when {
                    isCheckingUpstream -> UpstreamCheckState.CHECKING
                    upstreamCheckError -> UpstreamCheckState.ERROR
                    else -> UpstreamCheckState.OK
                },
        )

    val installedTag = "v$SPACEKAI_VERSION"
    val latestTag = updateResponse?.tagName
    // Semantic comparison, not string inequality: a re-cut tag like "v0.3.1-1" is
    // the SAME version as "v0.3.1" and must never trigger a false update.
    val isNewer = isVersionNewer(latestTag, installedTag)

    Column {
        Text(
            text = "Mises à jour",
            style = typo().labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        // ---------- SpaceKai block (the installed app) ----------
        // Installée = the REAL build version (VersionManager/BuildKonfig); dernière =
        // the live GitHub answer (N7T0-OF/Sankamusic/releases/latest). Before the first
        // check the tag is null — render an honest "—", never the broken "v—".
        val latestSpaceKai =
            if (updateResponse?.tagName.isNullOrBlank()) "—"
            else "v${displayUpstreamVersion(updateResponse?.tagName)}"
        SettingItem(
            title = "SpaceKai",
            subtitle = "Installée v$SPACEKAI_VERSION · dernière : $latestSpaceKai",
            onClick = {
                sharedViewModel.checkForUpdate()
            },
        )

        // ---------- Update action / progress ----------
        // When a newer release exists (and carries an APK), drive the real download ->
        // SHA-256 -> install pipeline and surface its progress. Before the first check, the
        // same row just triggers the release check.
        val resp: com.maxrave.domain.data.model.update.UpdateData? = updateResponse
        if (resp?.apkUrl != null && isNewer) {
            SpaceKaiUpdateRow(
                state = updateUi,
                onClickStart = {
                    val target = resp ?: return@SpaceKaiUpdateRow
                    SpaceKaiUpdateManager.reset()
                    scope.launch { SpaceKaiUpdateManager.install(target) }
                },
            )
        } else {
            SettingItem(
                title = "Mettre à jour",
                subtitle = "Vérifier s'il existe une nouvelle version SpaceKai",
                onClick = {
                    sharedViewModel.checkForUpdate()
                },
            )
        }

        // ---------- SimpMusic — Upstream block (info-only) ----------
        SettingItem(
            title = "SimpMusic — Upstream",
            subtitle =
                buildString {
                    // "Base intégrée" ≠ "Dernière release officielle". The first is a
                    // build-time constant; the second is the live GitHub answer.
                    append("Base intégrée : v${compatibility.basedOnUpstream}\n")
                    val latest =
                        when {
                            isCheckingUpstream -> "…"
                            upstreamCheckError -> "indisponible"
                            else -> "v${displayUpstreamVersion(upstreamResponse?.tagName)}"
                        }
                    append("Dernière release officielle : $latest\n")
                    append(compatibility.statusLabel)
                    lastUpstreamCheckAt?.let { at ->
                        val ago = (System.currentTimeMillis() - at) / 1000L
                        val label =
                            when {
                                ago < 60L -> "à l'instant"
                                ago < 3600L -> "il y a ${ago / 60L} min"
                                ago < 86400L -> "il y a ${ago / 3600L} h"
                                else -> "il y a ${ago / 86400L} j"
                            }
                        append("\nDernière vérification : $label")
                    }
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

@Composable
private fun SpaceKaiUpdateRow(
    state: SpaceKaiUpdateState,
    onClickStart: () -> Unit,
) {
    if (state.isBusy) {
        // Real byte-level progress straight from the platform downloader.
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            val text =
                when (state.phase) {
                    SpaceKaiUpdatePhase.DOWNLOADING ->
                        "Téléchargement… ${mb(state.downloadedBytes)}" +
                            if (state.totalBytes > 0L) " / ${mb(state.totalBytes)} Mo" else " Mo" +
                            if (state.bytesPerSecond > 0L) " · ${kb(state.bytesPerSecond)}" else ""
                    SpaceKaiUpdatePhase.VERIFYING -> "Vérification SHA-256…"
                    SpaceKaiUpdatePhase.INSTALLING -> "Installation…"
                    else -> ""
                }
            Text(
                text = text,
                style = typo().bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.phase == SpaceKaiUpdatePhase.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { state.progressFraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    } else {
        // Idle or terminal.
        SettingItem(
            title =
                when (state.phase) {
                    SpaceKaiUpdatePhase.SUCCESS -> "✓ Mise à jour installée"
                    SpaceKaiUpdatePhase.FAILED -> "❌ Mise à jour échouée"
                    SpaceKaiUpdatePhase.CANCELLED -> "Mise à jour annulée"
                    else -> "Mettre à jour"
                },
            subtitle =
                when (state.phase) {
                    SpaceKaiUpdatePhase.FAILED ->
                        "Réessayer — ${state.error ?: "erreur inconnue"}"
                    SpaceKaiUpdatePhase.CANCELLED -> "Toucher pour relancer le téléchargement."
                    SpaceKaiUpdatePhase.SUCCESS -> "Télécharger la dernière version SpaceKai."
                    else -> "Télécharger, vérifier la signature puis installer."
                },
            onClick = onClickStart,
        )
    }
}

private fun mb(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return java.math.BigDecimal(mb).setScale(1, java.math.RoundingMode.HALF_UP).toPlainString()
}

private fun kb(bps: Long): String = "${(bps / 1024.0).toInt()} Ko/s"
