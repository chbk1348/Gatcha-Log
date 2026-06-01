package com.gatcha.log.util

/**
 * 순수 Kotlin MD5 (RFC 1321) — HoYoLAB DS 토큰 서명용.
 * :app 은 java.security.MessageDigest 를 쓰지만 KMP common 엔 없어서 직접 구현.
 * (보안 용도가 아니라 API 서명 호환용이므로 MD5 사용이 적절함)
 */
fun md5Hex(input: String): String {
    val msg = input.encodeToByteArray()

    // 패딩
    val origLenBits = msg.size.toLong() * 8
    val padded = msg + byteArrayOf(0x80.toByte()) +
        ByteArray(((56 - (msg.size + 1) % 64) + 64) % 64) +
        ByteArray(8) { i -> ((origLenBits ushr (8 * i)) and 0xFF).toByte() }

    // 초기 상태
    var a0 = 0x67452301.toInt()
    var b0 = 0xefcdab89.toInt()
    var c0 = 0x98badcfe.toInt()
    var d0 = 0x10325476.toInt()

    val s = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )
    val k = intArrayOf(
        0xd76aa478.toInt(), 0xe8c7b756.toInt(), 0x242070db, 0xc1bdceee.toInt(),
        0xf57c0faf.toInt(), 0x4787c62a, 0xa8304613.toInt(), 0xfd469501.toInt(),
        0x698098d8, 0x8b44f7af.toInt(), 0xffff5bb1.toInt(), 0x895cd7be.toInt(),
        0x6b901122, 0xfd987193.toInt(), 0xa679438e.toInt(), 0x49b40821,
        0xf61e2562.toInt(), 0xc040b340.toInt(), 0x265e5a51, 0xe9b6c7aa.toInt(),
        0xd62f105d.toInt(), 0x02441453, 0xd8a1e681.toInt(), 0xe7d3fbc8.toInt(),
        0x21e1cde6, 0xc33707d6.toInt(), 0xf4d50d87.toInt(), 0x455a14ed,
        0xa9e3e905.toInt(), 0xfcefa3f8.toInt(), 0x676f02d9, 0x8d2a4c8a.toInt(),
        0xfffa3942.toInt(), 0x8771f681.toInt(), 0x6d9d6122, 0xfde5380c.toInt(),
        0xa4beea44.toInt(), 0x4bdecfa9, 0xf6bb4b60.toInt(), 0xbebfbc70.toInt(),
        0x289b7ec6, 0xeaa127fa.toInt(), 0xd4ef3085.toInt(), 0x04881d05,
        0xd9d4d039.toInt(), 0xe6db99e5.toInt(), 0x1fa27cf8, 0xc4ac5665.toInt(),
        0xf4292244.toInt(), 0x432aff97, 0xab9423a7.toInt(), 0xfc93a039.toInt(),
        0x655b59c3, 0x8f0ccc92.toInt(), 0xffeff47d.toInt(), 0x85845dd1.toInt(),
        0x6fa87e4f, 0xfe2ce6e0.toInt(), 0xa3014314.toInt(), 0x4e0811a1,
        0xf7537e82.toInt(), 0xbd3af235.toInt(), 0x2ad7d2bb, 0xeb86d391.toInt(),
    )

    // 64바이트 블록 처리
    for (chunk in 0 until padded.size / 64) {
        val m = IntArray(16) { j ->
            val base = chunk * 64 + j * 4
            (padded[base].toInt() and 0xFF) or
                ((padded[base + 1].toInt() and 0xFF) shl 8) or
                ((padded[base + 2].toInt() and 0xFF) shl 16) or
                ((padded[base + 3].toInt() and 0xFF) shl 24)
        }

        var a = a0; var b = b0; var c = c0; var d = d0
        for (i in 0 until 64) {
            val (f, g) = when {
                i < 16 -> ((b and c) or (b.inv() and d)) to i
                i < 32 -> ((d and b) or (d.inv() and c)) to (5 * i + 1) % 16
                i < 48 -> (b xor c xor d) to (3 * i + 5) % 16
                else -> (c xor (b or d.inv())) to (7 * i) % 16
            }
            val tmp = d
            d = c
            c = b
            val sum = a + f + k[i] + m[g]
            b += (sum shl s[i]) or (sum ushr (32 - s[i]))
            a = tmp
        }
        a0 += a; b0 += b; c0 += c; d0 += d
    }

    // 리틀엔디언 → hex
    return byteArrayOf(
        *intToLeBytes(a0), *intToLeBytes(b0), *intToLeBytes(c0), *intToLeBytes(d0),
    ).joinToString("") { byte ->
        val v = byte.toInt() and 0xFF
        "${HEX[v ushr 4]}${HEX[v and 0x0F]}"
    }
}

private const val HEX = "0123456789abcdef"

private fun intToLeBytes(v: Int): ByteArray =
    byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())
