package com.gatcha.log.data.api

import com.gatcha.log.data.PackageCatalog
import com.gatcha.log.util.currentTimeMillis

/**
 * 재화 목록 받기 — 운영 어드민(`Gatcha Log Admin/`)의 「재화 목록」이 쓰고 지출 추가 화면이 읽는다(10/13).
 *
 * 출처와 순서는 다른 운영 문서와 같다([LiveConfig]): Firestore `config/packages` →
 * raw `config/packages.json`. 둘 다 못 받으면 [PackageCatalog] 가 직전에 받은 값(디스크) → 앱 내장값으로 선다.
 *
 * **실패는 "그대로 둔다" 다.** 가격표를 못 받았다고 화면에 오류를 세울 이유가 없다.
 */
object PackageCatalogApi {

    private const val URL =
        "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/config/packages.json"

    /** 운영 어드민이 쓰는 라이브 문서 이름 — [LiveConfig] 참고. */
    private const val CONFIG_DOC = "packages"

    /** 가격표는 자주 바뀌지 않는다 — 지출 추가를 연달아 열어도 이 시간 안에는 다시 받지 않는다. */
    private const val FRESH_MS = 10 * 60_000L

    private var loadedAtMillis = 0L

    /**
     * 라이브 → 정본 순으로 받아 [PackageCatalog] 에 반영한다.
     * @return 목록이 실제로 바뀌었는가(화면은 [PackageCatalog.revision] 을 봐도 된다).
     */
    suspend fun load(force: Boolean = false): Boolean {
        if (!force && loadedAtMillis > 0L && currentTimeMillis() - loadedAtMillis < FRESH_MS) return false
        val body = LiveConfig.getOrRaw(CONFIG_DOC, URL, PackageCatalog::isReadable) ?: return false
        loadedAtMillis = currentTimeMillis()
        return PackageCatalog.apply(body)
    }
}
