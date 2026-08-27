package com.sankamusic.core.update

import com.sankamusic.core.api.NetworkApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Source de releases GitHub. L'implémentation réseau ([HttpGitHubReleasesClient])
 * s'appuie sur la [NetworkApi] de la plateforme ; les tests injectent une source
 * factice (aucun réseau).
 */
interface GitHubReleasesClient {
    /** Liste les releases d'un repository "owner/name" (triées du plus récent au plus ancien). */
    suspend fun listReleases(repository: String): List<GitHubRelease>
}

/**
 * Client GitHub Releases basé sur la [NetworkApi]
 * (`GET https://api.github.com/repos/{owner}/{repo}/releases`).
 * Échec réseau ou parsing → liste vide (l'appelant décide du comportement).
 */
class HttpGitHubReleasesClient(
    private val network: NetworkApi,
) : GitHubReleasesClient {

    override suspend fun listReleases(repository: String): List<GitHubRelease> {
        val json = network.get("https://api.github.com/repos/$repository/releases")
            .getOrElse { return emptyList() }
        return parseGitHubReleasesJson(json)
    }
}

/** Instance unique du décodeur (les champs inconnus de l'API sont ignorés). */
private val githubJson = Json { ignoreUnknownKeys = true }

/**
 * Parse le corps JSON de `GET /repos/{owner}/{repo}/releases`.
 * Les champs inconnus sont ignorés (l'API en expose beaucoup) ; JSON invalide → [].
 */
fun parseGitHubReleasesJson(json: String): List<GitHubRelease> = runCatching {
    githubJson.decodeFromString<List<GitHubRelease>>(json)
}.getOrDefault(emptyList())
