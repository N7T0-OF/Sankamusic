package com.maxrave.simpmusic.expect.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import org.koin.core.component.get
import android.content.Context

@Composable
actual fun filePickerResult(
    mimeType: String,
    onResultUri: (String?) -> Unit,
): FilePickerLauncher {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                onResultUri(uri.toString())
            }
        }
    return object : FilePickerLauncher {
        override fun launch() {
            launcher.launch(arrayOf(mimeType))
        }
    }
}

@Composable
actual fun fileSaverResult(
    fileName: String,
    mimeType: String,
    onResultUri: (String?) -> Unit,
): FilePickerLauncher {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
            if (uri != null) {
                onResultUri(uri.toString())
            }
        }
    return object : FilePickerLauncher {
        override fun launch() {
            launcher.launch(fileName)
        }
    }
}

actual suspend fun writeTextToUri(uri: String, text: String): Boolean {
    return try {
        val context: Context = GlobalContext.get().get()
        context.contentResolver.openOutputStream(Uri.parse(uri))?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
        }
        true
    } catch (e: Exception) {
        false
    }
}