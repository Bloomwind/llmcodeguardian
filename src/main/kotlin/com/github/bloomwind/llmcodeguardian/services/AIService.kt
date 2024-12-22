package com.github.bloomwind.llmcodeguardian.service

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
    private const val API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    private const val API_KEY = "sk-a212e3f3d4ed49ab9ad576692dde557f" // 请使用自己的API Key或从环境变量获取

    private val client = OkHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true // 启用忽略未知字段
    }

    @Serializable
    data class ChatRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double = 0.7
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
            val message: Message,
            val finish_reason: String? = null,
            val index: Int? = null,
            val logprobs: String? = null
        )
    }

    fun getAIResponse(userInput: String, model: String = "qwen-coder-turbo-0919"): String {
        val messages = listOf(
            Message(role = "system", content = "You are a helpful assistant."),
            Message(role = "user", content = userInput)
        )

        val requestObj = ChatRequest(
            model = model,
            messages = messages,
            temperature = 0.7
        )

        val requestJson = json.encodeToString(requestObj)
        println("Request JSON:\n$requestJson")

        val requestBody = requestJson.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            println("Response Code: ${response.code}")
            println("Response Body:\n$responseBody")

            if (response.isSuccessful && responseBody != null) {
                val chatResponse = json.decodeFromString<ChatResponse>(responseBody)
                chatResponse.choices.firstOrNull()?.message?.content ?: "No response"
            } else {
                "Error: ${response.code} - ${response.message}"
            }
        } catch (e: IOException) {
            "Error: ${e.message}"
        }
    }
}
