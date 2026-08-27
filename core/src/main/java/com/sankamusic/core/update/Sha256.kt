package com.sankamusic.core.update

import java.security.MessageDigest

/**
 * Vérification d'intégrité SHA-256 (docs/UPDATE_SYSTEM.md § 5, RELEASE_GUIDE.md,
 * docs/SECURITY.md § 5) : un artefact téléchargé n'est installé QUE si son
 * empreinte correspond à celle publiée. Mismatch → échec propre, pas d'installation.
 */
object Sha256 {

    /** Empreinte hexadécimale (minuscules) d'un contenu binaire. */
    fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    /**
     * Vrai si [bytes] a exactement l'empreinte [expectedHex]
     * (64 caractères hexadécimaux, insensible à la casse).
     */
    fun matches(bytes: ByteArray, expectedHex: String): Boolean {
        val expected = expectedHex.trim().lowercase()
        if (expected.length != 64 || expected.any { it !in "0123456789abcdef" }) return false
        return digest(bytes) == expected
    }

    /**
     * Extrait l'empreinte SHA-256 d'un fichier depuis un `SHA256SUMS.txt`
     * (format `"<hex>  <filename>"`, `"<hex> *<filename>"`, chemins tolérés).
     * Retourne null si aucune ligne valide ne correspond.
     */
    fun parseSha256For(checksums: String, fileName: String): String? {
        for (rawLine in checksums.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) continue
            val hex = parts[0]
            if (hex.length != 64 || hex.any { it !in "0123456789abcdefABCDEF" }) continue
            val entryName = parts[1].removePrefix("*").substringAfterLast('/')
            if (entryName == fileName) return hex
        }
        return null
    }
}
