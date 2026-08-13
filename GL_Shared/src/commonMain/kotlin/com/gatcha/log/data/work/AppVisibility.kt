package com.gatcha.log.data.work

/**
 * 앱이 지금 화면에 떠 있는가 — **알림을 쏠지 말지**를 가르는 단일 판정.
 *
 * 알림이 하는 일은 "안 보고 있을 때 알려주는 것"이다. 앱을 보고 있는 사람에게 앱 안에 이미
 * 떠 있는 내용을 알림으로 또 보내면 그건 알림이 아니라 소음이다.
 *
 * 이게 필요해진 경위: 백그라운드 실행이 못 미더워서([NativeScheduler] 참고 — iOS BGTask 는
 * 실행 시점이 OS 재량이고 Android 도 Doze 에서 늦는다) **앱을 여는 순간을 보조 트리거로** 쓴다.
 * 그런데 그 점검이 다른 트리거와 완전히 같은 코드라, 앱을 여는 순간 밀려 있던 공지 알림이
 * 게임 수만큼 한꺼번에 떴다. 트리거를 없앨 수는 없다(그러면 iOS 는 알림이 아예 안 온다).
 * 대신 **쏘는 쪽에서** 지금 사람이 보고 있는지를 보고 판단한다.
 *
 * 트리거 인자로 넘기지 않고 여기 두는 이유: Android 는 점검이 WorkManager 로 넘어가 나중에
 * 다른 문맥에서 도는데, 그때 앱이 이미 내려가 있으면 **쏘는 게 맞다.** 판정 시점은 '점검을
 * 요청한 때'가 아니라 '알림을 쏘려는 때'여야 한다.
 *
 * 프로세스가 죽으면 기본값(false)으로 돌아간다 — 앱이 없으니 알림을 쏘는 게 맞고, 정확하다.
 */
object AppVisibility {

    /** 화면에 떠 있으면 true. 갱신은 [onForeground]·[onBackground] 로만. */
    var isForeground: Boolean = false
        private set

    fun onForeground() { isForeground = true }

    fun onBackground() { isForeground = false }
}
