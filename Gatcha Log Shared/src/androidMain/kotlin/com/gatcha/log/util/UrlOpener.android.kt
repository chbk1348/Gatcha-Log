package com.gatcha.log.util

import android.content.Intent
import android.net.Uri
import com.gatcha.log.storage.AppContext

actual fun openUrl(url: String) {
    if (!SafeUrl.isHttps(url)) return   // 원격에서 온 주소를 여는 자리다 — https 가 아니면 열지 않는다
    runCatching {
        AppContext.appContext.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
