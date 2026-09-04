package com.maxrave.simpmusic.spacekai

import com.maxrave.simpmusic.ui.component.BottomNavScreen
import com.maxrave.simpmusic.ui.navigation.destination.home.AnalyticsDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.HomeDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.MixForYouDestination
import com.maxrave.simpmusic.ui.navigation.destination.search.SearchDestination

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
 * Build the default tab list shared by every navigation presentation.
 *
 * Analytics follows local tracking; Mix-for-you follows the signed-in session.
 * There is deliberately NO style parameter: the minimalistic style is
 * presentation-only (icons, compact size) and never removes a destination, so
 * Mix stays whenever it is enabled, exactly like the other styles. The only
 * way Mix disappears is the user hiding it explicitly through the
 * personalized-navigation editor (spacekai_nav_hidden), or the YouTube
 * session ending.
 */
fun defaultNavTabs(
    showAnalyticsTab: Boolean,
    showMixForYouTab: Boolean,
): List<BottomNavScreen> =
    listOfNotNull(
        BottomNavScreen.Home,
        BottomNavScreen.MixForYou.takeIf { showMixForYouTab },
        BottomNavScreen.Analytics.takeIf { showAnalyticsTab },
        BottomNavScreen.Library,
        BottomNavScreen.Search,
    )

/**
 * Return the canonical tab identities used by the personalization editor and route fallback.
 * Search is included even when the current settings would make it unavailable.
 */
fun allNavTabs(): List<BottomNavScreen> =
    listOf(
        BottomNavScreen.Home,
        BottomNavScreen.Search,
        BottomNavScreen.Library,
        BottomNavScreen.Analytics,
        BottomNavScreen.MixForYou,
    )

/**
 * Normalize a visible tab list into something every renderer can lay out.
 *
 * Home is the fallback when all tabs are hidden. A Search-only list also gets Home because
 * Search is rendered in a separate control and the capsule/rail still needs one tab.
 */
fun ensureUsableNavTabs(tabs: List<BottomNavScreen>): List<BottomNavScreen> {
    val distinctTabs = tabs.distinctBy { it.key }
    val nonEmptyTabs = distinctTabs.ifEmpty { listOf(BottomNavScreen.Home) }
    // A Search-only list still needs a capsule/rail tab, because Search is rendered in its own
    // control; Home is the product fallback (see resolveNavSelection).
    return if (nonEmptyTabs.all { it == BottomNavScreen.Search }) {
        listOf(BottomNavScreen.Home) + nonEmptyTabs
    } else {
        nonEmptyTabs
    }
}

/**
 * Resolve the final tab list for a nav bar.
 *
 * [defaultTabs] is the bar's list after its built-in conditioning: Analytics is present only
 * when local tracking is enabled, and Mix-for-you follows the signed-in session (the
 * minimalistic style is presentation-only and never removes it). The saved order is
 * relative, unknown/duplicate keys are ignored, and [hidden] removes tabs. The result is
 * normalized so an empty or Search-only personalization remains usable.
 */
fun resolveNavTabs(
    userOrder: List<String>,
    hidden: Set<String>,
    defaultTabs: List<BottomNavScreen>,
): List<BottomNavScreen> {
    val byKey = defaultTabs.associateBy { it.key }
    val orderedKeys =
        userOrder
            .filter { it in byKey }
            .distinct()
    val ordered = orderedKeys.map { byKey.getValue(it) }
    val rest = defaultTabs.filterNot { it.key in orderedKeys }
    return ensureUsableNavTabs((ordered + rest).filterNot { it.key in hidden })
}

/** Resolve a possibly hidden selection to a visible tab, preferring Home when available. */
fun resolveNavSelection(
    selectedTab: BottomNavScreen?,
    visibleTabs: List<BottomNavScreen>,
): BottomNavScreen {
    val usable = ensureUsableNavTabs(visibleTabs)
    return usable.firstOrNull { it == selectedTab }
        ?: usable.firstOrNull { it == BottomNavScreen.Home }
        ?: usable.first()
}

/** Resolve a possibly hidden ordinal selection to the ordinal of a visible tab. */
fun resolveNavSelectionIndex(
    selectedOrdinal: Int,
    visibleTabs: List<BottomNavScreen>,
): Int {
    val usable = ensureUsableNavTabs(visibleTabs)
    return resolveNavSelection(usable.firstOrNull { it.ordinal == selectedOrdinal }, usable).ordinal
}

/** Map a renderer's start destination to the shared tab-selection contract. */
fun resolveInitialNavSelection(
    startDestination: Any,
    visibleTabs: List<BottomNavScreen>,
): BottomNavScreen {
    val requested =
        when (startDestination) {
            is HomeDestination -> BottomNavScreen.Home
            is SearchDestination -> BottomNavScreen.Search
            is LibraryDestination -> BottomNavScreen.Library
            is AnalyticsDestination -> BottomNavScreen.Analytics
            is MixForYouDestination -> BottomNavScreen.MixForYou
            else -> null
        }
    return resolveNavSelection(requested, visibleTabs)
}
