package com.gatcha.log

actual fun platformName(): String = "Android ${android.os.Build.VERSION.RELEASE}"
