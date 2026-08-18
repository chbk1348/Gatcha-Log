package com.gatcha.log.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Storage Access Framework(SAF) 파일 I/O — Shared SpendingViewModel 은 문자열만 주고받고,
 * 실제 파일 read/write 는 UI 레이어(이 헬퍼)가 담당한다(iOS 의 fileExporter/fileImporter 와 동일 구조).
 * 모두 IO 디스패처에서 수행해 메인 스레드 블로킹을 피한다.
 */
object SafIO {

    suspend fun readText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
    }

    suspend fun readTexts(context: Context, uris: List<Uri>): List<String> =
        uris.mapNotNull { readText(context, it) }

    suspend fun writeText(context: Context, uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("출력 스트림 없음")
            true
        }.getOrDefault(false)
    }
}
