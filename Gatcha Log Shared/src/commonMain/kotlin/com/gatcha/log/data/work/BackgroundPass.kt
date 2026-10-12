package com.gatcha.log.data.work

import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.GatchaRepository

/**
 * **깨어난 뒤 도는 순서** — 한 곳에만 적는다(27.52.0).
 *
 * 앱이 화면 없이 깨어나는 길은 여럿이다: Android 주기 작업(GatchaWorker) · 자동 출석 알람 · 예약 알림 알람 · 재부팅,
 * iOS 백그라운드 작업(BGAppRefreshTask) · 앱 복귀 때의 보충 실행. 예전엔 그 여섯 곳이 「자동 출석 → 알림 점검 → 예약
 * 다시 걸기」를 저마다 적어 두어 서로 어긋났다 — iOS 만 자동 출석 토글을 보지 않았고, 자동 출석 알람은 예약을 고쳐
 * 걸지 않았으며, 단계마다 예외를 감싸는 범위도 달랐다.
 *
 * 이제 플랫폼은 **깨우는 일만** 맡고, 무엇을 어떤 순서로 할지는 여기가 정한다. 단계는 서로의 실패에 묶이지 않는다
 * (출석이 실패해도 알림 점검은 돌고, 점검이 실패해도 예약은 다시 걸린다).
 */
object BackgroundPass {

    /**
     * 주기 실행 한 번 — 자동 출석(토글이 켜져 있을 때만) → 알림 점검 → 예약 다시 걸기.
     *
     * 알림 점검을 예약보다 먼저 두는 이유: 점검이 실시간 노트를 새로 받아 캐시에 넣고, 예약은 그 캐시로
     * 「가득 차는 시각」을 계산한다. 출석을 맨 앞에 두는 이유: 뒤의 둘이 출석 결과(남은 출석)를 본다.
     *
     * **끝날 때까지 중단한다** — iOS 는 호출자가 완료 보고를 하는 순간 앱이 서스펜드되므로, 기다리지 않으면
     * 알림 · 예약이 등록되기 전에 프로세스가 멈춘다.
     */
    suspend fun run(
        settings: AppSettings = AppSettings(),
        repo: GatchaRepository = GatchaRepository(AppSettings.currentAccountId()),
    ) {
        val cfg = repo.loadHoyolab()
        if (settings.autoCheckIn) runCatching {
            AutoCheckInRunner.run(settings, repo, cfg, postFailureNotification = true)
        }
        runCatching { NotificationChecker.run(settings, repo, cfg) }
        runCatching { ScheduledAlerts.reschedule(settings, repo) }
    }

    /**
     * 자동 출석 알람이 깨웠을 때 — 출석만 하고, 출석이 바뀌었으니 예약(오늘 몫의 출석 리마인더)을 고쳐 건다.
     * 알림 점검은 돌리지 않는다(알람 수신부의 수명이 10초 남짓이라 네트워크 여러 건을 얹지 않는다).
     *
     * @return 출석 결과. 토글이 꺼져 있거나 연동이 없으면 null — 부르는 쪽이 재시도 여부를 여기서 가른다.
     */
    suspend fun checkInOnly(
        settings: AppSettings = AppSettings(),
        repo: GatchaRepository = GatchaRepository(AppSettings.currentAccountId()),
    ): AutoCheckInRunner.Outcome? {
        val outcome = if (!settings.autoCheckIn) null else runCatching {
            AutoCheckInRunner.run(settings, repo, repo.loadHoyolab(), postFailureNotification = true)
        }.getOrNull()
        runCatching { ScheduledAlerts.reschedule(settings, repo) }
        return outcome
    }

    /** 예약만 다시 건다 — 알람이 울린 직후 · 재부팅 뒤(알람은 한 번 울리거나 재부팅하면 사라진다). */
    suspend fun rescheduleOnly(
        settings: AppSettings = AppSettings(),
        repo: GatchaRepository = GatchaRepository(AppSettings.currentAccountId()),
    ) {
        runCatching { ScheduledAlerts.reschedule(settings, repo) }
    }
}
