package com.maxrave.simpmusic.spacekai.update

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val TAG = "SpaceKaiUpdater"

/**
 * SPACEKAI FEATURE: the honest Android update pipeline.
 *
 *   1. Download every byte to a cache file, reporting (downloaded, total, speed) as
 *      the stream is read - progress is real, not estimated.
 *   2. If the release ships a SHA256SUMS.txt (checksumsUrl), download it and verify
 *      the APK's SHA-256 matches its line. On mismatch the install is aborted and the
 *      file is deleted - a corrupted/tampered APK is never handed to the installer.
 *   3. Hand the verified file to the system package installer via FileProvider.
 *
 * The FileProvider authority and provider_paths.xml are already registered in the
 * androidApp manifest (the same wiring openUrl uses).
 */
internal actual object PlatformUpdater {
    actual suspend fun install(
        apkUrl: String,
        checksumsUrl: String?,
        onProgress: (downloaded: Long, total: Long, bytesPerSecond: Long) -> Unit,
    ): SpaceKaiUpdateResult =
        withContext(Dispatchers.IO) {
            try {
                val context: AppCompatActivity = getKoin().get()
                val apkFile = File(context.cacheDir, "spacekai-update-$TAG.apk")
                val tmp = File(context.cacheDir, "spacekai-update-$TAG.tmp")
                if (apkFile.exists()) apkFile.delete()
                if (tmp.exists()) tmp.delete()

                downloadWithProgress(url = apkUrl, output = tmp, onProgress = onProgress)

                // VERIFYING
                val verified =
                    checksumsUrl.isNullOrBlank() ||
                        verifySha256(
                            apkFile = tmp,
                            checksumsUrl = checksumsUrl,
                        )
                if (!verified) {
                    tmp.delete()
                    SpaceKaiUpdateResult.Failure(
                        "SHA-256 verification failed - the APK is corrupt or tampered.",
                    )
                } else {
                    if (!tmp.renameTo(apkFile)) {
                        apkFile.delete()
                        tmp.copyTo(apkFile, overwrite = true)
                        tmp.delete()
                    }
                    val uri =
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.FileProvider",
                            apkFile,
                        )
                    val installIntent =
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or FLAG_ACTIVITY_NEW_TASK)
                        }
                    context.startActivity(installIntent)
                    SpaceKaiUpdateResult.Success
                }
            } catch (e: Exception) {
                Logger.e(TAG, "In-app update failed: ${e.message}")
                SpaceKaiUpdateResult.Failure(e.message ?: "Update failed.")
            }
        }

}

private fun downloadWithProgress(
    url: String,
    output: File,
    onProgress: (downloaded: Long, total: Long, bytesPerSecond: Long) -> Unit,
) {
    val connection: HttpURLConnection = URL(url).openConnection() as HttpURLConnection
    val start = System.currentTimeMillis()
    val total = connection.contentLengthLong
    output.outputStream().use { os ->
        connection.inputStream.use { input ->
            val buf = ByteArray(64 * 1024)
            var downloaded = 0L
            var lastT = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                os.write(buf, 0, n)
                downloaded += n
                val now = System.currentTimeMillis()
                if (now - lastT >= 150) {
                    lastT = now
                    val speed = if (now > start) downloaded * 1000L / (now - start) else 0L
                    onProgress(downloaded, total, speed)
                }
            }
            onProgress(downloaded, total, if (System.currentTimeMillis() > start) downloaded * 1000L / (System.currentTimeMillis() - start) else 0L)
        }
    }
    connection.disconnect()
}

private fun verifySha256(
    apkFile: File,
    checksumsUrl: String,
): Boolean {
    // 1. Fetch the checksums manifest.
    val manifest: String =
        try {
            val conn: HttpURLConnection = URL(checksumsUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Logger.e(TAG, "Could not fetch checksums manifest: ${e.message}")
            return false
        }
    // 2. Compute the local APK SHA-256.
    val digest = MessageDigest.getInstance("SHA-256")
    apkFile.inputStream().use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }
    val localHex = digest.digest().joinToString("") { "%02x".format(it) }
    // 3. Match a manifest line whose hash equals the APK: "<sha256>  [**/]<filename>.<apk>"
    val match =
        manifest.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .firstOrNull { line ->
                val parts = line.split(Regex("\\s+"))
                parts.size >= 2 &&
                    parts[0].equals(localHex, ignoreCase = true) &&
                    parts.last().endsWith(".apk", ignoreCase = true)
            }
    if (match == null) {
        Logger.e(TAG, "No SHA-256 line matched the downloaded APK")
        return false
    }
    Logger.d(TAG, "SHA-256 verified for downloaded APK")
    return true
}