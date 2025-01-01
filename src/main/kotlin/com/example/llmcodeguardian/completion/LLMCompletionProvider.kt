package com.example.llmcodeguardian.completion

import com.intellij.codeInsight.completion.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBList
import com.intellij.util.ProcessingContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.awt.Dimension
import java.awt.Font
import java.awt.event.*
import javax.swing.JPanel
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel

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

        ApplicationManager.getApplication().executeOnPooledThread {
            val suggestions = callLLMForSuggestions(contextCode)
            println("LLM suggestions returned: $suggestions")

            ApplicationManager.getApplication().invokeLater {
                if (suggestions.isNotEmpty()) {
                    displayPopup(parameters.editor, suggestions)
                } else {
                    println("No valid suggestions found.")
                }
            }
        }
    }

    private fun displayPopup(editor: Editor, suggestions: List<String>) {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val instructionLabel = JLabel("Use arrow keys to navigate, Enter to apply, or click Apply Button.")
        instructionLabel.font = Font(Font.SANS_SERIF, Font.ITALIC, 12)
        instructionLabel.alignmentX = JPanel.CENTER_ALIGNMENT
        panel.add(instructionLabel)

        val list = JBList(suggestions)
        list.setCellRenderer { _, value, _, _, _ ->
            val panel = JPanel()
            panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
            val checkBox = JCheckBox()
            val label = JLabel("<html><pre style='background-color:#f5f5f5; padding:5px; border:1px solid #ccc;'>${value.replace("\n", "<br>")}</pre></html>")
            label.font = Font(Font.MONOSPACED, Font.PLAIN, 14)
            panel.add(checkBox)
            panel.add(label)
            panel
        }
        list.setEmptyText("No suggestions available")

        val scrollPane = JBScrollPane(list)
        scrollPane.preferredSize = Dimension(400, 300)
        panel.add(scrollPane)

        val applyButton = JButton("Apply Selected Suggestion")
        applyButton.font = Font(Font.SANS_SERIF, Font.BOLD, 12)
        applyButton.preferredSize = Dimension(200, 30)
        applyButton.background = java.awt.Color(59, 89, 152)
        applyButton.foreground = java.awt.Color.WHITE
        applyButton.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                applyButton.background = java.awt.Color(89, 119, 182)
            }

            override fun mouseExited(e: MouseEvent?) {
                applyButton.background = java.awt.Color(59, 89, 152)
            }
        })
        applyButton.addActionListener {
            val selectedValue = list.selectedValue
            if (selectedValue != null) {
                applySuggestion(editor, selectedValue)
            }
        }
        panel.add(applyButton)

        list.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent?) {
                if (e?.keyCode == KeyEvent.VK_ENTER) {
                    val selectedValue = list.selectedValue
                    if (selectedValue != null) {
                        applySuggestion(editor, selectedValue)
                    }
                }
            }
        })

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, list)
            .setTitle("Code Suggestions")
            .setResizable(true)
            .setMovable(true)
            .createPopup()

        popup.showInBestPositionFor(editor)
    }

    private fun applySuggestion(editor: Editor, suggestion: String) {
        val project = editor.project
        if (project != null) {
            WriteCommandAction.runWriteCommandAction(project) {
                val caretOffset = editor.caretModel.offset
                val documentText = editor.document.text

                val sanitizedSuggestion = sanitizeSuggestion(documentText, caretOffset, suggestion)
                if (sanitizedSuggestion.isNotBlank()) {
                    editor.document.insertString(caretOffset, sanitizedSuggestion)
                    println("Applied suggestion: $sanitizedSuggestion")
                } else {
                    println("Suggestion was fully redundant and not applied.")
                }
            }
        } else {
            println("Project is null, cannot apply suggestion.")
        }
    }

    private fun sanitizeSuggestion(currentCode: String, caretOffset: Int, suggestion: String): String {
        val currentCodeUpToCaret = currentCode.substring(0, caretOffset)
        val currentLines = currentCodeUpToCaret.lines().map { it.trim() }

        val suggestionLines = suggestion.lines().map { it.trim() }

        val filteredLines = suggestionLines.dropWhile { it in currentLines }

        return filteredLines.joinToString("\n")
    }

    private fun callLLMForSuggestions(contextCode: String): List<String> {
        println("callLLMForSuggestions() with contextCode:\n$contextCode")
        val response = httpPost("http://172.31.233.216:8080/v1/chat/completions", contextCode)
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
            llmResponse.choices.mapNotNull { it.message.content.trim() }
        } catch (e: Exception) {
            println("parseLLMResponse failed: ${e.message}")
            emptyList()
        }
    }

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
                {"role": "system", "content": "You are an advanced assistant specializing in Java code completion. Respond only with the missing or suggested code snippet without any additional explanation."},
                {"role": "user", "content": "Based on the following context, suggest relevant Java code completions: \"$sanitizedPrompt\""}
            ]
        }
        """.trimIndent()
    }
}
