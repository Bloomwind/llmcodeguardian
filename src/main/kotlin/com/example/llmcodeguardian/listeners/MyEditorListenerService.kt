package com.example.llmcodeguardian.listeners

import com.example.llmcodeguardian.services.AIService
import com.example.llmcodeguardian.services.ExplanationCacheService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

/**
 * 在这里实现对编辑器的监听逻辑：
 *  - 当检测到用户输入 "//" 时，调用 AIService 获得对当前行的解释（基于多轮上下文）。
 *  - 暂存在 ExplanationCacheService；当用户按下 Tab 时，把解释插入到 "//" 后面。
 */
@Service(Service.Level.PROJECT)
class MyEditorListenerService(project: Project) {

    /**
     * 多轮对话消息列表：
     * - 第一条通常是 system 指令
     * - 后续 user / assistant 交替追加
     *
     * 这里仅做简要示例：当你需要针对同一个文件多次 `//` 注释触发时，
     * 都会把新的提问/回答追加到这份列表，形成上下文。
     */
    private val conversationMessages = mutableListOf(
        AIService.Message(role = "system", content = "You are a helpful assistant who explains code lines.")
    )

    init {
        // 通过 EditorFactory 注册一个 EditorFactoryListener，用于监听编辑器创建/释放事件
        EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                val editor = event.editor
                if (editor.project == project) {
                    registerListeners(editor)
                }
            }

            override fun editorReleased(event: EditorFactoryEvent) {
                // 可在此做清理
            }
        }, project)
    }

    private fun registerListeners(editor: Editor) {
        // 可选：文档变动监听
        editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                // 如果想侦测 "//" 出现，也可在这里实现
            }
        })

        // 用 AWT KeyListener 监听编辑器输入
        editor.contentComponent.addKeyListener(object : KeyAdapter() {
            override fun keyTyped(e: KeyEvent) {
                // 如果用户输入 '/', 判断前一个字符是否也是 '/'
                if (e.keyChar == '/') {
                    val offset = editor.caretModel.offset
                    if (offset >= 2) {
                        val doc = editor.document
                        val textBefore = doc.getText(TextRange(offset - 2, offset))
                        if (textBefore == "//") {
                            // 触发AI逻辑
                            onDoubleSlashDetected(editor)
                        }
                    }
                }
            }

            override fun keyPressed(e: KeyEvent) {
                // 若按下 Tab，则尝试把解释插入到 "//" 后
                if (e.keyCode == KeyEvent.VK_TAB) {
                    if (insertExplanationIfAvailable(editor)) {
                        e.consume()
                    }
                }
            }
        })
    }

    /**
     * 当检测到 `//` 后，调用 AIService 获取解释
     * 把文件全部文本 + 当前行内容 + 既有多轮上下文一起发给后端
     */
    private fun onDoubleSlashDetected(editor: Editor) {
        val project = editor.project ?: return
        val document = editor.document

        val fileText = document.text  // 全局上下文
        val caretOffset = editor.caretModel.offset

        // 当前行文本（局部上下文）
        val lineNumber = document.getLineNumber(caretOffset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val currentLineText = document.getText(TextRange(lineStart, lineEnd))

        // 在对话里追加一条 user 消息，指明要解释当前行
        val userPrompt = buildString {
            appendLine("The entire file is below:")
            appendLine(fileText)
            appendLine("Please briefly explain this line: $currentLineText")
        }
        conversationMessages.add(AIService.Message(role = "user", content = userPrompt))

        // AI 回答先暂用 "Loading..."
        conversationMessages.add(AIService.Message(role = "assistant", content = "Loading..."))

        // 异步调用
        ApplicationManager.getApplication().executeOnPooledThread {
            // 真正调用后端：传入 conversationMessages
            val aiResponse = AIService.getAIResponse(messages = conversationMessages)

            // 替换刚添加的 “Loading...” (最后一条) 为实际回答
            if (conversationMessages.lastOrNull()?.content == "Loading...") {
                conversationMessages[conversationMessages.size - 1] =
                    AIService.Message(role = "assistant", content = aiResponse)
            }

            // 截断或精简回答
            val shortExplanation = parseForShortExplanation(aiResponse)

            // 将结果存入 ExplanationCache，以便 Tab 键插入
            ExplanationCacheService.getInstance(project).putExplanationForCaret(caretOffset, shortExplanation)
        }
    }

    /**
     * 若 ExplanationCache 有可用解释，就插到 "//" 后
     */
    private fun insertExplanationIfAvailable(editor: Editor): Boolean {
        val project = editor.project ?: return false
        val caretOffset = editor.caretModel.offset
        val explanation = ExplanationCacheService.getInstance(project).getExplanationForCaret(caretOffset)
            ?: return false

        if (explanation.isBlank()) return false

        // 找到当前行里的 "//"
        val document = editor.document
        val lineNumber = document.getLineNumber(caretOffset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val lineTextRange = TextRange(lineStartOffset, caretOffset)
        val lineTextBeforeCaret = document.getText(lineTextRange)

        val doubleSlashIndex = lineTextBeforeCaret.lastIndexOf("//")
        if (doubleSlashIndex < 0) return false

        val insertOffset = lineStartOffset + doubleSlashIndex + 2

        // 写操作
        WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(insertOffset, " " + explanation.trim())
        }

        editor.caretModel.moveToOffset(insertOffset + 1 + explanation.length)

        // 清除缓存，避免重复插入
        ExplanationCacheService.getInstance(project).clearExplanation(caretOffset)
        return true
    }

    /**
     * 对 AI 返回的长文本做截断或筛选
     */
    private fun parseForShortExplanation(full: String): String {
        // 示例：取前 80 字符
        return full.take(80)
    }
}
