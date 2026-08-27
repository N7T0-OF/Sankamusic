package com.sankamusic.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleasesJsonTest {

    // Réponse réelle de l'API GitHub (schéma v3), champs inconnus inclus.
    private val json = """
        [
          {
            "url": "https://api.github.com/repos/N7T0-OF/Sankamusic/releases/1",
            "id": 1,
            "tag_name": "v0.2.0",
            "target_commitish": "main",
            "name": "Sankamusic v0.2.0",
            "body": "### Ajouté\n- Nouveau système de plugins\n- Amélioration du lecteur",
            "draft": false,
            "prerelease": false,
            "created_at": "2026-08-01T10:00:00Z",
            "published_at": "2026-08-01T10:05:00Z",
            "assets": [
              {
                "url": "https://api.github.com/repos/N7T0-OF/Sankamusic/releases/assets/11",
                "id": 11,
                "name": "Sankamusic-v0.2.0.apk",
                "size": 12345678,
                "browser_download_url": "https://github.com/N7T0-OF/Sankamusic/releases/download/v0.2.0/Sankamusic-v0.2.0.apk"
              },
              {
                "name": "SHA256SUMS.txt",
                "size": 120,
                "browser_download_url": "https://github.com/N7T0-OF/Sankamusic/releases/download/v0.2.0/SHA256SUMS.txt"
              }
            ]
          },
          {
            "tag_name": "v0.3.0-rc1",
            "name": "rc",
            "body": null,
            "draft": false,
            "prerelease": true,
            "assets": []
          },
          {
            "tag_name": "v0.1.0",
            "name": "Sankamusic v0.1.0",
            "body": null,
            "draft": false,
            "prerelease": false,
            "assets": []
          }
        ]
    """.trimIndent()

    @Test
    fun `parse real-shaped payload with unknown keys`() {
        val releases = parseGitHubReleasesJson(json)
        assertEquals(3, releases.size)

        val r0 = releases[0]
        assertEquals("v0.2.0", r0.tagName)
        assertEquals(SemVer(0, 2, 0), r0.version)
        assertTrue(!r0.prerelease)
        assertTrue(!r0.draft)
        assertEquals("2026-08-01T10:05:00Z", r0.publishedAt)
        assertTrue(r0.body!!.contains("Nouveau système de plugins"))
        assertEquals(2, r0.assets.size)
        assertEquals("Sankamusic-v0.2.0.apk", r0.assets[0].name)
        assertEquals(12_345_678L, r0.assets[0].sizeBytes)
        assertEquals(
            "https://github.com/N7T0-OF/Sankamusic/releases/download/v0.2.0/Sankamusic-v0.2.0.apk",
            r0.assets[0].browserDownloadUrl,
        )

        assertTrue(releases[1].prerelease)
        assertEquals(SemVer(0, 3, 0, "rc1"), releases[1].version)
        assertEquals(SemVer(0, 1, 0), releases[2].version)
    }

    @Test
    fun `invalid json yields empty list`() {
        assertEquals(emptyList<GitHubRelease>(), parseGitHubReleasesJson("pas du json"))
        assertEquals(emptyList<GitHubRelease>(), parseGitHubReleasesJson(""))
    }
}
