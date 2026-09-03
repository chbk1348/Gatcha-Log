package com.gatcha.log.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * org.json 호환 레이어 (KMP).
 *
 * :app 의 코드는 Android 프레임워크에 내장된 org.json(JSONObject/JSONArray)을 쓰는데,
 * 이는 iOS 에 존재하지 않는다. 이 파일은 kotlinx.serialization 위에 동일한 API 표면을 제공해서
 * :app 코드를 복사할 때 import 한 줄만 바꾸면 되도록 한다:
 *
 *   import org.json.JSONObject  →  import com.gatcha.log.json.JSONObject
 *
 * 메서드 시그니처와 기본값 동작(opt* 계열)은 org.json 과 동일하게 맞춘다.
 */

class JSONException(message: String) : Exception(message)

private val lenientJson = Json { isLenient = true; ignoreUnknownKeys = true }

private fun parse(text: String): JsonElement = try {
    lenientJson.parseToJsonElement(text)
} catch (e: Exception) {
    throw JSONException("JSON 파싱 실패: ${e.message}")
}

class JSONObject {
    /**
     * 내용. **쓰기가 처음 일어날 때만 가변 복사본으로 바꾼다(copy-on-write).**
     *
     * 예전엔 생성자마다 `toMutableMap()` 으로 통째로 복사했다. org.json 은 하위 객체를 O(1) 참조로
     * 돌려주는데 여기서는 `getJSONObject`/`optJSONArray` 한 번이 O(n) 복사였다 —
     * 지출 1건마다 태그 배열을, 캐릭터 1명마다 스탯·유물 배열을 복사하는 식으로 파싱 비용이 상수배로 불었다.
     * 실제로 이 레이어는 대부분 읽기 전용으로 쓰이므로, 복사는 정말 쓸 때만 한다.
     */
    private var map: Map<String, JsonElement>

    /** [map] 이 이 인스턴스가 소유한 가변 복사본인지. false 면 원본을 그대로 보고 있다. */
    private var owned = false

    constructor() {
        map = mutableMapOf()
        owned = true
    }

    constructor(text: String) {
        val el = parse(text)
        if (el !is JsonObject) throw JSONException("JSONObject 가 아님")
        map = el
    }

    internal constructor(obj: JsonObject) {
        map = obj
    }

    /** 쓰기 직전에 호출 — 원본을 보고 있었다면 이 시점에 복사한다. */
    private fun mut(): MutableMap<String, JsonElement> {
        if (!owned) {
            map = map.toMutableMap()
            owned = true
        }
        @Suppress("UNCHECKED_CAST")
        return map as MutableMap<String, JsonElement>
    }

    fun has(key: String): Boolean = map.containsKey(key) && map[key] !is JsonNull

    fun keys(): Iterator<String> = map.keys.iterator()

    fun length(): Int = map.size

    // ---- get* (없으면 예외 — org.json 과 동일) ----

    fun getJSONObject(key: String): JSONObject =
        (map[key] as? JsonObject)?.let { JSONObject(it) } ?: throw JSONException("$key: JSONObject 아님")

    fun getJSONArray(key: String): JSONArray =
        (map[key] as? JsonArray)?.let { JSONArray(it) } ?: throw JSONException("$key: JSONArray 아님")

    fun getString(key: String): String =
        (map[key] as? JsonPrimitive)?.content ?: throw JSONException("$key: 문자열 아님")

    fun getInt(key: String): Int =
        (map[key] as? JsonPrimitive)?.intOrNull ?: throw JSONException("$key: 정수 아님")

    fun getLong(key: String): Long =
        (map[key] as? JsonPrimitive)?.longOrNull ?: throw JSONException("$key: Long 아님")

    fun getBoolean(key: String): Boolean =
        (map[key] as? JsonPrimitive)?.booleanOrNull ?: throw JSONException("$key: Boolean 아님")

    // ---- opt* (없으면 기본값 — org.json 과 동일) ----

    /** org.json 의 opt(key): Any? — 원시값은 문자열 content 로 반환 (?.toString() 용법 호환) */
    fun opt(key: String): Any? = when (val el = map[key]) {
        null, is JsonNull -> null
        is JsonPrimitive -> el.content
        is JsonObject -> JSONObject(el)
        is JsonArray -> JSONArray(el)
    }

    fun optJSONObject(key: String): JSONObject? = (map[key] as? JsonObject)?.let { JSONObject(it) }

    fun optJSONArray(key: String): JSONArray? = (map[key] as? JsonArray)?.let { JSONArray(it) }

    fun optString(key: String, fallback: String = ""): String {
        val el = map[key] ?: return fallback
        if (el is JsonNull) return fallback
        return (el as? JsonPrimitive)?.content ?: fallback
    }

    fun optInt(key: String, fallback: Int = 0): Int =
        (map[key] as? JsonPrimitive)?.intOrNull ?: fallback

    fun optLong(key: String, fallback: Long = 0L): Long =
        (map[key] as? JsonPrimitive)?.longOrNull ?: fallback

    fun optBoolean(key: String, fallback: Boolean = false): Boolean =
        (map[key] as? JsonPrimitive)?.booleanOrNull ?: fallback

    fun optDouble(key: String, fallback: Double = 0.0): Double =
        (map[key] as? JsonPrimitive)?.doubleOrNull ?: fallback

    // ---- put (빌더 — org.json 과 동일하게 체이닝 지원) ----

    fun put(key: String, value: String): JSONObject = apply { mut()[key] = JsonPrimitive(value) }
    fun put(key: String, value: Int): JSONObject = apply { mut()[key] = JsonPrimitive(value) }
    fun put(key: String, value: Long): JSONObject = apply { mut()[key] = JsonPrimitive(value) }
    fun put(key: String, value: Boolean): JSONObject = apply { mut()[key] = JsonPrimitive(value) }
    fun put(key: String, value: Double): JSONObject = apply { mut()[key] = JsonPrimitive(value) }
    fun put(key: String, value: JSONObject): JSONObject = apply { mut()[key] = value.toJsonElement() }
    fun put(key: String, value: JSONArray): JSONObject = apply { mut()[key] = value.toJsonElement() }

    internal fun toJsonElement(): JsonObject = JsonObject(map)

    override fun toString(): String = toJsonElement().toString()
}

class JSONArray {
    /** 내용. [JSONObject.map] 과 같은 이유로 copy-on-write. */
    private var list: List<JsonElement>

    /** [list] 가 이 인스턴스가 소유한 가변 복사본인지. */
    private var owned = false

    constructor() {
        list = mutableListOf()
        owned = true
    }

    constructor(text: String) {
        val el = parse(text)
        if (el !is JsonArray) throw JSONException("JSONArray 가 아님")
        list = el
    }

    /** org.json 의 JSONArray(Collection) — 문자열 컬렉션을 배열로 */
    constructor(values: Collection<String>) {
        list = values.map { JsonPrimitive(it) }.toMutableList()
        owned = true
    }

    internal constructor(arr: JsonArray) {
        list = arr
    }

    /** 쓰기 직전에 호출 — 원본을 보고 있었다면 이 시점에 복사한다. */
    private fun mut(): MutableList<JsonElement> {
        if (!owned) {
            list = list.toMutableList()
            owned = true
        }
        @Suppress("UNCHECKED_CAST")
        return list as MutableList<JsonElement>
    }

    fun length(): Int = list.size

    fun getJSONObject(index: Int): JSONObject =
        (list[index] as? JsonObject)?.let { JSONObject(it) } ?: throw JSONException("[$index]: JSONObject 아님")

    fun optJSONObject(index: Int): JSONObject? =
        (list.getOrNull(index) as? JsonObject)?.let { JSONObject(it) }

    fun getString(index: Int): String =
        (list[index] as? JsonPrimitive)?.content ?: throw JSONException("[$index]: 문자열 아님")

    fun optString(index: Int, fallback: String = ""): String =
        (list.getOrNull(index) as? JsonPrimitive)?.content ?: fallback

    fun put(value: JSONObject): JSONArray = apply { mut().add(value.toJsonElement()) }
    fun put(value: String): JSONArray = apply { mut().add(JsonPrimitive(value)) }
    fun put(value: Int): JSONArray = apply { mut().add(JsonPrimitive(value)) }
    fun put(value: Long): JSONArray = apply { mut().add(JsonPrimitive(value)) }

    internal fun toJsonElement(): JsonArray = JsonArray(list)

    override fun toString(): String = toJsonElement().toString()
}
