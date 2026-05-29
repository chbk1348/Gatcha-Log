package com.gatcha.log.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextSecondary

@Composable
internal fun UplogDialog(versionName: String, onDismiss: () -> Unit) {
    GlgDialog(
        title = "업데이트 로그",
        onDismiss = onDismiss,
        confirmText = "확인",
        onConfirm = onDismiss,
        dismissText = null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            UplogEntry(
                "v${versionName.ifBlank { "27.8.0" }}",
                listOf(
                    "앱 아이콘을 새 디자인(밤하늘 위 반짝이는 위시 스타)으로 단장했어요",
                    "로딩·로그인 화면 로고와 앱 색감을 새 아이콘 톤에 맞춰 정리했어요",
                    "모든 금액을 1,234원 형식(콤마+원)으로 통일해 읽기 쉽게",
                    "알림 배지가 한 번 확인하면 다시 뜨지 않도록 수정 — 앱을 다시 열어도 유지돼요",
                    "패치 일정에 버전 시작·종료 날짜를 함께 표시해 한눈에 알아보기 쉽게",
                ),
            )
            UplogEntry(
                "v27.7.1",
                listOf(
                    "지출 추가 화면이 + 버튼에서 펼쳐지듯 열리는 전환으로 더 부드러워졌어요",
                    "+ 버튼에 입체 그림자를 더해 또렷하게",
                    "내부 UI 프레임워크 최신화(Compose 1.9) — 더 부드러운 렌더링",
                ),
            )
            UplogEntry(
                "v27.7.0",
                listOf(
                    "HoYoLAB 토큰 만료를 자동 감지해 홈 상단에서 재연동을 안내 — 탭하면 연동 화면까지 바로 이동",
                    "천장 카운터에 임박 단계 강조(주의·임박·도달) + 단계 진입 시 토스트 안내",
                    "위시리스트를 전 게임으로 확장 + 위시 캐릭터가 픽업 배너에 뜨면 알림",
                    "자동 출석이 며칠 누락되던 문제 — 배터리 최적화 예외 요청으로 회복",
                    "지출 추가를 풀스크린 페이지로 개편 (FAB 위치에서 펼쳐지는 전환)",
                    "스타레일 가챠 효율 리포트에 콜라보 워프(콜라보 캐릭터·광추) 반영",
                ),
            )
            UplogEntry(
                "v27.6.0",
                listOf(
                    "혹시 자동 출석이 안 되고 계셨다면 — 자동 출석 토글을 한 번 껐다 다시 켜주세요 (알림 권한 안내가 떠요)",
                    "자동 출석 토글을 켜면 결과를 바로 알려드려요 — 완료·이미 완료·재연동 필요 등",
                    "자동 출석 실패 시 사유별 안내 — 재연동 필요·네트워크·기타로 구분",
                    "원신 창세의 결정·스타레일 오래된 꿈 아이콘을 정확한 이미지로 교체",
                    "알림 아이콘이 일부 단말(픽셀 등)에서 안 보이던 문제 수정",
                    "출처·저작권 안내에 명조(Kuro Games)·엔드필드(Hypergryph / Yostar) 권리자 추가",
                ),
            )
            UplogEntry(
                "v27.5.6",
                listOf(
                    "HoYoLAB 토큰을 단말기 내 암호화 저장소로 이관 — 클라우드 평문 보관 제거",
                    "비공식 앱 안내·토큰 로컬 보관 안내 등 면책 고지 보강",
                    "게임 재화 아이콘을 권리자 CDN에서 불러오도록 변경 — 앱 용량 축소",
                    "내부 안정성 보강(앱 실행 단계 회귀 방지)",
                ),
            )
            UplogEntry(
                "v27.5.3",
                listOf(
                    "설정 ▸ 앱 버전에 빌드 종류(DEBUG/RELEASE) 표시 칩 추가",
                ),
            )
            UplogEntry(
                "v27.5.2",
                listOf(
                    "연간 리포트·알림 상세 등 전체 화면 페이지에서 하단바와 + 버튼 자동 숨김",
                    "하단바가 차지하던 빈 여백 정리 — 콘텐츠를 더 넓게",
                ),
            )
            UplogEntry(
                "v27.5.1",
                listOf(
                    "[핫픽스] 리딤코드 교환 시 '쿠키 인증 필요' 오류 수정 — HoYoLAB 재연동(이메일 로그인) 후 정상 교환",
                ),
            )
            UplogEntry(
                "v27.5.0",
                listOf(
                    "HoYoLAB 로그인 한 번으로 토큰·게임 UID 자동 입력 (수동 복사 불필요)",
                    "HoYoLAB 리딤코드 자동 수집 + 한 번에 교환",
                    "HoYoLAB 계정 연동을 전용 페이지로 개편 (전환 애니메이션·연동 안내)",
                    "스타레일 등 UID가 부계정으로 잘못 채워지던 문제 수정 (대표 계정 우선)",
                ),
            )
            UplogEntry(
                "v27.4.2",
                listOf(
                    "[핫픽스] 지출 저장·삭제·수정 직후 당겨서 새로고침하면 변경이 사라지던 문제 수정",
                ),
            )
            UplogEntry(
                "v27.4.1",
                listOf(
                    "업데이트 확인이 잘 되지 않던 문제 수정 (버전 정보 캐시 우회)",
                    "지출 추가 모달 글씨 크기·가독성 개선",
                    "빠른 상품 선택에 분류 칩(월정액·패스·재화) 추가",
                    "명조·엔드필드·이환 정확한 가격 반영 + 게임별 재화 아이콘 (이환 신규 추가)",
                ),
            )
            UplogEntry(
                "v27.4.0",
                listOf(
                    "재설치·기기 변경 후 데이터 복원 안정화 — 같은 구글 계정으로 로그인하면 가챠 기록까지 복원",
                    "백업 파일 내보내기/복원 추가 (설정 ▸ 백업·복원) — 로그인 없이도 전체 데이터 보관",
                    "로그인 방식 개선 (Credential Manager) — 더 매끄러운 계정 선택",
                    "오프라인에서 앱이 로딩 화면에 멈추던 문제 수정",
                    "버전 호환성 개선으로 클라우드 데이터 보호 강화",
                ),
            )
            UplogEntry(
                "v27.3.1",
                listOf(
                    "[핫픽스] 화면이 짧은 단말에서 연동·예산 등 다이얼로그가 잘려 취소·저장 버튼이 안 보이던 문제 수정 (본문 스크롤)",
                ),
            )
            UplogEntry(
                "v27.3.0",
                listOf(
                    "지출 내역 실제 인게임 재화 아이콘 + 상세 버튼/페이지",
                    "HoYoLAB 연동 정보 구글 계정 동기화 안정화 (다른 기기 토큰 복원·자가 복구)",
                    "출석 기준 시간 베이징 표준시(UTC+8)로 정정 — 자정 직후 오출석 방지",
                    "출석 최근 7일 스트립 + 월간 달력",
                    "인앱 자동 업데이트 (다운로드→설치→자동 삭제)",
                    "HoYoLAB 리딤코드 교환, 젠레스 픽업 배너",
                    "데일리 인게임 용어·재화 명칭 정정 (선계 화폐 등)",
                    "하단 탭 다시 누르면 최상단 이동, 출처·저작권 고지",
                ),
            )
            UplogEntry(
                "v27.2.0",
                listOf(
                    "마이페이지 프로필 대시보드로 대개편",
                    "지출 상세 · 연간 리포트 · 알림 상세 페이지 추가",
                    "알림 읽음 처리 + 바로가기(액션형 알림)",
                    "프로필 쇼케이스 캐릭터 아이콘·원소 표시",
                    "화면 전환 애니메이션, 버튼·뒤로가기 디자인 통일",
                ),
            )
        }
    }
}

/** 출처·저작권 고지 — 비상업·비공식 팬 프로젝트, 게임 자료의 권리자 명시 + 권리자 요청 시 즉시 삭제. */
@Composable
internal fun CreditsDialog(onDismiss: () -> Unit) {
    GlgDialog(
        title = "출처 · 저작권",
        onDismiss = onDismiss,
        confirmText = "확인",
        onConfirm = onDismiss,
        dismissText = null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "본 앱은 개인이 만든 비상업·비공식 팬 프로젝트로 HoYoverse와 무관하며 공식 서비스가 아닙니다.",
                fontSize = 13.sp, color = TextSecondary,
            )
            CreditRow(
                "게임 콘텐츠 · 아이콘 저작권",
                "© HoYoverse (miHoYo / Cognosphere) — 원신 · 붕괴: 스타레일 · 젠레스 존 제로\n" +
                    "© Kuro Games — 명조: 워더링 웨이브\n" +
                    "© Hypergryph / Yostar — 명일방주: 엔드필드",
            )
            CreditRow(
                "데이터 · 에셋 출처",
                "enka.network · Project Amber (yatta.moe)\nHoYoLAB · ennead.cc",
            )
            Text(
                "모든 게임 콘텐츠의 권리는 각 권리자에게 있으며, 권리자의 요청이 있을 경우 즉시 해당 자료를 삭제합니다.",
                fontSize = 12.sp, color = TextSecondary,
            )
        }
    }
}

@Composable
private fun CreditRow(label: String, value: String) {
    val accent = LocalAccent.current
    Column {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun UplogEntry(version: String, items: List<String>) {
    val accent = LocalAccent.current
    Column {
        Text(version, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(6.dp))
        items.forEach {
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text("· ", fontSize = 13.sp, color = TextSecondary)
                Text(it, fontSize = 13.sp, color = TextSecondary)
            }
        }
    }
}
