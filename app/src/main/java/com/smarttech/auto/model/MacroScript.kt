package com.smarttech.auto.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ActionStep(
    val action: String,
    val targetType: String? = null,
    val targetValue: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val fromX: Int? = null,
    val fromY: Int? = null,
    val toX: Int? = null,
    val toY: Int? = null,
    val text: String? = null,
    val ms: Long? = null,
    val count: Int? = null,
    val direction: String? = null,
    val packageName: String? = null,
    val description: String? = null,
    val steps: List<ActionStep>? = null,
    val conditionType: String? = null,
    val conditionValue: String? = null,
    val maxAttempts: Int? = null,
    val intervalMs: Long? = null
) {
    fun toJson(): JSONObject {
        val obj = JSONObject().put("action", action)
        targetType?.let { obj.put("targetType", it) }
        targetValue?.let { obj.put("targetValue", it) }
        x?.let { obj.put("x", it) }
        y?.let { obj.put("y", it) }
        fromX?.let { obj.put("fromX", it) }
        fromY?.let { obj.put("fromY", it) }
        toX?.let { obj.put("toX", it) }
        toY?.let { obj.put("toY", it) }
        text?.let { obj.put("text", it) }
        ms?.let { obj.put("ms", it) }
        count?.let { obj.put("count", it) }
        direction?.let { obj.put("direction", it) }
        packageName?.let { obj.put("packageName", it) }
        description?.let { obj.put("description", it) }
        conditionType?.let { obj.put("conditionType", it) }
        conditionValue?.let { obj.put("conditionValue", it) }
        maxAttempts?.let { obj.put("maxAttempts", it) }
        intervalMs?.let { obj.put("intervalMs", it) }
        steps?.let { arr ->
            val ja = JSONArray()
            for (s in arr) ja.put(s.toJson())
            obj.put("steps", ja)
        }
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): ActionStep {
            val steps = if (obj.has("steps")) {
                val arr = obj.getJSONArray("steps")
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } else null

            return ActionStep(
                action = obj.getString("action"),
                targetType = obj.optString("targetType", null) ?: obj.optString("type", null),
                targetValue = obj.optString("targetValue", null) ?: obj.optString("value", null),
                x = if (obj.has("x")) obj.optInt("x") else null,
                y = if (obj.has("y")) obj.optInt("y") else null,
                fromX = if (obj.has("fromX")) obj.optInt("fromX") else null,
                fromY = if (obj.has("fromY")) obj.optInt("fromY") else null,
                toX = if (obj.has("toX")) obj.optInt("toX") else null,
                toY = if (obj.has("toY")) obj.optInt("toY") else null,
                text = obj.optString("text", null),
                ms = if (obj.has("ms")) obj.optLong("ms") else null,
                count = if (obj.has("count")) obj.optInt("count") else null,
                direction = obj.optString("direction", null),
                packageName = obj.optString("packageName", null),
                description = obj.optString("description", null),
                steps = steps,
                conditionType = obj.optString("conditionType", null),
                conditionValue = obj.optString("conditionValue", null),
                maxAttempts = if (obj.has("maxAttempts")) obj.optInt("maxAttempts") else null,
                intervalMs = if (obj.has("intervalMs")) obj.optLong("intervalMs") else null
            )
        }
    }
}

data class MacroScript(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetAppPackage: String = "",
    val targetAppName: String = "",
    val steps: List<ActionStep>,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        val ja = JSONArray()
        for (s in steps) ja.put(s.toJson())
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("targetAppPackage", targetAppPackage)
            put("targetAppName", targetAppName)
            put("steps", ja)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): MacroScript {
            val arr = obj.getJSONArray("steps")
            val steps = (0 until arr.length()).map { ActionStep.fromJson(arr.getJSONObject(it)) }
            return MacroScript(
                id = obj.getString("id"),
                name = obj.getString("name"),
                targetAppPackage = obj.optString("targetAppPackage", ""),
                targetAppName = obj.optString("targetAppName", ""),
                steps = steps,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}
