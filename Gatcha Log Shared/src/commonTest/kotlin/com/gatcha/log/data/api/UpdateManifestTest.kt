package com.gatcha.log.data.api

import com.gatcha.log.util.SafeUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** version.json 이 다른 곳을 가리켜도 앱은 이 저장소의 릴리즈만 본다(27.51.1 보안 점검). */
class UpdateManifestTest {
    private val sha = "b".repeat(64)

    @Test
    fun `정상 매니페스트는 그대로 읽는다`() {
        val info = assertNotNull(parseUpdateManifest(
            """{"versionCode":275110,"versionName":"27.51.1","url":"https://github.com/chbk1348/Gatcha-Log/releases/latest",
               "apkUrl":"https://github.com/chbk1348/Gatcha-Log/releases/download/v27.51.1/Gatcha-Log-27.51.1.apk",
               "sha256":"$sha","minVersionCode":275100,"notes":["a"]}""", 275100))
        assertEquals("https://github.com/chbk1348/Gatcha-Log/releases/download/v27.51.1/Gatcha-Log-27.51.1.apk", info.apkUrl)
        assertEquals("https://github.com/chbk1348/Gatcha-Log/releases/latest", info.url)
        assertEquals(sha, info.sha256)
        assertEquals(275100, info.minVersionCode)
    }

    @Test
    fun `남의 주소는 버리고 이 저장소 주소로 돌아간다`() {
        val info = assertNotNull(parseUpdateManifest(
            """{"versionCode":275110,"versionName":"27.51.1","url":"itms-services://?action=download-manifest&url=https://evil.example/m.plist",
               "apkUrl":"https://evil.example/Gatcha-Log.apk","sha256":"$sha"}""", 275100))
        assertEquals("https://github.com/chbk1348/Gatcha-Log/releases/download/v27.51.1/Gatcha-Log-27.51.1.apk", info.apkUrl)
        assertEquals(SafeUrl.RELEASES_URL, info.url)
    }

    @Test
    fun `같거나 낮은 버전은 업데이트가 아니다`() {
        assertNull(parseUpdateManifest("""{"versionCode":275100,"versionName":"27.51.0"}""", 275100))
    }
}
