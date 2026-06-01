package com.gatcha.log.ui.platform

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.gatcha.log.storage.AppContext

/** SAF(Storage Access Framework) 기반 — :app 의 파일 가져오기/내보내기와 동일한 동작 */

@Composable
actual fun rememberFileOpenLauncher(onResult: (contents: List<String>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        val contents = uris.mapNotNull { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
        }
        onResult(contents)
    }
    return remember(launcher) { { launcher.launch(arrayOf("application/json", "text/plain", "*/*")) } }
}

@Composable
actual fun rememberFileSaveLauncher(defaultName: String, contentProvider: () -> String?): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                contentProvider()?.let { content ->
                    context.contentResolver.openOutputStream(uri)?.use { it.write(content.encodeToByteArray()) }
                }
            }
        }
    }
    return remember(launcher, defaultName) { { launcher.launch(defaultName) } }
}

actual fun openUrl(url: String) {
    runCatching {
        AppContext.appContext.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
