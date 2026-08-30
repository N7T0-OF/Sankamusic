package com.maxrave.simpmusic.expect

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.maxrave.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "UpdateInstaller"

// SPACEKAI FEATURE: download the release APK and hand it to the system package installer,
// so updating happens entirely in-app (no browser, no manual uninstall).
actual fun installApk(url: String?) {
    if (url.isNullOrBlank()) {
        openUrl(url ?: "")
        return
    }
    val context: AppCompatActivity = getKoin().get()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val apk = downloadApk(url, context)
            withContext(Dispatchers.Main) {
                val authority = "${context.packageName}.FileProvider"
                val uri = FileProvider.getUriForFile(context, authority, apk)
                val installIntent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(installIntent)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "In-app APK install failed: ${e.message}")
            openUrl(url)
        }
    }
}

private fun downloadApk(
    url: String,
    context: AppCompatActivity,
): File {
    val output = File(context.cacheDir, "spacekai-update.apk")
    if (output.exists()) output.delete()
    val connection: HttpURLConnection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.inputStream.use { input ->
            output.outputStream().use { stream -> input.copyTo(stream) }
        }
    } finally {
        connection.disconnect()
    }
    if (!output.exists() || output.length() == 0L) {
        throw IllegalStateException("Downloaded APK is empty")
    }
    return output
}

actual fun openUrl(url: String) {
    val context: AppCompatActivity = getKoin().get()
    val browserIntent =
        Intent(
            Intent.ACTION_VIEW,
            url.toUri(),
        )
    browserIntent.setFlags(FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(browserIntent)
}

actual fun shareUrl(
    title: String,
    url: String,
) {
    val context: AppCompatActivity = getKoin().get()
    val shareIntent = Intent(Intent.ACTION_SEND)
    shareIntent.type = "text/plain"
    shareIntent.putExtra(Intent.EXTRA_TEXT, url)
    shareIntent.setFlags(FLAG_ACTIVITY_NEW_TASK)
    val chooserIntent =
        Intent.createChooser(shareIntent, title)
    context.startActivity(chooserIntent)
}