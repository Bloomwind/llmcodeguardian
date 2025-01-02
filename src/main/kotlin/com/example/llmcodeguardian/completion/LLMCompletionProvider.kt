package com.example.llmcodeguardian.completion

import com.example.llmcodeguardian.services.AIService
import com.example.llmcodeguardian.services.AIService.Message
import com.intellij.codeInsight.completion.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBList
import com.intellij.util.ProcessingContext
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.event.*
import javax.swing.*

class LLMCompletionProvider : CompletionProvider<CompletionParameters>() {

    private var popup: JBPopup? = null // 跟踪当前弹窗

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
            val systemMessage = Message("system", "Suggest code completions for the following context:")
            val userMessage = Message("user", contextCode)
            val suggestions = callLLMForSuggestions(listOf(systemMessage, userMessage))
            println("LLM suggestions returned: $suggestions")

            ApplicationManager.getApplication().invokeLater {
                if (suggestions.isNotEmpty()) {
                    updateOrCreatePopup(parameters.editor, suggestions)
                } else {
                    println("No valid suggestions found.")
                }
            }

        }
    }

    private fun updateOrCreatePopup(editor: Editor, suggestions: List<String>) {
        val codeContent = suggestions.joinToString("\n\n") // 合并所有代码块为一个字符串
        if (popup != null && popup!!.isVisible) {
            // 更新现有弹窗的内容
            val contentPanel = popup!!.content as JPanel
            val textArea = contentPanel.getComponent(1) as JTextArea
            textArea.text = codeContent
        } else {
            // 创建新的弹窗
            popup = createPopup(editor, codeContent)
            popup?.showInBestPositionFor(editor)
        }
    }

    private fun createPopup(editor: Editor, codeContent: String): JBPopup {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = BorderFactory.createEmptyBorder(15, 15, 15, 15) // 增加内边距

        // 提示标签
        val instructionLabel = JLabel("<html><div style='text-align: center;'>Review the suggested code below.<br>Press 'Apply' to insert at the cursor.</div></html>")
        instructionLabel.font = Font(Font.SANS_SERIF, Font.ITALIC, 12)
        instructionLabel.foreground = java.awt.Color(0, 102, 204) // 设置文字为蓝色
        instructionLabel.alignmentX = JPanel.CENTER_ALIGNMENT
        panel.add(instructionLabel)

        // 代码显示区域
        val textArea = JTextArea(codeContent)
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        textArea.isEditable = false
        textArea.font = Font(Font.MONOSPACED, Font.PLAIN, 14)
        textArea.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(java.awt.Color(200, 200, 200), 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        )
        val scrollPane = JBScrollPane(textArea)
        scrollPane.preferredSize = Dimension(600, 400)
        scrollPane.border = BorderFactory.createLineBorder(java.awt.Color(150, 150, 150))
        panel.add(scrollPane)

        // "应用"按钮
        val applyButton = JButton("<html><span style='color: #0056b3;'>Apply Suggestion</span></html>").apply {
            font = Font(Font.SANS_SERIF, Font.BOLD, 14)
            minimumSize = Dimension(220, 50) // 确保按钮大小足够
            preferredSize = Dimension(220, 50)
            background = java.awt.Color(245, 245, 245) // 按钮背景色为浅灰
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(java.awt.Color(0, 102, 204), 2), // 边框颜色为蓝色
                BorderFactory.createEmptyBorder(5, 15, 5, 15)
            )
            isFocusPainted = false // 移除焦点框

            // 鼠标交互效果
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent?) {
                    background = java.awt.Color(230, 230, 250) // 鼠标悬停背景色
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) // 鼠标变为手形
                }

                override fun mouseExited(e: MouseEvent?) {
                    background = java.awt.Color(245, 245, 245) // 恢复背景色
                    cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
                }

                override fun mousePressed(e: MouseEvent?) {
                    background = java.awt.Color(220, 220, 240) // 按下背景色
                }

                override fun mouseReleased(e: MouseEvent?) {
                    background = java.awt.Color(230, 230, 250) // 恢复悬停背景色
                }
            })

            // 按钮点击事件
            addActionListener {
                applySuggestion(editor, codeContent)
            }
        }
        panel.add(Box.createVerticalStrut(15)) // 添加垂直间距
        panel.add(applyButton)
        panel.alignmentX = JPanel.CENTER_ALIGNMENT

        // 返回美化后的弹窗
        return JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, null)
            .setTitle("Code Suggestions")
            .setResizable(true)
            .setMovable(true)
            .createPopup()
    }




    private fun applySuggestion(editor: Editor, suggestion: String) {
        val project = editor.project
        if (project != null) {
            WriteCommandAction.runWriteCommandAction(project) {
                val caretOffset = editor.caretModel.offset
                val document = editor.document
                val currentLineStartOffset = document.getLineStartOffset(document.getLineNumber(caretOffset))
                val currentLineContent = document.getText(com.intellij.openapi.util.TextRange(currentLineStartOffset, caretOffset)).trim()

                // 清理重复的代码内容
                val sanitizedSuggestion = sanitizeSuggestion(currentLineContent, suggestion)

                if (sanitizedSuggestion.isNotBlank()) {
                    document.insertString(caretOffset, sanitizedSuggestion)
                    println("Applied sanitized suggestion: $sanitizedSuggestion")
                } else {
                    println("Sanitized suggestion was empty or redundant, not applied.")
                }
                popup?.cancel()
            }
        } else {
            println("Project is null, cannot apply suggestion.")
        }
    }

    // 清理重复代码内容的辅助函数
    private fun sanitizeSuggestion(currentLine: String, suggestion: String): String {
        val suggestionLines = suggestion.lines()
        val sanitizedLines = if (currentLine.isNotBlank() && suggestionLines.firstOrNull()?.startsWith(currentLine) == true) {
            // 如果补全内容的第一行包含当前行代码，则剔除重复的部分
            suggestionLines.drop(1)
        } else {
            suggestionLines
        }
        return sanitizedLines.joinToString("\n").trim()
    }



    private fun callLLMForSuggestions(messages: List<Message>): List<String> {
        return try {
            val response = AIService.getAnswerResponse(messages)

            // 提取代码块
            val codeBlocks = extractCodeBlocks(response)

            if (codeBlocks.isEmpty()) {
                println("No code blocks found.")
            }
            codeBlocks
        } catch (e: Exception) {
            println("Error while calling AIService: ${e.message}")
            emptyList()
        }
    }

    // 提取代码块的辅助函数
    private fun extractCodeBlocks(response: String): List<String> {
        val regex = Regex("```[a-zA-Z]*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
        return regex.findAll(response)
            .mapNotNull { matchResult ->
                matchResult.groups[1]?.value?.trim() // 提取代码块内容
            }
            .toList()
    }


}
