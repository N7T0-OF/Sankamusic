package com.sankamusic.core.update

import kotlin.math.min

/**
 * Version sémantique (SemVer 2.0.0) — voir docs/UPDATE_SYSTEM.md § 3.
 *
 * Comparaison : MAJEUR > MINEUR > PATCH > pré-release.
 * Une version sans pré-release est supérieure à sa propre pré-release
 * (`2.1.0` > `2.1.0-rc1` > `2.1.0-beta`).
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** Pré-release, ex. "rc1", "beta.2" ; null = version stable. */
    val prerelease: String? = null,
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        val m = major.compareTo(other.major)
        if (m != 0) return m
        val n = minor.compareTo(other.minor)
        if (n != 0) return n
        val p = patch.compareTo(other.patch)
        if (p != 0) return p

        val mine = prerelease
        val theirs = other.prerelease
        return when {
            mine == null && theirs == null -> 0
            mine == null -> 1 // 2.1.0 > 2.1.0-rc1
            theirs == null -> -1
            else -> comparePrerelease(mine, theirs)
        }
    }

    override fun toString(): String = prerelease?.let { "$major.$minor.$patch-$it" } ?: "$major.$minor.$patch"

    companion object {
        private val REGEX = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$""")

        /** Parse "2.1.0" ou "2.1.0-rc1" ; null si le format est invalide. */
        fun parse(raw: String): SemVer? {
            val match = REGEX.matchEntire(raw.trim()) ?: return null
            return SemVer(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
                prerelease = match.groupValues[4].ifEmpty { null },
            )
        }

        /** Parse un tag git GitHub ("v2.1.0" → 2.1.0) ; null si invalide. */
        fun parseTag(raw: String): SemVer? = parse(raw.removePrefix("v"))
    }
}

/**
 * Comparaison de deux pré-releases selon SemVer 2.0.0 :
 * les identifiants numériques comparent numériquement, les alphanumériques
 * lexicalement, un numérique est inférieur à un alphanumérique, et un nombre
 * d'identifiants plus grand l'emporte si tous les précédents sont égaux.
 */
private fun comparePrerelease(a: String, b: String): Int {
    val ai = a.split(".")
    val bi = b.split(".")
    for (i in 0 until min(ai.size, bi.size)) {
        val x = ai[i]
        val y = bi[i]
        val xn = x.all { it.isDigit() }
        val yn = y.all { it.isDigit() }
        val c = when {
            xn && yn -> x.toInt().compareTo(y.toInt())
            xn && !yn -> -1 // numérique < alphanumérique
            !xn && yn -> 1
            else -> x.compareTo(y)
        }
        if (c != 0) return c
    }
    return ai.size.compareTo(bi.size)
}
