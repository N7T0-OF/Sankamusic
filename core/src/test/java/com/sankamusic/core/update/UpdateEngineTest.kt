package com.sankamusic.core.update

import com.sankamusic.core.api.PluginManifest
import com.sankamusic.core.api.UpdateState
import com.sankamusic.core.api.UpstreamCompatibilityState
import com.sankamusic.core.api.UpstreamInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de comportement de l'[UpdateEngine] — réponses GitHub FACTICES,
 * aucun réseau (docs/UPDATE_SYSTEM.md).
 */
class UpdateEngineTest {

    private val sumsContent = "${"a".repeat(64)}  Sankamusic-v0.2.0.apk\n"

    private class FakeClient(
        private val byRepo: Map<String, List<GitHubRelease>>,
    ) : GitHubReleasesClient {
        override suspend fun listReleases(repository: String): List<GitHubRelease> =
            byRepo[repository] ?: emptyList()
    }

    private fun release(tag: String, prerelease: Boolean = false, body: String? = "changelog $tag") =
        GitHubRelease(
            tagName = tag,
            name = "Sankamusic $tag",
            body = body,
            publishedAt = "2026-08-01T10:05:00Z",
            prerelease = prerelease,
            draft = false,
            assets = listOf(
                GitHubRelease.Asset(name = "Sankamusic-$tag.apk", sizeBytes = 12_345_678),
                GitHubRelease.Asset(name = "SHA256SUMS.txt", sizeBytes = 120),
            ),
        )

    private fun engine(
        installed: String = "0.1.0",
        sankamusic: List<GitHubRelease> = emptyList(),
        upstream: List<GitHubRelease> = emptyList(),
        upstreamInstalled: String = "1.7.2",
        plugins: List<PluginManifest> = emptyList(),
        latestVersions: Map<String, String> = emptyMap(),
        fetch: suspend (GitHubRelease.Asset) -> String? = { null },
    ) = UpdateEngine(
        installedVersion = SemVer.parse(installed)!!,
        upstreamInfo = UpstreamInfo(
            repository = "owner/SimpMusic",
            version = upstreamInstalled,
            adapterVersion = 1,
            compatibility = "1.7.x",
        ),
        releasesClient = FakeClient(
            mapOf(
                "N7T0-OF/Sankamusic" to sankamusic,
                "owner/SimpMusic" to upstream,
            ),
        ),
        sankamusicRepository = "N7T0-OF/Sankamusic",
        upstreamRepository = "owner/SimpMusic",
        installedPlugins = { plugins },
        latestPluginVersion = { id -> latestVersions[id] },
        fetchAssetContent = fetch,
    )

    // ── 1. Core Sankamusic ─────────────────────────────────────────────

    @Test
    fun `update available with changelog size and sha256`() = runBlocking {
        val engine = engine(
            installed = "0.1.0",
            sankamusic = listOf(release("v0.2.0"), release("v0.1.0")),
            fetch = { asset -> if (asset.name == "SHA256SUMS.txt") sumsContent else null },
        )
        val status = engine.checkSankamusicUpdate()
        assertEquals(UpdateState.UPDATE_AVAILABLE, status.state)
        assertEquals("0.1.0", status.installedVersion)
        assertEquals("0.2.0", status.availableVersion)
        assertEquals("changelog v0.2.0", status.changelog)
        assertEquals(12_345_678L, status.downloadSizeBytes)
        assertEquals("a".repeat(64), status.sha256)
        assertEquals("2026-08-01T10:05:00Z", status.publishedAt)
    }

    @Test
    fun `up to date when installed is latest stable`() = runBlocking {
        val engine = engine(installed = "0.2.0", sankamusic = listOf(release("v0.2.0"), release("v0.1.0")))
        val status = engine.checkSankamusicUpdate()
        assertEquals(UpdateState.UP_TO_DATE, status.state)
        assertNull(status.availableVersion)
    }

    @Test
    fun `prerelease only is not offered as update`() = runBlocking {
        val engine = engine(
            installed = "0.1.0",
            sankamusic = listOf(release("v0.1.0"), release("v0.2.0-rc1", prerelease = true)),
        )
        assertEquals(UpdateState.UP_TO_DATE, engine.checkSankamusicUpdate().state)
    }

    @Test
    fun `empty release source yields clean ERROR without crash`() = runBlocking {
        // Réalité vérifiée (2026-08-27) : N7T0-OF/Sankamusic existe et est public,
        // mais a 0 release (HTTP 200, []). Le moteur doit terminer sans crash et
        // produire un statut d'erreur propre, pas de fausse « mise à jour ».
        val engine = engine(installed = "0.1.0", sankamusic = emptyList())
        val status = engine.checkSankamusicUpdate()
        assertEquals(UpdateState.ERROR, status.state)
        assertEquals("0.1.0", status.installedVersion)
        assertNull(status.availableVersion)
    }

    // ── 2. Plugins / thèmes ────────────────────────────────────────────

    @Test
    fun `plugin updates only for newer valid versions`() = runBlocking {
        val engine = engine(
            plugins = listOf(
                PluginManifest(id = "spotify", name = "Spotify", version = "1.0.0", apiVersion = 1, minSankamusicVersion = "0.1.0"),
                PluginManifest(id = "apple", name = "Apple", version = "1.0.0", apiVersion = 1, minSankamusicVersion = "0.1.0"),
                PluginManifest(id = "deezer", name = "Deezer", version = "1.0.0", apiVersion = 1, minSankamusicVersion = "0.1.0"),
            ),
            latestVersions = mapOf(
                "spotify" to "1.2.0",
                "apple" to "1.0.0", // pas plus récent → pas de mise à jour
                "deezer" to "pas.une.version", // invalide → ignoré proprement
            ),
        )
        val updates = engine.checkPluginUpdates()
        assertEquals(1, updates.size)
        assertEquals("spotify", updates[0].pluginId)
        assertEquals("1.0.0", updates[0].fromVersion)
        assertEquals("1.2.0", updates[0].toVersion)
    }

    // ── 3. Compatibilité upstream SimpMusic ────────────────────────────

    @Test
    fun `upstream compatible when available not newer`() = runBlocking {
        val engine = engine(upstreamInstalled = "1.7.2", upstream = listOf(release("v1.7.2")))
        val status = engine.checkUpstreamCompatibility()
        assertEquals(UpstreamCompatibilityState.COMPATIBLE, status.state)
        assertEquals("1.7.2", status.availableUpstreamVersion)
        assertEquals("1.7.x", status.compatibility)
    }

    @Test
    fun `upstream needs adapter update when newer available`() = runBlocking {
        val engine = engine(upstreamInstalled = "1.7.2", upstream = listOf(release("v1.8.0")))
        val status = engine.checkUpstreamCompatibility()
        assertEquals(UpstreamCompatibilityState.NEEDS_ADAPTER_UPDATE, status.state)
        assertEquals("1.8.0", status.availableUpstreamVersion)
        assertEquals(1, status.adapterVersion)
    }

    @Test
    fun `upstream conservative incompatibility when source unreachable`() = runBlocking {
        val engine = engine(upstreamInstalled = "1.7.2", upstream = emptyList())
        assertEquals(UpstreamCompatibilityState.INCOMPATIBLE, engine.checkUpstreamCompatibility().state)
    }

    // ── SHA-256 avant installation ─────────────────────────────────────

    @Test
    fun `verifyDownloadedApk accepts matching digest`() {
        val engine = engine()
        val bytes = "payload".toByteArray()
        val good = Sha256.digest(bytes)
        assertTrue(engine.verifyDownloadedApk(bytes, good).isSuccess)
    }

    @Test
    fun `verifyDownloadedApk fails cleanly on mismatch`() {
        val engine = engine()
        val bytes = "payload".toByteArray()
        val result = engine.verifyDownloadedApk(bytes, "f".repeat(64))
        assertTrue(result.isFailure)
        // Échec propre : le message explique le refus d'installation, pas de crash.
        assertTrue(result.exceptionOrNull()?.message?.contains("SHA-256") == true)
    }

    // ── Comportements découverts sur l'API réelle (2026-08-27) ────────────

    @Test
    fun `sha256 is null when release has no SHA256SUMS asset`() = runBlocking {
        // Réalité vérifiée : les releases SimpMusic (maxrave-dev) n'ont AUCUN
        // SHA256SUMS.txt → aucune empreinte → pas de vérification d'intégrité
        // possible (comportement documenté, docs/UPDATE_SYSTEM.md § 5).
        val noSums = GitHubRelease(
            tagName = "v0.2.0",
            body = "changelog",
            publishedAt = "2026-08-01T10:05:00Z",
            prerelease = false,
            draft = false,
            assets = listOf(GitHubRelease.Asset(name = "Sankamusic-v0.2.0.apk", sizeBytes = 12_345_678)),
        )
        val engine = engine(installed = "0.1.0", sankamusic = listOf(noSums, release("v0.1.0")))
        val status = engine.checkSankamusicUpdate()
        assertEquals(UpdateState.UPDATE_AVAILABLE, status.state)
        assertEquals("0.2.0", status.availableVersion)
        assertEquals(12_345_678L, status.downloadSizeBytes)
        assertNull(status.sha256)
    }

    @Test
    fun `sha256 is null when checksum content cannot be fetched`() = runBlocking {
        // Asset SHA256SUMS.txt présent mais contenu injoignable → pas d'empreinte,
        // la mise à jour reste proposée (sans vérification d'intégrité possible).
        val engine = engine(
            installed = "0.1.0",
            sankamusic = listOf(release("v0.2.0"), release("v0.1.0")),
            fetch = { null },
        )
        val status = engine.checkSankamusicUpdate()
        assertEquals(UpdateState.UPDATE_AVAILABLE, status.state)
        assertNull(status.sha256)
    }

    @Test
    fun `download size uses the first apk asset when several exist`() = runBlocking {
        // Réalité vérifiée : SimpMusic publie 8 APK par release (foss/full × 4 ABI).
        // Le moteur retient le premier asset .apk pour la taille ; notre repo n'en
        // publiera qu'un seul (RELEASE_GUIDE.md).
        val multiApk = GitHubRelease(
            tagName = "v0.2.0",
            body = "changelog",
            publishedAt = "2026-08-01T10:05:00Z",
            prerelease = false,
            draft = false,
            assets = listOf(
                GitHubRelease.Asset(name = "app-arm64.apk", sizeBytes = 1_000),
                GitHubRelease.Asset(name = "app-x86_64.apk", sizeBytes = 2_000),
            ),
        )
        val engine = engine(installed = "0.1.0", sankamusic = listOf(multiApk))
        val status = engine.checkSankamusicUpdate()
        assertEquals(UpdateState.UPDATE_AVAILABLE, status.state)
        assertEquals(1_000L, status.downloadSizeBytes)
        assertNull(status.sha256)
    }
}
