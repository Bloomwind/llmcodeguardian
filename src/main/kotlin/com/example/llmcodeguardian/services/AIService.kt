package com.example.llmcodeguardian.services

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object AIService {
    private const val API_URL = "http://172.31.233.216:8080/v1/chat/answer"
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class ChatRequest(
        val model: String,
        val messages: List<Message>,
        val max_tokens: Int = 500,
        val presence_penalty: Double = 1.03,
        val frequency_penalty: Double = 1.0,
        val seed: Int? = null,
        val temperature: Double = 0.5,
        val top_p: Double = 0.95,
        val stream: Boolean = false
    )

    @Serializable
    data class Message(
        val role: String,
        val content: String
    )

    @Serializable
    data class ChatResponse(
        val choices: List<Choice>
    ) {
        @Serializable
        data class Choice(
            val message: Message?,
            val finish_reason: String? = null,
            val index: Int? = null,
            val logprobs: String? = null
        )
    }

    /**
     * 此函数接收整个 messages 列表，而不是单一 userInput
     * 第一个 message 通常是 system 指令，后续 user / assistant 交替
     */
    fun getAIResponse(
        messages: List<Message>,
        model: String = "qwen_coder_instruct"
    ): String {
        // 假设我们只保留最近 6 条(含 system) 防止 prompt 太长
        // 注：system 一般在 index=0
        val trimmedMessages = if (messages.size > 6) {
            val systemMsg = messages.first()
            val tail = messages.takeLast(5)
            listOf(systemMsg) + tail
        } else {
            messages
        }

        val requestObj = ChatRequest(
            model = model,
            messages = trimmedMessages,
            max_tokens = 1024, // 调大
            presence_penalty = 1.03,
            frequency_penalty = 1.0,
            seed = null,
            temperature = 0.5,
            top_p = 0.95,
            stream = false
        )

        val requestJson = json.encodeToString(requestObj)
        println("Request JSON:\n$requestJson")

        val requestBody = requestJson.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                println("Response Code: ${response.code}")
                println("Response Body:\n$responseBody")

                if (response.isSuccessful && responseBody != null) {
                    val chatResponse = json.decodeFromString<ChatResponse>(responseBody)
                    chatResponse.choices.firstOrNull()?.message?.content ?: "No response"
                } else {
                    "Error: ${response.code} - ${response.message}"
                }
            }
        } catch (e: IOException) {
            "Error: ${e.message}"
        }
    }
}
