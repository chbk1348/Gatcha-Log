package com.gatcha.log.ui.components

import android.app.Activity
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
 */
fun openExternalLink(
    ctx: Context,
    url: String,
    fallbackUrl: String? = null,
    /**
     * 이 앱으로 먼저 열어 본다(예: 티켓링크 `kr.co.ticketlink.cne`). 없거나 이 주소를 못 받으면
     * 조용히 평소 경로로 내려간다.
     *
     * **커스텀 스킴(`ticketlink://…`)을 쓰지 않는 이유**: 공개된 문서가 없어 지어내야 하는데,
     * 스킴이 틀리면 그 앱이 깔려 있어도 영영 안 열린다. 패키지를 지정해 같은 https 주소를
     * 보내면 앱이 자기 주소로 등록해 둔 화면을 스스로 고르고, 못 고르면 예외가 나 브라우저로
     * 떨어진다 — 틀려도 지금과 같아질 뿐이다.
     */
    preferPackage: String? = null,
) {
    if (preferPackage != null && tryOpen(ctx, url, preferPackage)) return
    if (tryOpen(ctx, url)) return
    if (fallbackUrl != null && tryOpen(ctx, fallbackUrl)) return
    Toast.makeText(ctx, "링크를 열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show()
}

private fun tryOpen(ctx: Context, url: String, pkg: String? = null): Boolean = try {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).newTaskIfNeeded(ctx)
    if (pkg != null) intent.setPackage(pkg)
    ctx.startActivity(intent)
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
    val chooser = Intent.createChooser(send, null).newTaskIfNeeded(ctx)
    try {
        ctx.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(ctx, "공유할 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Activity 컨텍스트가 아닐 때만 NEW_TASK 를 붙인다.
 *
 * NEW_TASK 는 Activity 가 아닌 컨텍스트(앱 컨텍스트·서비스 등)에서 화면을 띄울 때만 필요하다.
 * Activity 에서 붙이면 그 화면이 **별도 태스크**로 올라가 뒤로가기 흐름과 최근 앱 목록이 갈린다.
 */
private fun Intent.newTaskIfNeeded(ctx: Context): Intent = apply {
    if (ctx !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
