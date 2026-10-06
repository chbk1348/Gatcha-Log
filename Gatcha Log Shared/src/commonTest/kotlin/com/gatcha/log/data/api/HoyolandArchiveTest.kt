package com.gatcha.log.data.api

import com.gatcha.log.data.HoyolandArchives
import com.gatcha.log.data.HoyolandDefaults
import com.gatcha.log.data.HoyolandPastEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/** 지난 회차 보관본 — 「지난 행사」의 어느 줄이 상세로 이어지는지 가르는 규칙을 고정한다. */
class HoyolandArchiveTest {

    private fun past(title: String) = HoyolandPastEvent(title, emptyList())

    @Test
    fun `회차 키는 제목의 연도다`() {
        assertEquals("2026", past("호요랜드 2026").editionYear)
        assertEquals("2024", past("호요랜드 2024 (첫 개최)").editionYear)
        assertEquals("", past("호요랜드").editionYear)
    }

    @Test
    fun `목록 문서에서 보관된 회차만 읽는다`() {
        val body = """{"published":"2027","editions":["2026","2027"],"archived":["2026"]}"""
        assertEquals(listOf("2026"), HoyolandApi.parseArchiveIndex(body))
    }

    /** 키는 문서 이름 · 파일 경로에 그대로 들어간다 — 연도 꼴이 아니면 버린다. */
    @Test
    fun `연도 꼴이 아닌 키는 버린다`() {
        val body = """{"archived":["2026","../x","","2026","26","2025 "]}"""
        assertEquals(listOf("2026", "2025"), HoyolandApi.parseArchiveIndex(body))
    }

    @Test
    fun `목록을 못 읽으면 null — 직전 값을 그대로 쓴다`() {
        assertEquals(null, HoyolandApi.parseArchiveIndex("{깨진 JSON"))
        assertEquals(null, HoyolandApi.parseArchiveIndex("""{"published":"2027"}"""))
    }

    /** 원격 목록을 받기 전에도 2026(어드민 보관본) · 2025 · 2024(앱 내장)는 상세로 이어진다. 그 밖의 회차는 아니다. */
    @Test
    fun `내장값의 지난 행사는 모두 상세 키가 선다`() {
        val keys = HoyolandDefaults.event.past.map { HoyolandApi.archiveKeyOf(it) }
        assertEquals(listOf("2026", "2025", "2024"), keys)
        assertEquals("", HoyolandApi.archiveKeyOf(past("호요랜드 2023")))
    }

    /** 앱에 내장한 회차 — 날짜가 지나 종료 상태로 서고, 화면이 그릴 재료(라인업 · 프로그램)가 있다. */
    @Test
    fun `내장 회차는 종료 상태이고 라인업 다섯 게임을 갖는다`() {
        HoyolandArchives.bundled.forEach { (key, e) ->
            assertEquals(key, e.startYmd.take(4))
            assertEquals(true, e.phase().isOffSeason, "$key 가 종료 상태가 아니다")
            assertEquals(5, e.lineup.size)
            assertEquals(true, e.otherPrograms.isNotEmpty())
            // 굿즈 · 부스 · 푸드 · 배치도는 확인된 자료가 없어 비웠다 — 화면이 빈 칸을 세우지 않는다.
            assertEquals(true, e.goods.isEmpty() && e.booths.isEmpty() && e.foodPrograms.isEmpty() && !e.hasMap)
        }
        // 2024 는 일자별 무대 편성이 있다(나흘 · 하루 네 편).
        val y2024 = HoyolandArchives.bundled.getValue("2024")
        assertEquals(listOf(4, 4, 4, 4), y2024.days.map { it.slots.size })
        // 시간표 화면이 쓰는 값 — 날짜 탭 넷, 날마다 무대 네 편(끝난 행사라 전부 지난 편).
        assertEquals(listOf("2024-10-31", "2024-11-01", "2024-11-02", "2024-11-03"), y2024.dayYmds)
        assertEquals(listOf(4, 4, 4, 4), y2024.dayYmds.map { y2024.stageSlots(it).size })
        assertEquals("4일 · 16편", y2024.onsiteStageLine())
        assertEquals(false, HoyolandArchives.bundled.getValue("2025").hasTimetable)
    }

    /** 지난 회차 화면의 공통 손질 — 제목 「행사 구성」, 게임 배지 없음, 줄 순서는 문서(어드민) 그대로. */
    @Test
    fun `지난 회차는 행사 구성 제목에 문서 순서대로 선다`() {
        HoyolandArchives.bundled.values.map { HoyolandApi.asArchive(it) }.forEach { e ->
            assertEquals("행사 구성", e.programSectionTitle)
            assertEquals(false, e.programGameTags)
            assertEquals(listOf("원신", "붕괴: 스타레일", "젠레스 존 제로", "붕괴3rd", "미해결사건부"), e.otherPrograms.take(5).map { it.title })
        }
        // 어드민 보관본(2026) 꼴 — 게임 없는 줄이 앞에 있으면 앱에서도 앞이다. 순서는 어드민이 정한다.
        val y2025 = HoyolandArchives.bundled.getValue("2025")
        val mixed = y2025.copy(programs = listOf(
            com.gatcha.log.data.HoyolandProgram("2차 창작물 전시존", ""),
            com.gatcha.log.data.HoyolandProgram("웰컴 키트 — 공통", ""),
            com.gatcha.log.data.HoyolandProgram("웰컴 키트 — 붕괴: 스타레일", ""),
            com.gatcha.log.data.HoyolandProgram("웰컴 키트 — 원신", ""),
        ))
        assertEquals(
            listOf("2차 창작물 전시존", "웰컴 키트 — 공통", "웰컴 키트 — 붕괴: 스타레일", "웰컴 키트 — 원신"),
            HoyolandApi.asArchive(mixed).programs.map { it.title },
        )
        // 굿즈 칸 — 지난 회차는 담을 수 없으니 종수만, 지금 회차는 예전 문구 그대로.
        val goods = listOf(com.gatcha.log.data.HoyolandGoods(name = "키링", price = 9000))
        val empty = com.gatcha.log.data.HoyolandCart()
        assertEquals("1종", HoyolandApi.asArchive(y2025.copy(goods = goods)).onsiteGoodsLine(empty))
        assertEquals("1종 · 아직 안 담았어요", y2025.copy(goods = goods).onsiteGoodsLine(empty))
        // 지금 회차는 그대로다.
        assertEquals("프로그램", HoyolandDefaults.event.programSectionTitle)
        assertEquals(true, HoyolandDefaults.event.programGameTags)
    }

    /** 여러 줄 글의 목록 규칙 — 어드민에서 친 `- ` · `* ` · `+ ` 가 앱에서도 「· 항목」 목록으로 선다. */
    @Test
    fun `마크다운 글머리는 가운뎃점 목록으로 읽는다`() {
        assertEquals("· 하나\n· 둘\n· 셋", com.gatcha.log.data.HoyolandText.normalize("- 하나\n* 둘\n+ 셋"))
        // 들여쓰기는 남고, 줄 가운데의 것 · 띄어쓰기 없는 것은 안 건드린다.
        assertEquals("· 키트\n   · 성옥 100", com.gatcha.log.data.HoyolandText.normalize("- 키트\n   - 성옥 100"))
        assertEquals("10:00 - 11:00\n-5도\n*주의", com.gatcha.log.data.HoyolandText.normalize("10:00 - 11:00\n-5도\n*주의"))

        // 문서에서 읽을 때 걸린다.
        val e = HoyolandApi.parseOrNull(
            """{"startYmd":"2026-10-02","endYmd":"2026-10-05","programs":[{"title":"웰컴 키트","desc":"구성품\n- 부직포백\n* 가이드북"}],"booths":[{"title":"에코백","desc":"+ 스티커 택1","reward":"- 에코백 1개"}]}""",
        )!!
        assertEquals("구성품\n· 부직포백\n· 가이드북", e.programs.single().desc)
        assertEquals("· 스티커 택1", e.booths.single().desc)
        assertEquals("· 에코백 1개", e.booths.single().reward)
    }

    /** 푸드존 줄은 제목 머리나 메뉴 사진으로 알아본다 — 제목 머리가 빠진 문서에서도 메뉴판이 프로그램 섹션에 서지 않는다. */
    @Test
    fun `메뉴 사진이 걸린 줄은 제목 머리가 없어도 푸드존이다`() {
        val e = HoyolandApi.parseOrNull(
            """{"startYmd":"2026-10-02","endYmd":"2026-10-05","programs":[
              {"title":"웰컴 키트 — 원신","desc":"· 리딤코드"},
              {"title":"푸드존 — 원신","desc":"· 닭구이 — 10,000원"},
              {"title":"붕괴: 스타레일","desc":"· 만두 — 7,000원","menuImages":{"만두":"food/2026/hsr-01.webp"}},
              {"title":"젠레스 존 제로","desc":"· 코스 A — 12,000원","menuImages":{}}
            ]}""",
        )!!
        assertEquals(listOf("푸드존 — 원신", "붕괴: 스타레일"), e.foodPrograms.map { it.title })
        assertEquals(listOf("젠레스 존 제로"), e.otherPrograms.map { it.title })
        assertEquals(listOf("웰컴 키트 — 원신"), e.perkPrograms.map { it.title })
    }

    @Test
    fun `여러 줄 글을 문단 · 목록 줄 · 부연으로 가른다`() {
        val lines = com.gatcha.log.data.HoyolandText.lines(
            "\n구성품은 둘입니다.\n고를 수 있어요.\n· 리딤코드\n   · 성옥 100\n   계정당 4회\n· 비치타올\n\n재료가 떨어지면 끝납니다.\n",
        )
        assertEquals(
            listOf(
                Triple(com.gatcha.log.data.HoyolandTextKind.PARA, "구성품은 둘입니다.\n고를 수 있어요.", 0),
                Triple(com.gatcha.log.data.HoyolandTextKind.ITEM, "리딤코드", 0),
                Triple(com.gatcha.log.data.HoyolandTextKind.ITEM, "성옥 100", 1),
                Triple(com.gatcha.log.data.HoyolandTextKind.SUB, "계정당 4회", 1),
                Triple(com.gatcha.log.data.HoyolandTextKind.ITEM, "비치타올", 0),
                Triple(com.gatcha.log.data.HoyolandTextKind.BLANK, "", 0),
                Triple(com.gatcha.log.data.HoyolandTextKind.PARA, "재료가 떨어지면 끝납니다.", 0),
            ),
            lines.map { Triple(it.kind, it.text, it.level) },
        )
        // 깊이 — 띄어쓰기 셋(또는 탭 하나)이 한 단이고, 단마다 점이 다르다(어드민 글 칸과 같다).
        val T = com.gatcha.log.data.HoyolandText
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 1, 2), listOf("· a", " · a", "  · a", "   · a", "    · a", "      · a", "       · a", "\t· a", "\t\t· a").map { T.depth(it) })
        assertEquals(listOf("•", "◦", "▪", "•"), (0..3).map { T.dot(it) })
        assertEquals(
            listOf(0, 1, 2),
            T.lines("· 하나\n   · 둘\n      · 셋").map { it.level },
        )
        // 번호 줄 — 어드민 글 칸의 「1. 」 목록. 날짜로 시작하는 줄은 번호가 아니다.
        assertEquals(
            listOf(Triple(com.gatcha.log.data.HoyolandTextKind.NUM, "1.", "티켓링크에서 검색"), Triple(com.gatcha.log.data.HoyolandTextKind.NUM, "2.", "날짜 고르기"), Triple(com.gatcha.log.data.HoyolandTextKind.SUB, "", "평일만 열려요"), Triple(com.gatcha.log.data.HoyolandTextKind.ITEM, "", "하위 항목")),
            T.lines("1. 티켓링크에서 검색\n2. 날짜 고르기\n   평일만 열려요\n   · 하위 항목").map { Triple(it.kind, it.mark, it.text) },
        )
        assertEquals(true, T.hasList("1. 하나\n2. 둘"))
        assertEquals(listOf(com.gatcha.log.data.HoyolandTextKind.PARA), T.lines("2026. 10. 2. 개막").map { it.kind })
        // 화면용 점이 값에 섞여 들어와도 목록으로 읽는다.
        assertEquals("· 하나\n   · 둘\n      · 셋\n   · 넷", T.normalize("• 하나\n   ∘ 둘\n      ▪ 셋\n   ◦ 넷"))
        // 목록이 아닌 자리의 들여쓴 줄은 문단 그대로다.
        assertEquals(listOf(com.gatcha.log.data.HoyolandTextKind.PARA), com.gatcha.log.data.HoyolandText.lines("첫 줄\n   들여쓴 줄").map { it.kind })
        assertEquals(true, com.gatcha.log.data.HoyolandText.hasList("안내\n· 하나"))
        assertEquals(false, com.gatcha.log.data.HoyolandText.hasList("A · B조 — 오전 10시"))
    }
}
