package com.gatcha.log.auth

import android.app.Activity
import android.os.Bundle

/**
 * 웹 OAuth 리다이렉트 수신용 — 브라우저가 com.googleusercontent.apps.* 스킴으로 돌아오면
 * 콜백 URI 를 [GoogleWebOAuth] 로 넘기고 즉시 종료해 앱(이전 화면)으로 복귀한다.
 */
class OAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GoogleWebOAuth.onRedirect(intent?.data)
        finish()
    }
}
