package com.sankamusic.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Release GitHub (API v3, `GET /repos/{owner}/{repo}/releases`) — modèle réduit
 * aux champs utiles au moteur de mise à jour (docs/UPDATE_SYSTEM.md § 8).
 */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    /** Corps de la release → changelog affiché à l'utilisateur. */
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<Asset> = emptyList(),
) {
    /** Version du tag ("v2.1.0" → 2.1.0) ; null si le tag n'est pas un SemVer. */
    val version: SemVer? get() = SemVer.parseTag(tagName)

    @Serializable
    data class Asset(
        val name: String,
        @SerialName("size") val sizeBytes: Long,
        @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
    )
}
