package com.smarttech.auto.storage

import android.content.Context
import android.content.SharedPreferences
import com.smarttech.auto.model.ActionStep
import com.smarttech.auto.model.MacroScript
import org.json.JSONArray
import org.json.JSONObject

class MacroRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("macro_scripts", Context.MODE_PRIVATE)

    fun getAll(): List<MacroScript> {
        val raw = prefs.getString("macros", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { MacroScript.fromJson(arr.getJSONObject(it)) }
            .sortedByDescending { it.updatedAt }
    }

    fun getById(id: String): MacroScript? {
        return getAll().find { it.id == id }
    }

    fun save(script: MacroScript) {
        val all = getAll().toMutableList()
        val idx = all.indexOfFirst { it.id == script.id }
        val updated = script.copy(updatedAt = System.currentTimeMillis())
        if (idx >= 0) all[idx] = updated else all.add(updated)
        saveAll(all)
    }

    fun delete(id: String) {
        val all = getAll().toMutableList()
        all.removeAll { it.id == id }
        saveAll(all)
    }

    fun getApiKey(): String {
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    private fun saveAll(scripts: List<MacroScript>) {
        val arr = JSONArray()
        for (s in scripts) arr.put(s.toJson())
        prefs.edit().putString("macros", arr.toString()).apply()
    }
}
