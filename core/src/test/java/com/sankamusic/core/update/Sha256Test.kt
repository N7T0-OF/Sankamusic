package com.sankamusic.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Sha256Test {

    // Valeur de référence NIST : SHA-256("abc")
    private val ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun `digest matches NIST reference`() {
        assertEquals(ABC_SHA256, Sha256.digest("abc".toByteArray()))
    }

    @Test
    fun `matches accepts correct digest regardless of case`() {
        assertTrue(Sha256.matches("abc".toByteArray(), ABC_SHA256))
        assertTrue(Sha256.matches("abc".toByteArray(), ABC_SHA256.uppercase()))
    }

    @Test
    fun `mismatch fails cleanly`() {
        assertTrue(!Sha256.matches("abc".toByteArray(), "f".repeat(64)))
        assertTrue(!Sha256.matches("abcd".toByteArray(), ABC_SHA256))
    }

    @Test
    fun `invalid expected hex fails`() {
        assertTrue(!Sha256.matches("abc".toByteArray(), "xyz"))
        assertTrue(!Sha256.matches("abc".toByteArray(), "g".repeat(64)))
        assertTrue(!Sha256.matches("abc".toByteArray(), ABC_SHA256.dropLast(1)))
    }

    @Test
    fun `parseSha256For finds the right file`() {
        val checksums = "${"a".repeat(64)}  Sankamusic-v0.2.0.apk\n${"b".repeat(64)} *SHA256SUMS.txt\n"
        assertEquals("a".repeat(64), Sha256.parseSha256For(checksums, "Sankamusic-v0.2.0.apk"))
        assertEquals("b".repeat(64), Sha256.parseSha256For(checksums, "SHA256SUMS.txt"))
    }

    @Test
    fun `parseSha256For supports paths and ignores invalid lines`() {
        val checksums = "not-a-hash  file.apk\n${"c".repeat(64)}  releases/Sankamusic-v0.1.0.apk\n"
        assertEquals("c".repeat(64), Sha256.parseSha256For(checksums, "Sankamusic-v0.1.0.apk"))
        assertNull(Sha256.parseSha256For(checksums, "file.apk"))
    }
}
