package com.maxrave.simpmusic.spacekai.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.spacekai.SPACEKAI_NAV_HIDDEN_KEY
import com.maxrave.simpmusic.spacekai.SPACEKAI_NAV_ORDER_KEY
import com.maxrave.simpmusic.spacekai.allNavTabs
import com.maxrave.simpmusic.spacekai.parseNavHidden
import com.maxrave.simpmusic.spacekai.parseNavOrder
import com.maxrave.simpmusic.ui.component.BottomNavScreen
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.SharedViewModel
import org.jetbrains.compose.resources.stringResource

// SPACEKAI FEATURE: personalized navigation editor. Shown inside the SpaceKai settings
// section when the customNavigation flag is ON. Lets the user hide/re-show each tab and
// move it up/down; every change is persisted through the generic string store
// (SPACEKAI_NAV_ORDER_KEY / SPACEKAI_NAV_HIDDEN_KEY) and takes effect immediately on the
// bars, because App.kt resolves the same keys into `navTabs` for every nav style.
@Composable
fun SpaceKaiNavCustomizer(
    sharedViewModel: SharedViewModel,
) {
    // Canonical tab identities = declaration order; used only until the user reorders.
    val allTabs = allNavTabs()
    val defaultOrder = allTabs.map { it.key }

    val persisted by
        produceState(initialValue = emptyList<String>() to emptySet<String>()) {
            value =
                parseNavOrder(sharedViewModel.getString(SPACEKAI_NAV_ORDER_KEY)) to
                    parseNavHidden(sharedViewModel.getString(SPACEKAI_NAV_HIDDEN_KEY))
        }
    var order by remember { mutableStateOf(persisted.first.ifEmpty { defaultOrder }) }
    var hidden by remember { mutableStateOf(persisted.second) }

    fun persist(newOrder: List<String>, newHidden: Set<String>) {
        order = newOrder
        hidden = newHidden
        sharedViewModel.putString(SPACEKAI_NAV_ORDER_KEY, newOrder.joinToString(","))
        sharedViewModel.putString(SPACEKAI_NAV_HIDDEN_KEY, newHidden.joinToString(","))
    }

    fun move(tabKey: String, delta: Int) {
        val idx = order.indexOf(tabKey)
        if (idx < 0) return
        val target = idx + delta
        if (target < 0 || target >= order.size) return
        val next = order.toMutableList().apply {
            removeAt(idx)
            add(target, tabKey)
        }
        persist(next, hidden)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = "Navigation personnalisée",
            style = typo().labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Text(
            text = "Masquer un onglet ou le déplacer — la barre change immédiatement.",
            style = typo().bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        allTabs.forEach { tab ->
            val idx = order.indexOf(tab.key)
            val visible = tab.key !in hidden
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                Text(
                    text = stringResource(tab.title),
                    style = typo().bodyMedium,
                    color =
                        if (visible) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { move(tab.key, -1) },
                    enabled = visible && idx > 0,
                    modifier = Modifier.padding(horizontal = 0.dp),
                ) {
                    Text("↑", textAlign = TextAlign.Center)
                }
                TextButton(
                    onClick = { move(tab.key, 1) },
                    enabled = visible && idx in 0 until order.size - 1,
                    modifier = Modifier.padding(horizontal = 0.dp),
                ) {
                    Text("↓", textAlign = TextAlign.Center)
                }
                Switch(
                    checked = visible,
                    onCheckedChange = { show ->
                        persist(
                            order,
                            if (show) hidden - tab.key else hidden + tab.key,
                        )
                    },
                )
            }
        }
    }
}
