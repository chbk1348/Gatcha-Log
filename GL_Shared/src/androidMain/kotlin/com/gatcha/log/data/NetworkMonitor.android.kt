package com.gatcha.log.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.gatcha.log.storage.AppContext

/** ConnectivityManager 기반 즉시 연결 질의. 권한: ACCESS_NETWORK_STATE(매니페스트 선언됨). */
actual object NetworkMonitor {
    actual fun isOnline(): Boolean {
        val cm = AppContext.appContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
