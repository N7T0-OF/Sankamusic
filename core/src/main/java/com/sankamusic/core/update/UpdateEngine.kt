package com.sankamusic.core.update

import com.sankamusic.core.api.PluginManifest
import com.sankamusic.core.api.PluginUpdate
import com.sankamusic.core.api.UpdateManager
import com.sankamusic.core.api.UpdateState
import com.sankamusic.core.api.UpdateStatus
import com.sankamusic.core.api.UpstreamCompatibilityState
import com.sankamusic.core.api.UpstreamInfo
import com.sankamusic.core.api.UpstreamStatus

/**
 * Moteur de mises à jour (docs/UPDATE_SYSTEM.md, ADR-004) — trois catégories
 * DISTINCTES, jamais de mise à jour automatique d'un composant non validé :
 *
 *  1. [checkSankamusicUpdate] — Core Sankamusic (releases GitHub du projet) ;
 *  2. [checkPluginUpdates] — plugins/thèmes, indépendants du Core ;
 *  3. [checkUpstreamCompatibility] — base upstream SimpMusic (jamais auto-installée).
 *
 * Toute vérification est non bloquante (suspend) et tolérante : une source
 * injoignable ou un parsing invalide produit un état d'erreur, jamais un crash.
 * Les pré-releases (`-rc`, `-beta`) ne sont JAMAIS proposées comme stables.
 */
class UpdateEngine(
    private val installedVersion: SemVer,
    private val upstreamInfo: UpstreamInfo,
    private val releasesClient: GitHubReleasesClient,
    private val sankamusicRepository: String,
    private val upstreamRepository: String,
    private val installedPlugins: () -> Collection<PluginManifest> = { emptyList() },
    private val latestPluginVersion: suspend (pluginId: String) -> String? = { null },
    private val fetchAssetContent: suspend (asset: GitHubRelease.Asset) -> String? = { null },
) : UpdateManager {

    // ── 1. Core Sankamusic ─────────────────────────────────────────────

    override suspend fun checkSankamusicUpdate(): UpdateStatus {
        val latest = latestStableRelease(sankamusicRepository) ?: return errorStatus()
        val latestVersion = latest.version ?: return errorStatus()

        return if (latestVersion > installedVersion) {
            val apkAsset = latest.assets.firstOrNull { it.name.endsWith(".apk") }
            UpdateStatus(
                installedVersion = installedVersion.toString(),
                availableVersion = latestVersion.toString(),
                changelog = latest.body,
                downloadSizeBytes = apkAsset?.sizeBytes,
                sha256 = findApkSha256(latest),
                publishedAt = latest.publishedAt,
                state = UpdateState.UPDATE_AVAILABLE,
            )
        } else {
            UpdateStatus(installedVersion = installedVersion.toString(), state = UpdateState.UP_TO_DATE)
        }
    }

    // ── 2. Plugins / thèmes ────────────────────────────────────────────

    override suspend fun checkPluginUpdates(): List<PluginUpdate> {
        val updates = mutableListOf<PluginUpdate>()
        for (manifest in installedPlugins()) {
            val latestRaw = latestPluginVersion(manifest.id) ?: continue
            val latest = SemVer.parse(latestRaw) ?: continue
            val installed = SemVer.parse(manifest.version) ?: continue
            if (latest > installed) {
                updates += PluginUpdate(
                    pluginId = manifest.id,
                    fromVersion = manifest.version,
                    toVersion = latestRaw.trim(),
                )
            }
        }
        return updates
    }

    // ── 3. Compatibilité upstream SimpMusic ────────────────────────────

    override suspend fun checkUpstreamCompatibility(): UpstreamStatus {
        val installed = SemVer.parse(upstreamInfo.version)
        val latest = latestStableRelease(upstreamRepository)?.version
        val state = when {
            // Conservateur : source injoignable ou version locale invalide → on ne
            // peut pas confirmer la compatibilité → aucune mise à jour upstream.
            installed == null || latest == null -> UpstreamCompatibilityState.INCOMPATIBLE
            latest <= installed -> UpstreamCompatibilityState.COMPATIBLE
            else -> UpstreamCompatibilityState.NEEDS_ADAPTER_UPDATE
        }
        return UpstreamStatus(
            upstreamName = upstreamInfo.repository,
            installedUpstreamVersion = upstreamInfo.version,
            availableUpstreamVersion = latest?.toString(),
            adapterVersion = upstreamInfo.adapterVersion,
            compatibility = upstreamInfo.compatibility,
            state = state,
        )
    }

    // ── Intégrité avant installation ───────────────────────────────────

    /**
     * Vérifie l'intégrité SHA-256 d'un APK téléchargé AVANT installation.
     * Mismatch → échec propre : l'installation est refusée, aucun effet de bord.
     */
    fun verifyDownloadedApk(apkBytes: ByteArray, expectedSha256: String): Result<Unit> =
        if (Sha256.matches(apkBytes, expectedSha256)) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    "SHA-256 mismatch : l'intégrité de l'APK téléchargé n'est pas vérifiée, installation refusée.",
                ),
            )
        }

    // ── Internes ───────────────────────────────────────────────────────

    private fun errorStatus() = UpdateStatus(
        installedVersion = installedVersion.toString(),
        state = UpdateState.ERROR,
    )

    /** Dernière release stable (ni pré-release, ni draft) — liste triée récente → ancienne. */
    private suspend fun latestStableRelease(repository: String): GitHubRelease? =
        releasesClient.listReleases(repository).firstOrNull { !it.prerelease && !it.draft }

    /** Empreinte SHA-256 de l'APK via l'asset `SHA256SUMS.txt` de la même release. */
    private suspend fun findApkSha256(release: GitHubRelease): String? {
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
        val sumsAsset = release.assets.firstOrNull { it.name == "SHA256SUMS.txt" } ?: return null
        val content = fetchAssetContent(sumsAsset) ?: return null
        return Sha256.parseSha256For(content, apk.name)
    }
}
