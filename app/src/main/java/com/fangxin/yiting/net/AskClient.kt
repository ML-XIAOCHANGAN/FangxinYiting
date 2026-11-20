package com.fangxin.yiting.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class AiAnswer {
    data class Judge(
        val verdict: String,
        val reasons: List<String>,
        val steps: List<String>,
    ) : AiAnswer()

    data class Chat(val reply: String) : AiAnswer()
}

object AskClient {
    const val ORIGIN = "https://fangxin-yiting-oqspdkiq3lk.qoder.zone"
    private const val URL = "$ORIGIN/functions/v1/app/ask"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun ask(text: String): AiAnswer? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("text", text).toString().toRequestBody(jsonType)
            val request = Request.Builder()
                .url(URL)
                .header("Origin", ORIGIN)
                .header("Accept", "application/json")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val raw = response.body?.string().orEmpty()
                parse(raw)
            }
        } catch (error: Exception) {
            null
        }
    }

    private fun parse(raw: String): AiAnswer? = try {
        val obj = JSONObject(raw)
        when (obj.optString("kind")) {
            "judge" -> {
                val verdict = if (obj.optString("verdict") == "red") "red" else "amber"
                AiAnswer.Judge(
                    verdict = verdict,
                    reasons = strings(obj.optJSONArray("reasons")).take(3),
                    steps = strings(obj.optJSONArray("steps")).take(3),
                )
            }
            "chat" -> AiAnswer.Chat(obj.optString("reply").trim())
            else -> null
        }
    } catch (error: Exception) {
        null
    }

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val items = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotEmpty()) items += value
        }
        return items
    }
}
