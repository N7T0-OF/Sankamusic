package com.sankamusic.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun `parse valid versions`() {
        assertEquals(SemVer(2, 1, 0), SemVer.parse("2.1.0"))
        assertEquals(SemVer(2, 1, 0, "rc1"), SemVer.parse("2.1.0-rc1"))
        assertEquals(SemVer(2, 1, 0, "beta.2"), SemVer.parse(" 2.1.0-beta.2 "))
        assertEquals(SemVer(2, 1, 0), SemVer.parseTag("v2.1.0"))
        assertEquals(SemVer(2, 1, 0), SemVer.parseTag("2.1.0"))
    }

    @Test
    fun `parse invalid versions returns null`() {
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("2.1"))
        assertNull(SemVer.parse("2.a.0"))
        assertNull(SemVer.parse("v2.1.0")) // le "v" est géré par parseTag uniquement
        assertNull(SemVer.parse("2.1.0.0"))
        assertNull(SemVer.parse("latest"))
    }

    @Test
    fun `major minor patch ordering`() {
        assertTrue(SemVer.parse("2.1.0")!! > SemVer.parse("2.0.9")!!)
        assertTrue(SemVer.parse("3.0.0")!! > SemVer.parse("2.99.99")!!)
        assertTrue(SemVer.parse("2.1.1")!! > SemVer.parse("2.1.0")!!)
        assertEquals(SemVer.parse("2.1.0"), SemVer.parse("2.1.0"))
    }

    @Test
    fun `prerelease ordering per SemVer`() {
        assertTrue(SemVer.parse("2.1.0")!! > SemVer.parse("2.1.0-rc1")!!)
        assertTrue(SemVer.parse("2.1.0-rc1")!! > SemVer.parse("2.1.0-beta")!!)
        assertTrue(SemVer.parse("2.1.0-beta.2")!! > SemVer.parse("2.1.0-beta.1")!!)
        assertTrue(SemVer.parse("2.1.0-alpha")!! < SemVer.parse("2.1.0-rc1")!!)
        assertTrue(SemVer.parse("2.1.0-1")!! < SemVer.parse("2.1.0-alpha")!!) // numérique < alphanumérique
    }

    @Test
    fun `string representation`() {
        assertEquals("2.1.0", SemVer(2, 1, 0).toString())
        assertEquals("2.1.0-rc1", SemVer(2, 1, 0, "rc1").toString())
    }
}
