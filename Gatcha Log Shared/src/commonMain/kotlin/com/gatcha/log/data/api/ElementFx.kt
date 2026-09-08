package com.gatcha.log.data.api

/**
 * 속성 연출 — 캐릭터 상세에 들어설 때 **한 번** 재생하는 짧은 효과.
 *
 * 처음엔 빛 번짐·파문 같은 **추상 패턴**으로 만들었는데, 그러면 "무슨 속성인지"가 안 읽혔다.
 * 지금은 속성마다 **형상**을 그린다 — 번개는 지그재그 볼트, 얼음은 서리 결정, 불은 불꽃 혀.
 *
 * 형상은 양 플랫폼이 **같은 알고리즘**으로 그린다(Compose `Canvas` · SwiftUI `Path`).
 * 좌표를 공유 모듈에 두지 않는 대신, 여기 정의한 패턴과 길이를 기준으로 맞춘다.
 *
 * 반복 재생하지 않는다. 매번 보는 화면이라 계속 움직이면 금방 거슬리고 배터리에도 좋지 않다.
 * 사용자가 끌 수도 있다([AppSettings.charElementFx]) — 꺼도 정적 테두리는 남긴다.
 */
enum class ElementFx {
    /** 번개·전기 — 지그재그 볼트가 순간 번쩍인다. */
    BOLT,

    /** 얼음 — 가장자리에서 서리 결정이 자라 들어온다. */
    FROST,

    /** 불·화염 — 아래에서 불꽃 혀가 피어오른다. */
    FLAME,

    /** 물 — 물방울이 떨어져 파문이 퍼진다. */
    DROP,

    /** 바람 — 소용돌이 결이 가로질러 흐른다. */
    SWIRL,

    /** 풀 — 잎이 흩날린다. */
    LEAF,

    /** 바위 — 초상이 바위로 덮이고 망치가 내리친다. */
    ROCK,

    /** 물리 — 교차 참격과 충격파. 바위(둔기)와 성격을 가른다. */
    IMPACT,

    /** 허수 — 환영처럼 겹쳐 도는 고리. */
    IMAGINARY,

    /** 에테르 — 공간이 갈라지고 그 틈으로 빛이 샌다. */
    ETHER,

    /** 루멘 — 광선속. 중심이 터지며 빛살이 뻗고, 잦아들며 잔광만 남는다. */
    LUMEN,

    /** 양자 — 확산 펄스와 잔입자. */
    PULSE,
}

/** 속성명 → 연출. 모르는 값은 [ElementFx.PULSE]. */
fun elementFx(element: String): ElementFx = when (element) {
    "번개", "전기" -> ElementFx.BOLT
    // 서리 — 미야비의 특수 속성. 얼음에서 갈라져 나왔으니 형상도 얼음을 쓴다.
    "얼음", "서리" -> ElementFx.FROST
    "불", "화염" -> ElementFx.FLAME
    "물" -> ElementFx.DROP
    "바람" -> ElementFx.SWIRL
    "풀" -> ElementFx.LEAF
    "바위" -> ElementFx.ROCK
    // 서슬 — 엽빛나의 특수 속성. 물리 계열이라 충격 형상을 같이 쓴다.
    "물리", "서슬" -> ElementFx.IMPACT
    "허수" -> ElementFx.IMAGINARY
    "에테르" -> ElementFx.ETHER
    // 루멘은 에테르 계열이 아니라 **빛의 양(광선속)** 을 뜻하는 단위다. 형상도 따로 간다.
    "루멘" -> ElementFx.LUMEN
    else -> ElementFx.PULSE
}

/** 연출 길이(ms). 형상마다 체감 속도가 달라 따로 준다. */
fun elementFxDurationMs(fx: ElementFx): Int = when (fx) {
    ElementFx.BOLT -> 1100
    ElementFx.FROST -> 2600
    ElementFx.FLAME -> 1400
    ElementFx.DROP -> 1600
    ElementFx.SWIRL -> 1500
    ElementFx.LEAF -> 1700
    ElementFx.ROCK -> 1600
    ElementFx.IMPACT -> 2400   // 3연타 + 붕괴까지 담아야 해서 길다
    ElementFx.IMAGINARY -> 1500
    ElementFx.ETHER -> 1500
    ElementFx.LUMEN -> 1800   // 터짐보다 **잦아드는 잔광**이 길어야 빛으로 읽힌다
    ElementFx.PULSE -> 1100
}
