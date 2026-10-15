package com.gatcha.log.data.api

import kotlinx.serialization.Serializable

// [EnkaApi.cleanName] 이 쓰는 정규식 — 파일 레벨에서 한 번만 컴파일한다.
// 예전엔 함수 안에 있어서, 메타 맵을 만들 때 **항목 수만큼**(수백~수천) 두 개를 다시 컴파일했다.

// 내 캐릭터(로스터 · 스탯) 모델 — 세 게임이 같은 모양으로 맞춰 쓴다. 조회와 파싱은 `EnkaApi.kt` · `Enka*Parse.kt`.
// 1,200줄짜리 `EnkaApi.kt` 를 모델 · 조회 · 게임별 파서로 나눴다(27.52.0) — 옮기기만 했고 부르는 쪽은 그대로다.

/** 쇼케이스 캐릭터 (id, 한글명, 레벨, 명좌/성혼 rank, 희귀도, 아이콘 URL, 한글 원소) */
@Serializable
data class EnkaChar(
    val id: Int,
    val name: String,
    val level: Int,
    val rank: Int,
    val rarity: Int,
    val iconUrl: String? = null,
    val element: String = "",
    /** 스타레일 운명의 길(파멸·수렵 등). 다른 게임은 빈 문자열. */
    val path: String = "",
    /** 젠레스 직업(강공·격파·이상·지원·방어). 다른 게임은 빈 문자열. 주요 스탯 강조에 사용. */
    val specialty: String = "",
    /** 캐릭터 상세 공개 시에만 채워짐(풀 스탯시트). 비공개면 false → 로스터만 표시. */
    val detailed: Boolean = false,
    val stats: List<EnkaStatLine> = emptyList(),
    val weapon: EnkaWeapon? = null,
    val artifacts: List<EnkaArtifact> = emptyList(),
    val sets: List<EnkaSet> = emptyList(),
    /**
     * 소속·진영. 지금은 젠레스만 채운다(`camp_name_mi18n`) — 공허 사냥꾼 같은 진영 배지에 쓴다.
     * 원신·스타레일 응답에는 대응 필드가 없어 빈 문자열이다.
     */
    val camp: String = "",
)

/** 스탯 한 줄(라벨+표시값). [crit]=치명타 계열(UI 강조). */
@Serializable
data class EnkaStatLine(val label: String, val value: String, val crit: Boolean = false)

/** 무기/광추. */
@Serializable
data class EnkaWeapon(
    val name: String,
    val level: Int,
    val refinement: Int,
    val main: EnkaStatLine?,
    val sub: EnkaStatLine?,
    /**
     * 게임 내부 id — 정련 효과 설명을 도감([NanokaApi])에서 찾을 때 쓴다. 0 = 응답에 없음.
     *
     * 젠레스는 채우지 않는다. 상류 한국어 W-엔진 데이터가 미번역 자리표시자(`Item_Weapon_..._Name`)로
     * 오는 게 흔해서, id 가 있어도 보여줄 문장이 없다.
     */
    val id: Int = 0,
    /**
     * 무기/광추/W-엔진 아이콘 URL. 응답에 없으면 null(슬롯 배지로 대체).
     *
     * 게임마다 출처가 다르다 — 원신은 Enka UI(`flat.icon` → `enka.network/ui/…`),
     * 스타레일은 StarRailRes(`light_cone.icon`), 젠레스·HoYoLAB 경로는 절대 URL 이 그대로 온다.
     * 셋 다 응답에는 있었는데 여태 읽지 않고 버렸다.
     */
    val iconUrl: String? = null,
    /**
     * 무기 특성(정련 효과) — **응답이 직접 주는 것.** 지금은 젠레스 W-엔진뿐이다.
     *
     * 도감([NanokaApi])은 원신·스타레일만 지원해서 젠레스 엔진 특성이 영영 안 떴는데,
     * HoYoLAB 응답이 `talent_title`/`talent_content` 로 그대로 준다.
     *
     * ⚠️ 원신·스타레일의 `desc` 는 여기 넣지 않는다 — 그건 **무기 소개문(플레이버)** 이라
     * 특성 자리에 두면 효과 설명과 배경 이야기가 나란히 뜬다.
     */
    val traitName: String = "",
    val traitDesc: String = "",
)

/** 성유물/유물 슬롯 1개. setName 은 v1 미해결 시 빈 문자열. */
@Serializable
data class EnkaArtifact(
    val slot: String,
    val setName: String,
    val level: Int,
    val main: EnkaStatLine,
    val subs: List<EnkaStatLine>,
    /** 성유물/유물/디스크 실제 인게임 아이콘 URL. 응답에 없으면 null(슬롯 배지로 대체). */
    val iconUrl: String? = null,
)

/** 세트 보너스 1줄. [pieces]=발동 조각수(2/4), [text]=효과 설명(접두 제거), [active]=현재 장착 수로 발동 여부. */
@Serializable
data class EnkaSetEffect(val pieces: Int, val text: String, val active: Boolean)

/**
 * 세트 효과(성유물/유물/장신구/드라이브 디스크).
 * [count]=장착 수, [effects]=조각수별 보너스(미발동 tier 포함), [kind]=종류 라벨(HSR "유물"/"장신구", 그 외 "").
 */
@Serializable
data class EnkaSet(
    val name: String,
    val count: Int,
    val effects: List<EnkaSetEffect> = emptyList(),
    val kind: String = "",
)

/** Yatta 아바타 메타(한글명·희귀도·아이콘 URL·한글 원소). id 매핑용 캐시 값. */
internal data class AvatarMeta(val name: String, val rarity: Int, val iconUrl: String, val element: String)

/** Enka 프로필 (닉네임/모험/세계 레벨/서명 + 쇼케이스 캐릭터) */
@Serializable
data class EnkaProfile(
    val nickname: String,
    val level: Int,
    val worldLevel: Int,
    val signature: String,
    val chars: List<EnkaChar>,
)

@Serializable
data class EnkaResult(
    val profile: EnkaProfile?,
    val error: String?,
    /**
     * **덜 받은 결과**다(27.52.0) — 연동된 계정인데 HoYoLAB 보유 목록을 못 받아 쇼케이스만 왔거나, 일부 캐릭터의
     * 상세가 빠졌다. 이런 결과로 이미 가진 목록을 통째로 갈면 80명이 8명으로 줄어 캐시까지 덮였다.
     * 받는 쪽은 [mergePartialRoster] 로 가진 목록 위에 얹고, 곧 다시 받는다.
     */
    val partial: Boolean = false,
)

/**
 * 덜 받은 결과([EnkaResult.partial])를 **이미 가진 목록 위에 얹는다.** 새로 온 캐릭터는 새 값으로 갈되, 상세가 빠진 채
 * 온 캐릭터는 가진 상세를 지키고, 이번에 안 온 캐릭터는 그대로 둔다. 온전한 결과면 [r] 그대로다.
 */
internal fun mergePartialRoster(base: EnkaResult?, r: EnkaResult): EnkaResult {
    val fresh = r.profile ?: return r
    val old = base?.profile?.chars.orEmpty()
    if (!r.partial || old.isEmpty()) return r
    val oldIds = old.mapTo(HashSet()) { it.id }
    val freshById = fresh.chars.associateBy { it.id }
    val merged = old.map { o -> freshById[o.id]?.let { n -> if (!n.detailed && o.detailed) o else n } ?: o } +
        fresh.chars.filter { it.id !in oldIds }
    return r.copy(profile = fresh.copy(chars = merged))
}
