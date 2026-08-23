package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable
import com.maxrave.data.io.getHomeFolderPath
import java.io.File

@Composable
actual fun filePickerResult(
    mimeType: String,
    onResultUri: (String?) -> Unit,
): FilePickerLauncher =
    object : FilePickerLauncher {
        override fun launch() {
            onResultUri(null)
        }
    }

@Composable
actual fun fileSaverResult(
    fileName: String,
    mimeType: String,
    onResultUri: (String?) -> Unit,
): FilePickerLauncher =
    object : FilePickerLauncher {
        override fun launch() {
            onResultUri(File(getHomeFolderPath(emptyList()), fileName).absolutePath)
        }
    }

actual suspend fun writeTextToUri(uri: String, text: String): Boolean {
    return try {
        File(uri).writeText(text, Charsets.UTF_8)
        true
    } catch (e: Exception) {
        false
    }
}