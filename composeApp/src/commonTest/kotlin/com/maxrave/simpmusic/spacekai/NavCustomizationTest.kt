package com.maxrave.simpmusic.spacekai

import com.maxrave.simpmusic.ui.component.BottomNavScreen
import com.maxrave.simpmusic.ui.navigation.destination.home.AnalyticsDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.MixForYouDestination
import kotlin.test.Test
import kotlin.test.assertEquals

class NavCustomizationTest {
    // P0 regression (v0.3.6): the minimalistic style is presentation-only and must
    // NEVER drop Mix-for-you when it is enabled — Mix follows the signed-in session
    // like every other style. defaultNavTabs deliberately takes no style parameter.
    @Test
    fun `minimalist keeps mix when mix is enabled`() {
        val tabs =
            defaultNavTabs(
                showAnalyticsTab = false,
                showMixForYouTab = true,
            )

        assertEquals(
            listOf("home", "mix", "library", "search"),
            tabs.map { it.key },
        )
        assertEquals(true, tabs.any { it == BottomNavScreen.MixForYou })
    }

    @Test
    fun `minimalist never removes any enabled destination`() {
        val tabs =
            defaultNavTabs(
                showAnalyticsTab = true,
                showMixForYouTab = true,
            )

        assertEquals(
            listOf("home", "mix", "analytics", "library", "search"),
            tabs.map { it.key },
        )
    }

    @Test
    fun `default tab list is style-independent`() {
        assertEquals(
            defaultNavTabs(showAnalyticsTab = true, showMixForYouTab = true),
            defaultNavTabs(showAnalyticsTab = true, showMixForYouTab = true),
        )
    }

    @Test
    fun `explicitly hiding mix removes it from every bar list`() {
        val resolved =
            resolveNavTabs(
                userOrder = emptyList(),
                hidden = setOf(BottomNavScreen.MixForYou.key),
                defaultTabs = defaultNavTabs(true, true),
            )

        assertEquals(
            listOf("home", "analytics", "library", "search"),
            resolved.map { it.key },
        )
        assertEquals(false, resolved.any { it == BottomNavScreen.MixForYou })
    }

    @Test
    fun `mix session end removes it regardless of style`() {
        val tabs =
            defaultNavTabs(
                showAnalyticsTab = true,
                showMixForYouTab = false,
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
            )

        assertEquals(
            listOf("home", "mix", "library", "search"),
            tabs.map { it.key },
        )
    }

    @Test
    fun `selection resolves to mix when mix is selected`() {
        val tabs = defaultNavTabs(showAnalyticsTab = true, showMixForYouTab = true)

        assertEquals(BottomNavScreen.MixForYou, resolveNavSelection(BottomNavScreen.MixForYou, tabs))
        assertEquals(
            BottomNavScreen.MixForYou.ordinal,
            resolveNavSelectionIndex(BottomNavScreen.MixForYou.ordinal, tabs),
        )
    }

    @Test
    fun `personalized navigation preserves defaults before applying hidden tabs`() {
        val defaultTabs = defaultNavTabs(showAnalyticsTab = true, showMixForYouTab = true)

        val tabs =
            resolveNavTabs(
                userOrder = listOf(BottomNavScreen.Library.key, BottomNavScreen.Analytics.key),
                hidden = setOf(BottomNavScreen.Search.key),
                defaultTabs = defaultTabs,
            )

        assertEquals(
            listOf("library", "analytics", "home", "mix"),
            tabs.map { it.key },
        )
    }

    @Test
    fun `hiding the selected tab resolves the highlight to home`() {
        val resolved =
            resolveNavTabs(
                userOrder = emptyList(),
                hidden = setOf(BottomNavScreen.Analytics.key),
                defaultTabs = defaultNavTabs(true, true),
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
                defaultTabs = defaultNavTabs(true, true),
            )

        assertEquals(
            listOf("home", "mix", "analytics", "library"),
            resolved.map { it.key },
        )
        assertEquals(false, resolved.any { it == BottomNavScreen.Search })
    }

    @Test
    fun `hiding every tab still yields a usable home tab`() {
        val defaultTabs = defaultNavTabs(true, true)
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
                defaultTabs = defaultNavTabs(true, true),
            )

        assertEquals(listOf("home", "search"), resolved.map { it.key })
    }

    @Test
    fun `selection falls back to the first visible tab when home is hidden`() {
        val resolved =
            resolveNavTabs(
                userOrder = emptyList(),
                hidden = setOf("home", "analytics", "mix"),
                defaultTabs = defaultNavTabs(true, true),
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
                defaultTabs = defaultNavTabs(true, true),
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
                defaultTabs = defaultNavTabs(true, true),
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