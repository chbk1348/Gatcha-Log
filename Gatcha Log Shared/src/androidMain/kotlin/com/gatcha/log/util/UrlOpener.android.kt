package com.gatcha.log.util

import android.content.Intent
import android.net.Uri
import com.gatcha.log.storage.AppContext

actual fun openUrl(url: String) {
    runCatching {
        AppContext.appContext.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
