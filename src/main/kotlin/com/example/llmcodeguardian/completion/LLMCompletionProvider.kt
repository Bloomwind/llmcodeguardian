package com.example.llmcodeguardian.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.ProcessingContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.*
import javax.swing.SwingUtilities
import com.intellij.openapi.util.IconLoader
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
class LLMCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        resultSet: CompletionResultSet
    ) {
        println("LLMCompletionProvider triggered")
        val file = parameters.originalFile
        val offset = parameters.offset
        val textBefore = file.text.substring(0, offset).takeLast(500)
        val textAfter = file.text.substring(offset, file.textLength).take(200)
        val contextCode = textBefore + "\n<cursor>\n" + textAfter
        println("Generated contextCode: $contextCode")

        // 异步调用 LLM 获取建议
        ApplicationManager.getApplication().executeOnPooledThread {
            val suggestions = callLLMForSuggestions(contextCode)
            println("LLM suggestions returned: $suggestions")
            SwingUtilities.invokeLater {
                if (suggestions.isNotEmpty()) {
                    // 为每个建议添加注释形式的补全提示
                    for (suggestion in suggestions) {
                        resultSet.addElement(
                            LookupElementBuilder.create(suggestion)
                                .withPresentableText("// Suggestion: $suggestion")  // 显示为注释
                                .withTypeText("LLM Suggestion")
                                .bold()
                        )
                    }
                } else {
                    println("No valid suggestions found.")
                }
                resultSet.stopHere() // 确保此处在所有元素添加后调用
            }
        }
    }

    /**
     * 向远端LLM接口发送上下文并获取建议
     */
    private fun callLLMForSuggestions(contextCode: String): List<String> {
        println("callLLMForSuggestions() with contextCode:\n$contextCode")
        val response = httpPost("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", contextCode)
        println("Response from LLM: $response")
        return parseLLMResponse(response)
    }

    @Serializable
    data class LLMResponse(
        val choices: List<Choice>
    ) {
        @Serializable
        data class Choice(
            val message: Message
        ) {
            @Serializable
            data class Message(
                val content: String
            )
        }
    }

    private fun parseLLMResponse(jsonString: String): List<String> {
        return try {
            val llmResponse = Json { ignoreUnknownKeys = true }.decodeFromString<LLMResponse>(jsonString)
            val content = llmResponse.choices.firstOrNull()?.message?.content ?: ""
            extractSuggestionsFromContent(content)
        } catch (e: Exception) {
            println("parseLLMResponse failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * 从返回内容中提取代码补全建议
     */
    private fun extractSuggestionsFromContent(content: String): List<String> {
        val suggestions = mutableListOf<String>()
        val regex = Regex("```java\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
        regex.findAll(content).forEach { match ->
            suggestions.add(match.groupValues[1].trim())
        }
        return suggestions
    }

    /**
     * 使用OkHttp发起POST请求并返回字符串形式的响应
     */
    private fun httpPost(url: String, prompt: String): String {
        val client = OkHttpClient()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val jsonObj = buildJsonRequestBody(prompt)
        val requestBody = jsonObj.toRequestBody(mediaType)

        val apiKey = System.getenv("DASHSCOPE_API_KEY") ?: "sk-a212e3f3d4ed49ab9ad576692dde557f"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            println("HTTP response code: ${response.code}")
            val responseBody = response.body?.string() ?: "{}"
            println("Raw LLM response: $responseBody")
            return responseBody
        }
    }

    private fun buildJsonRequestBody(prompt: String): String {
        val sanitizedPrompt = prompt.replace("\n", " ").replace("<cursor>", "").trim()
        return """
    {
        "model": "qwen-plus",
        "messages": [
            {"role": "system", "content": "You are an advanced assistant specializing in Java code completion. Respond only with precise code snippets."},
            {"role": "user", "content": "Based on the following context, suggest relevant Java code completions: \"$sanitizedPrompt\"" }
        ]
    }
    """.trimIndent()
    }
}
