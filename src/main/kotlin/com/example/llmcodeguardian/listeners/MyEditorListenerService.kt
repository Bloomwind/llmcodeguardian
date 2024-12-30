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
 *  - 当检测到用户输入 "//" 时，调用 AIService 获得对当前行的解释。
 *  - 暂存在 ExplanationCacheService；当用户按下 Tab 时，把解释插入到 "//" 后面。
 */
@Service(Service.Level.PROJECT)
class MyEditorListenerService(project: Project) {

    init {
        // 通过 EditorFactory 注册一个 EditorFactoryListener，用于监听编辑器创建/释放事件
        EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                val editor = event.editor
                // 确保当前 editor 对应本 project
                if (editor.project == project) {
                    registerListeners(editor)
                }
            }

            override fun editorReleased(event: EditorFactoryEvent) {
                // 当 editor 被释放时，若有需要可以清理资源
            }
        }, project) // 以 project 作为 Disposable
    }

    private fun registerListeners(editor: Editor) {
        // （可选）文档变动监听：如果需要在文本发生任何变动时介入，可在这里
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
                            // 触发 AI 调用
                            onDoubleSlashDetected(editor)
                        }
                    }
                }
            }

            override fun keyPressed(e: KeyEvent) {
                // 如果用户按下 Tab 键，就尝试把解释插入到 "//" 后面
                if (e.keyCode == KeyEvent.VK_TAB) {
                    if (insertExplanationIfAvailable(editor)) {
                        // 若已经插入了解释，则不再传给其他处理（如自动补全）
                        e.consume()
                    }
                }
            }
        })
    }

    /**
     * 当检测到 `//` 后，调用 AI 获取解释
     */
    private fun onDoubleSlashDetected(editor: Editor) {
        val project = editor.project ?: return
        val document = editor.document
        val fileText = document.text          // 全局上下文（整份代码）
        val caretOffset = editor.caretModel.offset

        // 当前行文本（局部上下文）
        val lineNumber = document.getLineNumber(caretOffset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val currentLineText = document.getText(TextRange(lineStart, lineEnd))

        // 异步调用 AI，防止阻塞 UI 线程
        ApplicationManager.getApplication().executeOnPooledThread {
            // 调用 AI 接口，返回一个完整解释
            val fullExplanation = AIService.getAIResponse(
                userInput = buildString {
                    // 你可以在这里自定义 prompt，如:
                    appendLine("I have the following code:")
                    appendLine(fileText)
                    appendLine("Please give a short explanation for the following line:")
                    appendLine(currentLineText)
                },
                model = "qwen-coder-plus-latest"
            )

            // 筛选或截断，得到简短解释
            val shortExplanation = parseForShortExplanation(fullExplanation)

            // 将结果缓存到 ExplanationCache，以便用户按下 Tab 时可插入
            ExplanationCacheService.getInstance(project)
                .putExplanationForCaret(caretOffset, shortExplanation)
        }
    }

    /**
     * 如果 ExplanationCache 中有可用解释，就插入到 "//" 后面
     */
    private fun insertExplanationIfAvailable(editor: Editor): Boolean {
        val project = editor.project ?: return false
        val caretOffset = editor.caretModel.offset

        val explanation = ExplanationCacheService.getInstance(project)
            .getExplanationForCaret(caretOffset)
            ?: return false // 没有缓存的解释

        if (explanation.isBlank()) return false

        // 找到当前行中的 "//" 位置
        val document = editor.document
        val lineNumber = document.getLineNumber(caretOffset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val lineTextBeforeCaret = document.getText(TextRange(lineStartOffset, caretOffset))
        val doubleSlashIndex = lineTextBeforeCaret.lastIndexOf("//")
        if (doubleSlashIndex < 0) return false // 当前行没有 "//"

        // 计算插入位置：在 "//" 后面
        val insertOffset = lineStartOffset + doubleSlashIndex + 2

        // 写操作，插入文本
        WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(insertOffset, " " + explanation.trim())
        }

        // 移动光标到插入内容后面
        editor.caretModel.moveToOffset(insertOffset + 1 + explanation.length)

        // 清除缓存，避免重复插入
        ExplanationCacheService.getInstance(project).clearExplanation(caretOffset)

        return true
    }

    /**
     * 对 AI 返回的长文本进行简要截断或处理
     */
    private fun parseForShortExplanation(fullExplanation: String): String {
        // 这里仅示例截断前 80 个字符
        return fullExplanation.take(80)
    }
}
