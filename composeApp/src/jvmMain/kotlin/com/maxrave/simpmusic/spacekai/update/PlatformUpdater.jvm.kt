package com.maxrave.simpmusic.spacekai.update

import com.maxrave.simpmusic.expect.openUrl
import com.maxrave.logger.Logger

private const val TAG = "SpaceKaiUpdateJvm"

/**
 * SPACEKAI FEATURE: desktop cannot drive the Android package installer, so a
 * SpaceKai update is handed to the system browser (which downloads the APK and,
 * once downloaded on the phone/emulator it targets, can be installed there).
 * This is an honest fallback — we never claim an install happened on desktop.
 */
internal actual object PlatformUpdater {
    actual suspend fun install(
        apkUrl: String,
        checksumsUrl: String?,
        onProgress: (downloaded: Long, total: Long, bytesPerSecond: Long) -> Unit,
        onPhase: (SpaceKaiUpdatePhase) -> Unit,
    ): SpaceKaiUpdateResult {
        onPhase(SpaceKaiUpdatePhase.INSTALLING)
        openUrl(apkUrl)
        Logger.d(TAG, "Opened APK URL in browser (desktop cannot install in place)")
        return SpaceKaiUpdateResult.Success
    }
}