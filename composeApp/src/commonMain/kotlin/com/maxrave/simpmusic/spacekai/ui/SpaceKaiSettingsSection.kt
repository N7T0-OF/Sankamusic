package com.maxrave.simpmusic.spacekai.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.simpmusic.spacekai.SPACEKAI_FLAG_PREFIX
import com.maxrave.simpmusic.spacekai.SpaceKaiFeatures
import com.maxrave.simpmusic.spacekai.configSpaceKai
import com.maxrave.simpmusic.spacekai.currentFeatures
import com.maxrave.simpmusic.spacekai.isSpaceKaiAvailable
import com.maxrave.simpmusic.spacekai.mergePersistedSpaceKaiFeatures
import com.maxrave.simpmusic.ui.component.SettingItem
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.SharedViewModel

// SPACEKAI FEATURE
//
// Settings section for the SpaceKai add-on layer. It only renders when the
// layer is active (`isSpaceKaiAvailable()`), so a vanilla SimpMusic build
// (or an upstream merge that dropped `configSpaceKai`) shows nothing.
//
// Persistence: each flag is stored as a generic string key ("spacekai_<flag>")
// through SharedViewModel.getString/putString — the same mechanism as
// hide_nav_label — so the SpaceKai layer needs no typed keys in the core
// submodule. On every toggle the flag is written AND `configSpaceKai` is
// re-issued so `isSpaceKaiFeatureEnabled` reflects the change immediately.

/**
 * The SpaceKai settings section: a header plus one toggle per feature flag.
 *
 * Toggles are persisted in DataStore (generic string keys) and re-issued to
 * `configSpaceKai` on every change, so the flags survive a restart and the
 * app reacts immediately.
 */
@Composable
fun SpaceKaiSettingsSection(
    sharedViewModel: SharedViewModel,
) {
    if (!isSpaceKaiAvailable()) return

    // Load the persisted flags once per composition from the generic string store.
    // A stored "true"/"false" (user's explicit choice) wins over the build-time
    // default; an absent key keeps the default.
    val persisted by
        produceState(initialValue = currentFeatures()) {
            value =
                mergePersistedSpaceKaiFeatures(
                    base = currentFeatures(),
                    getString = { key -> sharedViewModel.getString(key) },
                )
        }
    var features by remember { mutableStateOf(persisted) }

    fun persist(feature: String, enabled: Boolean) {
        sharedViewModel.putString(
            "$SPACEKAI_FLAG_PREFIX$feature",
            if (enabled) DataStoreManager.TRUE else DataStoreManager.FALSE,
        )
        // Re-issue so isSpaceKaiFeatureEnabled reflects the change immediately.
        configSpaceKai(features)
    }

    Column {
        Text(
            text = "SpaceKai",
            style = typo().labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        SettingItem(
            title = "Custom navigation",
            subtitle = "Custom bottom navigation",
            switch =
                (features.customNavigation to {
                    features = features.copy(customNavigation = it)
                    persist("custom_navigation", it)
                }),
        )
        SettingItem(
            title = "Minimalistic navigation",
            subtitle = "Compact navigation variant",
            switch =
                (features.minimalisticNavigation to {
                    features = features.copy(minimalisticNavigation = it)
                    persist("minimalistic_navigation", it)
                }),
        )
        SettingItem(
            title = "Dynamic color",
            subtitle = "SpaceKai colour overrides",
            switch =
                (features.dynamicColor to {
                    features = features.copy(dynamicColor = it)
                    persist("dynamic_color", it)
                }),
        )
        SettingItem(
            title = "Haptics",
            subtitle = "Tactile feedback on interactions",
            switch =
                (features.haptics to {
                    features = features.copy(haptics = it)
                    persist("haptics", it)
                }),
        )
        SettingItem(
            title = "Custom player info",
            subtitle = "Custom player info line",
            switch =
                (features.customPlayerInfo to {
                    features = features.copy(customPlayerInfo = it)
                    persist("custom_player_info", it)
                }),
        )
        SettingItem(
            title = "Landscape player",
            subtitle = "Landscape-aware Now Playing layout",
            switch =
                (features.landscapePlayer to {
                    features = features.copy(landscapePlayer = it)
                    persist("landscape_player", it)
                }),
        )
    }
}
