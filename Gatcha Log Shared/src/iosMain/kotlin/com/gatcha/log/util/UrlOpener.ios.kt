package com.gatcha.log.util

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openUrl(url: String) {
    if (!SafeUrl.isHttps(url)) return   // 원격에서 온 주소를 여는 자리다 — https 가 아니면 열지 않는다
    NSURL.URLWithString(url)?.let { nsUrl ->
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
