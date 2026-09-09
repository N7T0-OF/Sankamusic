package com.maxrave.simpmusic.spacekai

import com.maxrave.domain.data.model.update.UpdateData
import com.maxrave.domain.manager.DataStoreManager

/**
 * SPACEKAI FEATURE: user preferences for the SpaceKai updater, persisted as
 * generic DataStore string keys (`spacekai_<key>`) — the same mechanism as
 * the feature flags, so the layer needs no typed keys in the core submodule.
 *
 *   - [autoCheckEnabled] — check for a newer SpaceKai release on startup
 *     (default: on). The check itself is owned by SharedViewModel; this only
 *     decides whether the startup trigger fires.
 *   - [updateChannel] — STABLE considers published stable releases only;
 *     BETA also considers pre-releases. GitHub `/releases/latest` only ever
 *     returns stable, so BETA additionally looks at `/releases` and compares
 *     the newest prerelease with the newest stable candidate.
 */
enum class SpaceKaiUpdateChannel(val key: String) {
    STABLE("stable"),
    BETA("beta"),
    ;

    companion object {
        fun fromKey(raw: String?): SpaceKaiUpdateChannel =
            entries.firstOrNull { it.key == raw?.trim()?.lowercase() } ?: STABLE
    }
}

object SpaceKaiUpdatePrefs {
    const val AUTO_CHECK_KEY = "${SPACEKAI_FLAG_PREFIX}update_auto_check"
    const val CHANNEL_KEY = "${SPACEKAI_FLAG_PREFIX}update_channel"

    const val DEFAULT_AUTO_CHECK = true
    val DEFAULT_CHANNEL = SpaceKaiUpdateChannel.STABLE

    fun autoCheckEnabled(getString: (String) -> String?): Boolean =
        getString(AUTO_CHECK_KEY)?.let { it == DataStoreManager.TRUE } ?: DEFAULT_AUTO_CHECK

    fun setAutoCheck(putString: (String, String) -> Unit, enabled: Boolean) {
        putString(
            AUTO_CHECK_KEY,
            if (enabled) DataStoreManager.TRUE else DataStoreManager.FALSE,
        )
    }

    fun channel(getString: (String) -> String?): SpaceKaiUpdateChannel =
        SpaceKaiUpdateChannel.fromKey(getString(CHANNEL_KEY))

    fun setChannel(putString: (String, String) -> Unit, channel: SpaceKaiUpdateChannel) {
        putString(CHANNEL_KEY, channel.key)
    }
}

/**
 * Chooses the release that may be shown by the SpaceKai updater.
 *
 * Stable mode is deliberately limited to the stable endpoint's candidate. Beta mode
 * considers both candidates that are newer than the installed version and chooses the
 * highest semantic version; a stable release wins when both candidates have the same
 * numeric version. This keeps an older prerelease from hiding a newer stable build.
 */
fun selectSpaceKaiUpdate(
    channel: SpaceKaiUpdateChannel,
    installedVersion: String?,
    betaRelease: UpdateData?,
    stableRelease: UpdateData?,
): UpdateData? {
    if (channel == SpaceKaiUpdateChannel.STABLE) return stableRelease

    val candidates =
        listOfNotNull(betaRelease, stableRelease)
            .filter { isVersionNewer(it.tagName, installedVersion) }
    return candidates.maxWithOrNull(
        Comparator { left, right ->
            val leftVersion = parseVersion(left.tagName)
            val rightVersion = parseVersion(right.tagName)
            when {
                leftVersion == null && rightVersion == null -> 0
                leftVersion == null -> -1
                rightVersion == null -> 1
                else -> {
                    val versionComparison = leftVersion.compareTo(rightVersion)
                    if (versionComparison != 0) {
                        versionComparison
                    } else {
                        val releaseTypeComparison =
                            (if (left.isPrerelease) 0 else 1)
                                .compareTo(if (right.isPrerelease) 0 else 1)
                        if (releaseTypeComparison != 0) {
                            releaseTypeComparison
                        } else {
                            val dateComparison =
                                (left.releaseTime ?: "").compareTo(right.releaseTime ?: "")
                            if (dateComparison != 0) dateComparison else left.tagName.compareTo(right.tagName)
                        }
                    }
                }
            }
        },
    )
}