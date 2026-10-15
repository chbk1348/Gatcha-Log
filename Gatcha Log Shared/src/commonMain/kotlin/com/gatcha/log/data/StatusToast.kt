package com.gatcha.log.data

/** 토스트의 상태 — 아이콘 원의 색과 그림이 이것으로 갈린다(GLDS 2.1 토스트 D안, 10/15). */
enum class ToastKind { SUCCESS, INFO, ERROR }

/**
 * 화면에 띄울 토스트 한 건 — 제목 한 줄과, 있으면 설명 한 줄.
 *
 * [id] 는 같은 문구가 연달아 와도 다시 띄우려고 둔다. StateFlow 는 같은 값을 흘려보내지 않아서, 문구만 들고 있으면
 * 「선택된 항목이 없어요」를 두 번 눌러도 두 번째는 뜨지 않았다.
 */
data class StatusToast(
    val id: Long,
    val title: String,
    val detail: String,
    val kind: ToastKind,
) {
    companion object {
        /**
         * 문구를 제목 · 설명으로 가른다 — 처음 나오는 「 — 」 앞이 제목, 뒤가 설명이다.
         * 앱 문구가 이미 「할 일 — 덧붙임」 꼴로 쓰여 있어서, 문구마다 둘로 나눠 적지 않고 이 규칙 하나로 맞춘다.
         */
        fun split(message: String): Pair<String, String> {
            val i = message.indexOf(SEPARATOR)
            if (i <= 0) return message.trim() to ""
            return message.substring(0, i).trim() to message.substring(i + SEPARATOR.length).trim()
        }

        private const val SEPARATOR = " — "
    }
}
