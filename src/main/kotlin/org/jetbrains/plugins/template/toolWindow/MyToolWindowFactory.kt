package org.jetbrains.plugins.template.toolWindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import org.jetbrains.plugins.template.service.AIService
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JPanel
import com.intellij.openapi.application.ApplicationManager
import javax.swing.SwingUtilities
import java.awt.event.ActionListener
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.html.HtmlRenderer

class MyToolWindowFactory : ToolWindowFactory, DumbAware {

    // 用于存储对话历史 (用户输入, AI 回复)
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    // Markdown转换器（单例）
    private val parser: Parser = Parser.builder().build()
    private val renderer: HtmlRenderer = HtmlRenderer.builder().build()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())

        // 使用 JEditorPane 显示HTML内容
        val chatPane = JEditorPane().apply {
            contentType = "text/html"
            isEditable = false
            text = "<html><body style='font-family: sans-serif; margin:0; padding:0;'></body></html>"
        }

        val scrollPane = JBScrollPane(chatPane)

        val inputField = JBTextField().apply {
            toolTipText = "Type your question here"
        }

        val sendButton = JButton("Send").apply {
            toolTipText = "Click to send your message"
        }

        sendButton.addActionListener {
            val userInput = inputField.text.trim()
            if (userInput.isNotEmpty()) {
                // 显示Loading状态
                conversationHistory.add(userInput to "Loading...")
                updateChatContent(chatPane)
                inputField.text = ""

                // 异步调用API
                ApplicationManager.getApplication().executeOnPooledThread {
                    val aiResponse = getAIResponse(userInput)

                    // 更新最后一条消息（"Loading..." -> AI回应）
                    conversationHistory.removeAt(conversationHistory.size - 1)
                    conversationHistory.add(userInput to aiResponse)

                    // 在EDT上更新UI
                    SwingUtilities.invokeLater {
                        updateChatContent(chatPane)
                    }
                }
            }
        }

        val inputPanel = JPanel(BorderLayout()).apply {
            add(inputField, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }

        panel.add(scrollPane, BorderLayout.CENTER)
        panel.add(inputPanel, BorderLayout.SOUTH)

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun getAIResponse(userInput: String): String {
        // 调用真实API获取AI回复（AIService负责调用DashScope/OpenAI兼容接口）
        return AIService.getAIResponse(userInput)
    }

    private fun updateChatContent(chatPane: JEditorPane) {
        val newHTML = """
        <html>
          <body style='font-family: sans-serif; margin:0; padding:0;'>
            ${generateChatHTML()}
          </body>
        </html>
        """.trimIndent()

        chatPane.text = newHTML
        chatPane.caretPosition = chatPane.document.length
    }

    private fun generateChatHTML(): String {
        val sb = StringBuilder()
        sb.append("<div style='padding:8px;'>")
        for ((user, ai) in conversationHistory) {
            sb.append(
                """
            <div style="margin:8px; padding:8px; background-color:#e0f7fa; border-radius:8px;">
              <img src="file:///C:\Users\ASUS\Desktop\Final Year Project\intellij-platform-plugin-template\src\main\resources\icon\user.png" width="20" height="20" style="vertical-align: middle; margin-right:5px;">
              <b>You:</b> ${escapeHTML(user)}
            </div>
            """
            )
            if (ai == "Loading...") {
                sb.append(
                    """
                <div style="margin:8px; padding:8px; background-color:#f0f0f0; border-radius:8px;">
                  <img src="file:///C:\Users\ASUS\Desktop\Final Year Project\intellij-platform-plugin-template\src\main\resources\icon\loading-splash.gif" width="20" height="20" style="vertical-align: middle; margin-right:5px;">
                  <b>AI:</b> 正在思考...
                </div>
                """
                )
            } else {
                // 将AI返回的Markdown转换为HTML
                val aiHtml = markdownToHtml(ai)
                sb.append(
                    """
                <div style="margin:8px; padding:8px; background-color:#f0f0f0; border-radius:8px;">
                  <img src="file:///C:\Users\ASUS\Desktop\Final Year Project\intellij-platform-plugin-template\src\main\resources\icon\ai.png" width="20" height="20" style="vertical-align: middle; margin-right:5px;">
                  <b>AI:</b> $aiHtml
                </div>
                """
                )
            }
        }
        sb.append("</div>")
        return sb.toString()
    }

    private fun markdownToHtml(markdownText: String): String {
        val document = parser.parse(markdownText)
        return renderer.render(document)
    }

    private fun escapeHTML(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
