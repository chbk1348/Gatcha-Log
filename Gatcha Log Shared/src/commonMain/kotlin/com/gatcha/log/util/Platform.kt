package com.gatcha.log.util

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * JVM 전용 API 의 KMP 대체 헬퍼.
 * :app 코드 복사 시 다음 치환만 하면 된다:
 *   System.currentTimeMillis()      → currentTimeMillis()
 *   UUID.randomUUID().toString()    → randomUuid()
 */

@OptIn(ExperimentalTime::class)
fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

@OptIn(ExperimentalUuidApi::class)
fun randomUuid(): String = Uuid.random().toString()
