package com.gatcha.log.data

/**
 * 출처·저작권 고지 정본 — Android(Compose)·iOS(SwiftUI) 공용 단일 소스.
 *
 * 예전엔 같은 문구가 `SettingsDialogs.kt` 와 `SettingsDialogs.swift` 에 따로 박혀 있었다.
 * 새 출처를 붙일 때마다 두 곳을 고쳐야 했고, 실제로 명조·엔드필드 공지를 연동한 뒤
 * **어느 쪽에도 그 출처가 적히지 않은 채** 배포될 뻔했다. 데이터가 한 곳이면 그런 누락이 없다.
 *
 * ⚠️ 새 API·에셋 소스를 붙이면 [dataSources] 에 **반드시** 한 줄 추가한다. 이 앱은 남의
 * 저작물을 빌려 쓰는 팬 프로젝트라, 어디서 받아 무엇에 쓰는지가 고지의 핵심이다.
 */
data class CreditSection(val label: String, val body: String)

object Credits {

    /** 맨 위 한 문단 — 이 앱이 무엇이 아닌지부터 밝힌다. */
    const val disclaimer: String =
        "개인이 만든 비상업·비공식 팬 프로젝트입니다. " +
            "HoYoverse · Kuro Games · Hypergryph · Hotta Studio 를 비롯한 어떤 게임사와도 " +
            "제휴·후원·검수 관계가 없으며, 공식 서비스가 아닙니다."

    /** 게임별 권리자. 앱이 다루는 게임([Game])과 같이 움직인다. */
    val gameRights = CreditSection(
        "게임 콘텐츠 · 상표",
        "© HoYoverse (miHoYo / COGNOSPHERE)\n" +
            "    원신 · 붕괴: 스타레일 · 젠레스 존 제로\n" +
            "© Kuro Games — 명조: 워더링 웨이브\n" +
            "© Hypergryph / Gryphline — 명일방주: 엔드필드\n" +
            "© Hotta Studio / Perfect World Games — 이환\n\n" +
            "게임명 · 캐릭터명 · 아이콘 · 이미지는 각 권리자의 상표이자 저작물입니다.",
    )

    /**
     * 데이터·에셋 출처 — **무엇에 쓰는지까지** 적는다.
     *
     * 출처만 나열하면 고지가 아니라 목록이다. 어떤 화면의 어떤 값이 남의 데이터인지 보이도록
     * 용도를 붙인다.
     */
    val dataSources = CreditSection(
        "데이터 · 에셋 출처",
        "HoYoLAB — 출석 · 전투 진행도 · 수입 일지 · 공지 본문\n" +
            "ennead.cc — 호요버스 3게임 공지 · 픽업 배너\n" +
            "Kuro Games 공식 공지 CDN — 명조 공지\n" +
            "ak-endfield-api-archive — 엔드필드 공지 (커뮤니티 아카이브)\n" +
            "Enka.Network · mihomo.me — 보유 캐릭터 · 장비 정보\n" +
            "Project Amber (yatta.moe) · nanoka.cc\n" +
            "    — 캐릭터 · 아이템 아이콘, 돌파 효과 설명\n" +
            "akasha.cv — 원신 유물 평가(CV) 기준\n" +
            "hoyo-codes (seria.moe) — 선물코드 목록",
    )

    /** 번들한 서드파티 자산 — 폰트는 재배포 조건이 붙는 저작물이라 라이선스까지 밝힌다. */
    val bundledAssets = CreditSection(
        "글꼴",
        "Pretendard © Kil Hyung-jin\n" +
            "SIL Open Font License 1.1 에 따라 사용 · 재배포합니다.",
    )

    /** 맨 아래 한 문단 — 권리 주장 없음과 삭제 약속. */
    const val notice: String =
        "이 앱이 표시하는 게임 자료의 권리는 전부 각 권리자에게 있으며, 이 앱은 그에 대한 어떠한 " +
            "권리도 주장하지 않습니다. 권리자의 요청이 있을 경우 해당 자료를 즉시 삭제합니다."

    /** 화면이 순서대로 그리기만 하면 되도록 묶어 둔다. */
    val sections: List<CreditSection> = listOf(gameRights, dataSources, bundledAssets)
}
