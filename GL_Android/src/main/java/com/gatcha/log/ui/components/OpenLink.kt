package com.gatcha.log.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * 외부 링크 열기 — Compose 의 `LocalUriHandler.openUri` 대체.
 *
 * `openUri` 는 처리할 앱이 없으면 [ActivityNotFoundException] 을 그대로 던져 앱이 죽는다.
 * 특히 지도 링크(map.naver.com)처럼 **앱 링크로 가로채지는 주소**는 해당 앱 상태나 기본 브라우저
 * 설정에 따라 실패하는 경우가 있어, 여기서 잡고 폴백까지 태운다.
 *
 * 1) 원래 주소로 시도 → 2) [fallbackUrl] 이 있으면 그걸로 재시도 → 3) 둘 다 안 되면 안내 토스트.
 * (외부 앱을 띄우므로 NEW_TASK 플래그 필수 — Activity 가 아닌 컨텍스트에서 호출될 수 있다.)
 */
fun openExternalLink(ctx: Context, url: String, fallbackUrl: String? = null) {
    if (tryOpen(ctx, url)) return
    if (fallbackUrl != null && tryOpen(ctx, fallbackUrl)) return
    Toast.makeText(ctx, "링크를 열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show()
}

private fun tryOpen(ctx: Context, url: String): Boolean = try {
    ctx.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: Exception) {
    // 보안 정책·잘못된 스킴 등 그 외 실패도 앱을 죽이지 않는다.
    false
}

/**
 * 텍스트 공유 — 시스템 공유 시트(ACTION_SEND).
 *
 * 공지 본문은 앱이 원문 응답을 재구성해 그린 것이라 그대로 보낼 수 없다. 제목 + 원문 링크를 보낸다.
 * 공유 앱이 하나도 없으면(드묾) 예외를 삼키고 안내만 띄운다.
 */
fun shareText(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(ctx, "공유할 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show()
    }
}
