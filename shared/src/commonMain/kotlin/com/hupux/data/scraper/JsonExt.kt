package com.hupux.data.scraper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** 全局宽松解析器：虎扑返回的 JSON 偶有非标准片段，宽松模式更稳。 */
internal val HupuJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal val EmptyJsonArray = JsonArray(emptyList())

/** 解析整段 JSON 文本为对象树根。 */
internal fun parseJsonObject(text: String): JsonObject =
    HupuJson.parseToJsonElement(text).jsonObjectOrThrow()

internal fun JsonElement.jsonObjectOrThrow(): JsonObject =
    this as? JsonObject ?: error("expected JsonObject but was ${this::class.simpleName}")

// ── 安全访问器：语义对齐原 Gson 版本 ───────────────────────────────────────
// 键缺失或值为 JSON null 时返回 null。数字/字符串宽松互转（对齐 gson asString/asInt）。

private fun JsonObject.prim(key: String): JsonPrimitive? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
/** 字段存在且值不是 JSON null（不限类型）。 */
internal fun JsonObject.has(key: String): Boolean = this[key]?.let { it !is JsonNull } ?: false
internal fun JsonObject.str(key: String): String? = prim(key)?.contentOrNull
internal fun JsonObject.int_(key: String): Int? = prim(key)?.intOrNull
internal fun JsonObject.long_(key: String): Long? = prim(key)?.longOrNull
internal fun JsonObject.bool_(key: String): Boolean? = prim(key)?.booleanOrNull

// ── 元素级快捷方式（用于数组遍历） ─────────────────────────────────────────

internal val JsonElement.obj: JsonObject get() = this as JsonObject
internal val JsonElement.isObj: Boolean get() = this is JsonObject
internal val JsonElement.asStr: String? get() = (this as? JsonPrimitive)?.contentOrNull
internal val JsonElement.asLong: Long? get() = (this as? JsonPrimitive)?.longOrNull
