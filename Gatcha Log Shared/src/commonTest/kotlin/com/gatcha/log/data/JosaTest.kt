package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 조사 선택 — 알림 본문이 "레진가 가득 찼어요" 로 나가던 것을 막는 자리.
 * 실제로 이 함수에 들어가는 말(재화명·게임 약칭)을 그대로 표본으로 쓴다.
 */
class JosaTest {

    @Test
    fun 재화명_주격조사() {
        assertEquals("레진이", Josa.subj("레진"))          // ㄴ 받침
        assertEquals("개척력이", Josa.subj("개척력"))       // ㄱ 받침
        assertEquals("배터리가", Josa.subj("배터리"))       // 받침 없음
    }

    @Test
    fun 게임약칭_접속조사() {
        assertEquals("원신과", Josa.with("원신"))
        assertEquals("스타레일과", Josa.with("스타레일"))
        assertEquals("젠레스와", Josa.with("젠레스"))
    }

    @Test
    fun 목적격_주제조사() {
        assertEquals("예산을", Josa.obj("예산"))
        assertEquals("한도를", Josa.obj("한도"))
        assertEquals("보상은", Josa.topic("보상"))
        assertEquals("시즌은", Josa.topic("시즌"))
    }

    @Test
    fun ㄹ받침은_으로가_아니라_로() {
        assertEquals("설정으로", Josa.to("설정"))
        assertEquals("서울로", Josa.to("서울"))
        assertEquals("메뉴로", Josa.to("메뉴"))
    }

    @Test
    fun 숫자는_읽는_소리를_따른다() {
        assertEquals("160이", Josa.subj("160"))   // 영
        assertEquals("12가", Josa.subj("12"))     // 이
        assertEquals("3이", Josa.subj("3"))       // 삼
    }

    @Test
    fun 빈_문자열은_그대로() {
        assertEquals("가", Josa.subj(""))
    }
}
