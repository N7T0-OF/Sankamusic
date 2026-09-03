package com.maxrave.simpmusic.spacekai

import com.maxrave.simpmusic.ui.component.BottomNavScreen
import com.maxrave.simpmusic.ui.navigation.destination.home.AnalyticsDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.MixForYouDestination
import kotlin.test.Test
import kotlin.test.assertEquals

class NavCustomizationTest {
    @Test
    fun `compact navigation keeps analytics when local tracking is enabled`() {
        val tabs =
            defaultNavTabs(
                showAnalyticsTab = true,
                showMixForYouTab = true,
                minimalistic = true,
            )

        assertEquals(
            listOf("home", "analytics", "library", "search"),
            tabs.map { it.key },
        )
    }

    @Test
    fun `analytics follows local tracking independently of compact mode`() {
        val tabs =
            defaultNavTabs(
                showAnalyticsTab = false,
                showMixForYouTab = true,
                minimalistic = true,
            )

        assertEquals(
            listOf("home", "library", "search"),
            tabs.map { it.key },
        )
    }

    @Test
    fun `personalized navigation preserves compact defaults before applying hidden tabs`() {
        val defaultTabs =
            defaultNavTabs(
                showAnalyticsTab = true,
                showMixForYouTab = true,
                minimalistic = true,
            )

        val tabs =
            resolveNavTabs(
                userOrder = listOf(BottomNavScreen.Library.key, BottomNavScreen.Analytics.key),
                hidden = setOf(BottomNavScreen.Search.key),
                defaultTabs = defaultTabs,
            )

        assertEquals(
            listOf("library", "analytics", "home"),
            tabs.map { it.key },
        )
    }

    @Test
    fun `hiding the selected tab resolves the highlight to home`() {
        val resolved =
            resolveNavTabs(
                userOrder = emptyList(),
                hidden = setOf(BottomNavScreen.Analytics.key),
                defaultTabs = defaultNavTabs(true, true, false),
            )

        assertEquals(
            listOf("home", "mix", "library", "search"),
            resolved.map { it.key },
        )
        assertEquals(BottomNavScreen.Home, resolveNavSelection(BottomNavScreen.Analytics, resolved))
        assertEquals(BottomNavScreen.Home.ordinal, resolveNavSelectionIndex(BottomNavScreen.Analytics.ordinal, resolved))
        assertEquals(BottomNavScreen.Library.ordinal, resolveNavSelectionIndex(BottomNavScreen.Library.ordinal, resolved))
    }

    @Test
    fun `hiding search removes it from every bar list`() {
        val resolved =
            resolveNavTabs(
                userOrder = emptyList(),
                hidden = setOf(BottomNavScreen.Search.key),
                defaultTabs = defaultNavTabs(true, true, false),
            )

        assertEquals(
            listOf("home", "mix", "analytics", "library"),
            resolved.map { it.key },
        )
        assertEquals(false, resolved.any { it == BottomNavScreen.Search })
    }

    @Test
    fun `hiding every tab still yields a usable home tab`() {
        val defaultTabs = defaultNavTabs(true, true, false)
        val resolved =
            resolveNavTabs(
                userOrder = emptyList(),
                hidden = defaultTabs.map { it.key }.toSet(),
                defaultTabs = defaultTabs,
            )

        assertEquals(listOf("home"), resolved.map { it.key })
        assertEquals(BottomNavScreen.Home, resolveNavSelection(BottomNavScreen.Library, resolved))
    }

    @Test
    fun `search-only personalization keeps a capsule tab`() {
        val resolved =
            resolveNavTabs(
                userOrder = emptyList(),
                hidden = setOf("home", "mix", "analytics", "library"),
                defaultTabs = defaultNavTabs(true, true, false),
            )

        assertEquals(listOf("home", "search"), resolved.map { it.key })
    }

    @Test
    fun `selection falls back to the first visible tab when home is hidden`() {
        val resolved =
            resolveNavTabs(
                userOrder = emptyList(),
                hidden = setOf("home", "analytics", "mix"),
                defaultTabs = defaultNavTabs(true, true, false),
            )

        assertEquals(listOf("library", "search"), resolved.map { it.key })
        assertEquals(BottomNavScreen.Library, resolveNavSelection(BottomNavScreen.Analytics, resolved))
    }

    @Test
    fun `initial selection resolves through the visible list`() {
        val resolved =
            resolveNavTabs(
                userOrder = emptyList(),
                hidden = setOf("analytics", "mix"),
                defaultTabs = defaultNavTabs(true, true, false),
            )

        assertEquals(BottomNavScreen.Home, resolveInitialNavSelection(AnalyticsDestination, resolved))
        assertEquals(BottomNavScreen.Home, resolveInitialNavSelection(MixForYouDestination, resolved))
        assertEquals(BottomNavScreen.Home, resolveInitialNavSelection(Unit, resolved))
        // Home is preferred when available, even when another visible tab exists.
        assertEquals(BottomNavScreen.Home, resolveInitialNavSelection(MixForYouDestination, listOf(BottomNavScreen.Library, BottomNavScreen.Home)))
    }

    @Test
    fun `unknown and duplicate order keys are ignored`() {
        val resolved =
            resolveNavTabs(
                userOrder = listOf("home", "bogus", "library", "home"),
                hidden = emptySet(),
                defaultTabs = defaultNavTabs(true, true, false),
            )

        assertEquals(
            listOf("home", "library", "mix", "analytics", "search"),
            resolved.map { it.key },
        )
    }

    @Test
    fun `ensure usable tabs dedupes and never returns an empty or search-only list`() {
        assertEquals(listOf("home"), ensureUsableNavTabs(emptyList()).map { it.key })
        assertEquals(
            listOf("home", "search"),
            ensureUsableNavTabs(listOf(BottomNavScreen.Search)).map { it.key },
        )
        assertEquals(
            listOf("home", "library"),
            ensureUsableNavTabs(
                listOf(BottomNavScreen.Home, BottomNavScreen.Home, BottomNavScreen.Library),
            ).map { it.key },
        )
    }
}
