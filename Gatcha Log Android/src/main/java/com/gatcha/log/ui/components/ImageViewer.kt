package com.gatcha.log.ui.components

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL

// ════════════════════════════════════════════════════════════════════════════
// 이미지 뷰어 — 공지 본문 이미지를 탭하면 전체화면으로 크게 보고, 갤러리에 저장한다.
//
// 본문 안에서는 이미지가 폭에 맞춰 작게 들어가 있어 표·수치가 안 읽힌다(공지 이미지는 대개 정보 표다).
// 확대(핀치)와 이동(드래그)을 붙여 실제로 읽을 수 있게 하고, 저장은 갤러리로 내보낸다.
//
// (SwiftUI 패리티: GL_IOS/DesignSystem/ImageViewer.swift)
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun GlgImageViewer(url: String, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        // 1배 미만으로는 줄지 않게(원본보다 작아지면 읽을 이유가 없다), 6배까지만.
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        // 확대 상태에서만 이동 — 1배에서 끌리면 화면이 흔들리는 것처럼 보인다.
        if (scale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .transformable(transform),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ViewerButton(Icons.Default.Close, "닫기", onDismiss)
                ViewerButton(Icons.Default.Download, "저장") {
                    scope.launch {
                        val ok = saveImageToGallery(context, url)
                        onSaved(if (ok) "갤러리에 저장했어요" else "저장하지 못했어요")
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/**
 * 이미지를 갤러리(Pictures/Gatcha LOG)에 저장한다.
 *
 * Android 10+ 는 MediaStore 에 쓰는 한 저장소 권한이 필요 없다(scoped storage) — 그래서 권한 요청이 없다.
 * 9 이하는 RELATIVE_PATH 를 못 써서 기본 Pictures 로 떨어진다.
 */
private suspend fun saveImageToGallery(context: Context, url: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = URL(url).openStream().use { it.readBytes() }
        val name = "gatchalog_${System.currentTimeMillis()}.png"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Gatcha LOG")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore insert 실패")
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("스트림 열기 실패")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    }.getOrElse { false }
}
