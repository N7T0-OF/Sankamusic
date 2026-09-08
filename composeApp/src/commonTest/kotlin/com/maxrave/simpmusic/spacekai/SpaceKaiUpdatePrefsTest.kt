package com.maxrave.simpmusic.spacekai

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SPACEKAI FEATURE: tests for the updater preferences — auto-check default on,
 * stable/beta channel parsing (tolerant, stable fallback), persistence
 * round-trip through a fake string store.
 */
class SpaceKaiUpdatePrefsTest {

    private class FakeStore {
        val map = mutableMapOf<String, String>()

        val getString: (String) -> String? = { map[it] }
        val putString: (String, String) -> Unit = { k, v -> map[k] = v }
    }

    @Test
    fun `defaults are auto-check on and stable channel`() {
        val store = FakeStore()
        assertEquals(true, SpaceKaiUpdatePrefs.autoCheckEnabled(store.getString))
        assertEquals(SpaceKaiUpdateChannel.STABLE, SpaceKaiUpdatePrefs.channel(store.getString))
    }

    @Test
    fun `auto-check persists and reads back`() {
        val store = FakeStore()
        SpaceKaiUpdatePrefs.setAutoCheck(store.putString, false)
        assertEquals(false, SpaceKaiUpdatePrefs.autoCheckEnabled(store.getString))
        SpaceKaiUpdatePrefs.setAutoCheck(store.putString, true)
        assertEquals(true, SpaceKaiUpdatePrefs.autoCheckEnabled(store.getString))
    }

    @Test
    fun `channel persists and reads back`() {
        val store = FakeStore()
        SpaceKaiUpdatePrefs.setChannel(store.putString, SpaceKaiUpdateChannel.BETA)
        assertEquals(SpaceKaiUpdateChannel.BETA, SpaceKaiUpdatePrefs.channel(store.getString))
        SpaceKaiUpdatePrefs.setChannel(store.putString, SpaceKaiUpdateChannel.STABLE)
        assertEquals(SpaceKaiUpdateChannel.STABLE, SpaceKaiUpdatePrefs.channel(store.getString))
    }

    @Test
    fun `channel parsing is tolerant and falls back to stable`() {
        assertEquals(SpaceKaiUpdateChannel.STABLE, SpaceKaiUpdateChannel.fromKey(null))
        assertEquals(SpaceKaiUpdateChannel.STABLE, SpaceKaiUpdateChannel.fromKey(""))
        assertEquals(SpaceKaiUpdateChannel.STABLE, SpaceKaiUpdateChannel.fromKey("  "))
        assertEquals(SpaceKaiUpdateChannel.STABLE, SpaceKaiUpdateChannel.fromKey("weekly"))
        assertEquals(SpaceKaiUpdateChannel.BETA, SpaceKaiUpdateChannel.fromKey("BETA"))
        assertEquals(SpaceKaiUpdateChannel.BETA, SpaceKaiUpdateChannel.fromKey(" beta "))
    }

    @Test
    fun `keys live in the spacekai_ prefix namespace`() {
        assertEquals("spacekai_update_auto_check", "${SPACEKAI_FLAG_PREFIX}update_auto_check")
        assertEquals("spacekai_update_channel", "${SPACEKAI_FLAG_PREFIX}update_channel")
    }
}
