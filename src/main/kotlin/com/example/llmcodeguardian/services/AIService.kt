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
    private const val QA_API_URL = "http://172.31.233.216:8080/v1/chat/answer"
    private const val COMPLETION_API_URL = "http://172.31.233.216:8080/v1/chat/completions"
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
            val index: Int? = null
        )
    }

    /**
     * 通用请求方法
     */
    private fun sendRequest(
        apiUrl: String,
        requestObj: ChatRequest
    ): String {
        val requestJson = json.encodeToString(requestObj)
        println("Request JSON:\n$requestJson")

        val requestBody = requestJson.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
//                println("Response Code: ${response.code}")
//                println("Response Body:\n$responseBody")

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

    /**
     * 问答专用方法
     */
    fun getAnswerResponse(
        messages: List<Message>,
        model: String = "qwen_coder_instruct"
    ): String {
        val requestObj = ChatRequest(
            model = model,
            messages = messages
        )
        return sendRequest(QA_API_URL, requestObj)
    }

    /**
     * 补全专用方法
     */
    fun getCompletionResponse(
        messages: List<Message>,
        model: String = "qwen_coder_completions"
    ): String {
        val requestObj = ChatRequest(
            model = model,
            messages = messages,
            max_tokens = 1000,
            presence_penalty = 0.0,
            frequency_penalty = 0.0,
            temperature = 1.0,
            top_p = 1.0
        )
        return sendRequest(COMPLETION_API_URL, requestObj)
    }
}
