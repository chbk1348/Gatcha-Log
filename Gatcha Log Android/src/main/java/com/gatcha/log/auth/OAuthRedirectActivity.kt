package com.gatcha.log.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.gatcha.log.MainActivity

/**
 * 웹 OAuth 리다이렉트 수신용 — 브라우저가 com.googleusercontent.apps.* 스킴으로 돌아오면
 * 콜백 URI 를 [GoogleWebOAuth] 로 넘긴다.
 *
 * 단순히 finish() 만 하면 같은 태스크 위에 떠 있던 Custom Tab(인앱 브라우저)이 그대로 남아
 * 로그인 후에도 브라우저가 닫히지 않는다. 그래서 MainActivity 를 CLEAR_TOP|SINGLE_TOP 으로
 * 다시 전면화해 위에 쌓인 Custom Tab 을 스택에서 걷어낸 뒤 종료한다(AppAuth 의 리다이렉트 처리 방식).
 */
class OAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        GoogleWebOAuth.onRedirect(intent?.data)
        // MainActivity 를 전면으로 끌어올리며 그 위(Custom Tab)를 모두 정리 → 브라우저 닫힘.
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
