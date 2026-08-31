package com.maxrave.simpmusic.spacekai

import com.maxrave.simpmusic.ui.component.BottomNavScreen

// SPACEKAI FEATURE: personalized navigation — reorder / hide / re-show the bottom-bar
// tabs, persisted through the generic string store (same mechanism as hide_nav_label
// and the SpaceKai flags, so the layer needs no typed DataStore keys).
//
//   - Order  : "spacekai_nav_order"  = comma-joined tab keys, e.g. "library,home,search"
//   - Hidden : "spacekai_nav_hidden" = comma-joined hidden tab keys, e.g. "analytics,mix"
//
// Applied only when the customNavigation feature flag is ON (App.kt resolves the final
// tab list and hands it to every nav bar via `navTabs`); when OFF the bars behave exactly
// as before.

const val SPACEKAI_NAV_ORDER_KEY: String = "spacekai_nav_order"
const val SPACEKAI_NAV_HIDDEN_KEY: String = "spacekai_nav_hidden"

/** Serialize an ordered tab list to its persisted form ("home,library,search"). */
fun serializeNavOrder(tabs: List<BottomNavScreen>): String = tabs.joinToString(",") { it.key }

/** Parse the persisted order; blank/missing => empty list = keep the default order. */
fun parseNavOrder(raw: String?): List<String> =
    raw
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

/** Parse the persisted hidden set. */
fun parseNavHidden(raw: String?): Set<String> =
    raw
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        ?: emptySet()

/**
 * Resolve the final tab list for a nav bar.
 *
 * [defaultTabs] is the bar's own list after its built-in conditioning (optional
 * Analytics/Mix-for-you tabs present only when enabled and not minimalistic). The user's
 * saved [userOrder] is applied as a RELATIVE order — tabs the user never ordered keep
 * their default positions — and [hidden] removes tabs the user switched off.
 *
 * `ordinal` stays the tab identity, so navigation/selection logic is untouched; only the
 * rendered order/visibility changes. Search rides its own FAB in every style, so hiding it
 * only removes it from the bar, exactly like the conditional tabs.
 */
fun resolveNavTabs(
    userOrder: List<String>,
    hidden: Set<String>,
    defaultTabs: List<BottomNavScreen>,
): List<BottomNavScreen> {
    if (userOrder.isEmpty() && hidden.isEmpty()) return defaultTabs
    val byKey = defaultTabs.associateBy { it.key }
    val ordered = userOrder.mapNotNull { byKey[it] }
    val rest = defaultTabs.filterNot { it.key in userOrder }
    return (ordered + rest).filterNot { it.key in hidden }
}
