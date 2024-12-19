package org.jetbrains.plugins.template.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object AIService {
    private const val API_URL = "https://dashscope.aliyuncs.com/openai/v1/chat/completions"
    private const val API_KEY = "" // API 密钥

    private val client = OkHttpClient()

    // 请求数据模型
    @Serializable
    data class ChatRequest(
        val model: String = "gpt-3.5-turbo",
        val messages: List<Message>,
        val temperature: Double = 0.7
    )

    @Serializable
    data class Message(
        val role: String, // "user" 或 "assistant"
        val content: String
    )

    // 响应数据模型
    @Serializable
    data class ChatResponse(
        val choices: List<Choice>
    ) {
        @Serializable
        data class Choice(
            val message: Message
        )
    }

    // 调用 AI API
    fun getAIResponse(conversationHistory: List<Pair<String, String>>, userInput: String): String {
        // 构造消息历史
        val messages = conversationHistory.map { (user, ai) ->
            listOf(
                Message(role = "user", content = user),
                Message(role = "assistant", content = ai)
            )
        }.flatten() + Message(role = "user", content = userInput)

        // 构造请求体
        val requestBody = Json.encodeToString(
            ChatRequest(messages = messages)
        ).toRequestBody("application/json".toMediaType())

        // 构造请求
        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $API_KEY")
            .post(requestBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val chatResponse = Json.decodeFromString<ChatResponse>(responseBody)
                    chatResponse.choices.firstOrNull()?.message?.content ?: "No response"
                } else {
                    "No response body"
                }
            } else {
                "Error: ${response.code} - ${response.message}"
            }
        } catch (e: IOException) {
            "Error: ${e.message}"
        }
    }
}
