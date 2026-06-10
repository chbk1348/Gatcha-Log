package com.gatcha.log.util

import kotlin.test.Test
import kotlin.test.assertEquals

/** RFC 1321 표준 테스트 벡터 — JVM MessageDigest("MD5") 출력과 동일함을 보장 */
class Md5Test {
    @Test
    fun rfc1321TestVectors() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5Hex(""))
        assertEquals("0cc175b9c0f1b6a831c399e269772661", md5Hex("a"))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", md5Hex("abc"))
        assertEquals("f96b697d7cb7938d525a2f31aaf161d0", md5Hex("message digest"))
        assertEquals("c3fcd3d76192e4007dfb496cca67e13b", md5Hex("abcdefghijklmnopqrstuvwxyz"))
        assertEquals(
            "57edf4a22be3c955ac49da2e2107b67a",
            md5Hex("12345678901234567890123456789012345678901234567890123456789012345678901234567890"),
        )
    }

    @Test
    fun hoyolabDsStyleInput() {
        // DS 토큰 형식의 입력(salt&t&r&b&q)도 일관된 32자 hex 를 생성하는지
        val hash = md5Hex("salt=okr4obncj8bw5a65hbnn5oo6ixjc3l9w&t=1748736000&r=123456&b=&q=role_id=800000000&server=os_asia")
        assertEquals(32, hash.length)
        assertEquals(hash, md5Hex("salt=okr4obncj8bw5a65hbnn5oo6ixjc3l9w&t=1748736000&r=123456&b=&q=role_id=800000000&server=os_asia"))
    }
}
