package com.example.llmcodeguardian.listeners

import com.example.llmcodeguardian.services.AIService
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.json.JSONObject

class CodeTranslationAction : AnAction("Translate Code") {

    override fun actionPerformed(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val project = event.project ?: return
        val selectedText = editor.selectionModel.selectedText
        if (selectedText.isNullOrBlank()) {
            println("[Translate Code] No text selected for translation")
            return
        }

        println("[Translate Code] Selected text: $selectedText")

        // 在 prompt 上就告诉 AI：给出简短的翻译
        val prompt = """
            请给出一个简短的代码注释翻译，对这部分代码进行概括性描述，不超过50字
            $selectedText
        """.trimIndent()

        val aiResponse = AIService.getAnswerResponse(
            listOf(AIService.Message("user", prompt))
        )

        // 直接把 aiResponse 当翻译，并再做一次 refine
        val translation = refineTranslationText(aiResponse)
        println("[Translate Code] AI translation: $translation")

        insertTranslation(editor, project, translation)

    }
    private fun refineTranslationText(response: String): String {
        // 1. 把多余空白转成一个空格
        val oneLine = response.replace(Regex("\\s+"), " ").trim()
        // 2. 做一个 120 字符的截断
        val maxLength = 200
        return if (oneLine.length > maxLength) {
            oneLine.take(maxLength) + "..."
        } else {
            oneLine
        }
    }

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        event.presentation.isEnabledAndVisible = hasSelection
        println("[Translate Code] Update action visibility: hasSelection=$hasSelection")
    }

    private fun insertTranslation(editor: Editor, project: Project, translation: String) {
        val selectionEnd = editor.selectionModel.selectionEnd
        WriteCommandAction.runWriteCommandAction(project) {
            val document = editor.document
            val translationComment = " // $translation"
            document.insertString(selectionEnd, translationComment)
            println("[Translate Code] Inserted translation at offset: $selectionEnd")
        }
        editor.selectionModel.removeSelection()
    }
}
