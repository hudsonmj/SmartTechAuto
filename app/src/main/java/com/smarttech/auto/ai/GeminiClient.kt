package com.smarttech.auto.ai

import com.smarttech.auto.model.ActionStep
import com.smarttech.auto.model.RecordedAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class GeminiClient(private val apiKey: String) {

    private companion object {
        const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
        const val TAG = "GeminiClient"
    }

    suspend fun generateScript(userRequest: String): Result<List<ActionStep>> = withContext(Dispatchers.IO) {
        try {
            val prompt = PromptBuilder.build(userRequest)
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text", prompt)
                    ))
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 4096)
                })
            }

            val url = URL("$API_URL?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 30000

            conn.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val response = BufferedReader(InputStreamReader(stream)).readText()

            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("API error $responseCode: $response"))
            }

            val json = JSONObject(response)
            val text = json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            val cleaned = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val arr = JSONArray(cleaned)
            val steps = (0 until arr.length()).map { ActionStep.fromJson(arr.getJSONObject(it)) }
            Result.success(steps)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun optimizeFromRecording(actions: List<RecordedAction>): Result<List<ActionStep>> = withContext(Dispatchers.IO) {
        try {
            val prompt = PromptBuilder.buildFromRecording(actions)
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text", prompt)
                    ))
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 4096)
                })
            }

            val url = URL("$API_URL?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 30000

            conn.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val response = BufferedReader(InputStreamReader(stream)).readText()

            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("API error $responseCode: $response"))
            }

            val json = JSONObject(response)
            val text = json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            val cleaned = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val arr = JSONArray(cleaned)
            val steps = (0 until arr.length()).map { ActionStep.fromJson(arr.getJSONObject(it)) }
            Result.success(steps)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
