package com.maxrave.simpmusic.spacekai

/**
 * SPACEKAI FEATURE: single source of truth for the SpaceKai release repository.
 *
 * The upstream app checks GitHub releases of *SimpMusic* for updates. SpaceKai
 * must check its own repository instead — otherwise users would be offered
 * SimpMusic releases (which do not carry the SpaceKai layer) or never see an
 * update at all.
 *
 * The actual HTTP call lives in the `core/data` module
 * (`UpdateRepositoryImpl.checkForGithubReleaseUpdate()`), which is upstream
 * code we do not modify here. Wire this object in there — the whole change is
 * replacing the hardcoded upstream repo with these two constants:
 *
 * ```kotlin
 * // SPACEKAI FEATURE: point the update checker at SpaceKai releases.
 * val url = "https://api.github.com/repos/${SpaceKaiUpdateConfig.repoOwner}/${SpaceKaiUpdateConfig.repoName}/releases/latest"
 * ```
 *
 * This is a *known upstream conflict*: every merge from SimpMusic may restore
 * the SimpMusic repo URL. After each `./scripts/update-upstream.sh`, grep for
 * `maxrave-dev/SimpMusic` in `core/data/.../update/` and re-apply this line.
 *
 * The `updateChannel` setting (GitHub / F-Droid) is untouched: SpaceKai ships
 * via GitHub releases, so the default GITHUB channel is the right one.
 */
object SpaceKaiUpdateConfig {
    /** GitHub owner of the SpaceKai fork. */
    const val repoOwner: String = "N7T0-OF"

    /** GitHub repository name of the SpaceKai fork. */
    const val repoName: String = "Sankamusic"

    /** Human-facing repository URL. */
    val repoUrl: String = "https://github.com/$repoOwner/$repoName"

    /** API endpoint for the latest release — what the update checker hits. */
    val latestReleaseApiUrl: String =
        "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"

    /** Release page — what the update dialog should link to. */
    val releasesPageUrl: String = "$repoUrl/releases"
}