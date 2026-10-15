package com.gatcha.log.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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

    /**
     * 쓰기에 실패한 문서를 지운다(27.52.0) — 「문서 만들기」는 고르는 순간 빈 파일을 만들어 두므로,
     * 그대로 두면 0바이트 파일이 저장된 것처럼 남는다. 제공자가 삭제를 지원하지 않으면 그대로 둔다(실패 무시).
     */
    suspend fun delete(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        }
    }
}
