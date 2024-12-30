package com.example.llmcodeguardian.toolWindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.example.llmcodeguardian.services.AIService
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JPanel
import com.intellij.openapi.application.ApplicationManager
import javax.swing.SwingUtilities
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.JLabel
import javax.swing.BorderFactory
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class MyToolWindowFactory : ToolWindowFactory, DumbAware {

    /**
     * 当前会话的消息记录：Pair<用户输入, AI回复>
     */
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    /**
     * 用于暂存所有历史会话的记录（只在本次插件运行期间有效）
     * 每新建一次对话，就把当前对话记录存进去，然后清空 conversationHistory
     */
    private val previousConversations = mutableListOf<List<Pair<String, String>>>()

    /**
     * Copilot Chat 风格的初始欢迎文本，可在新建对话时重置
     */
    private val initialWelcomeHtml = """
        <html>
          <body style="font-family: sans-serif; margin: 0; padding: 0;">
            <div style="padding: 10px;">
              <div style="margin-bottom: 8px; font-weight: bold; font-size: 14px;">
                Hi, how can I help you?
              </div>
              <div style="font-size: 12px; color: #666;">
                I’m powered by AI, so surprises and mistakes are possible. Make sure to verify any generated code or suggestions,
                and share feedback so that we can learn and improve.
              </div>
            </div>
          </body>
        </html>
    """.trimIndent()

    // 如果需要头像或加载动画，可以保留以下资源；未使用也可以删除
    private val USER_AVATAR_URL = MyToolWindowFactory::class.java.getResource("/icon/user.png")?.toExternalForm()
    private val AI_AVATAR_URL = MyToolWindowFactory::class.java.getResource("/icon/ai.png")?.toExternalForm()
    private val SPINNER_IMAGE_URL = MyToolWindowFactory::class.java.getResource("/icon/spinner.gif")?.toExternalForm()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val mainPanel = JPanel(BorderLayout())

        // ========== 顶部栏 ==========

        val topPanel = JPanel(BorderLayout()).apply {
            background = Color(0xF5, 0xF5, 0xF5)
            border = BorderFactory.createEmptyBorder(8, 16, 8, 16)
            preferredSize = Dimension(400, 60)
        }

        val titleLabel = JLabel("<html><b style='font-size:14px;'>CodeGuardian</b></html>").apply {
            foreground = Color(0x33, 0x33, 0x33)
        }
        topPanel.add(titleLabel, BorderLayout.WEST)

        // “New Conversation” 链接，点击后清空当前对话，另存到 previousConversations
        val newConversationLabel = JLabel("<html><a href='#' style='text-decoration:none; font-size:12px;'>New Conversation</a></html>").apply {
            foreground = Color(0x00, 0x7A, 0xCC)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    // 若当前会话有内容，则存储到 previousConversations
                    if (conversationHistory.isNotEmpty()) {
                        previousConversations.add(conversationHistory.toList())
                    }
                    // 清空当前对话
                    conversationHistory.clear()
                    // 重置聊天窗口显示
                    chatPane.text = initialWelcomeHtml
                }
            })
        }
        topPanel.add(newConversationLabel, BorderLayout.EAST)

        mainPanel.add(topPanel, BorderLayout.NORTH)

        // ========== 聊天窗口 ==========

        chatPane = JEditorPane().apply {
            contentType = "text/html"
            isEditable = false
            text = initialWelcomeHtml  // 初始欢迎
        }

        val scrollPane = JBScrollPane(chatPane).apply {
            preferredSize = Dimension(400, 300)
        }

        // ========== 底部输入面板 ==========

        val inputField = JBTextField().apply {
            toolTipText = "Ask Copilot a question or type '/' for commands"
            text = ""
        }

        // 使用纯文本按钮，而非图标
        val sendButton = JButton("Send").apply {
            toolTipText = "Send your message"
        }

        // 回车键发送
        inputField.addActionListener {
            sendButton.doClick()
        }

        // 点击发送按钮逻辑
        sendButton.addActionListener {
            val userInput = inputField.text.trim()
            if (userInput.isNotEmpty()) {
                // 添加到对话记录
                conversationHistory.add(userInput to "Loading...")
                // 更新UI
                updateChatContent()
                // 清空输入框
                inputField.text = ""

                // 异步调用AI
                ApplicationManager.getApplication().executeOnPooledThread {
                    val aiResponse = AIService.getAIResponse(userInput)
                    // 替换掉 "Loading..." 为实际回复
                    conversationHistory[conversationHistory.lastIndex] = userInput to aiResponse

                    SwingUtilities.invokeLater {
                        updateChatContent()
                    }
                }
            }
        }

        val inputPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(8, 16, 8, 16)
            background = Color.WHITE
            add(inputField, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }

        mainPanel.add(scrollPane, BorderLayout.CENTER)
        mainPanel.add(inputPanel, BorderLayout.SOUTH)

        val content = ContentFactory.getInstance().createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    /**
     * 聊天窗控件，方便在方法中直接更新
     */
    private lateinit var chatPane: JEditorPane

    /**
     * 更新聊天内容 (重新生成HTML)
     */
    private fun updateChatContent() {
        chatPane.text = buildHtmlContent()
        // 滚动到底部
        chatPane.caretPosition = chatPane.document.length
    }

    /**
     * 根据 conversationHistory 构建HTML
     */
    private fun buildHtmlContent(): String {
        val sb = StringBuilder()
        sb.append(
            """
            <html>
              <body style="font-family: sans-serif; margin:0; padding:0;">
            """.trimIndent()
        )

        // 你可以选择保留最初的欢迎片段，也可以只显示对话
        // 这里保留最初的标题区
        sb.append(
            """
                <div style="padding: 10px; border-bottom: 1px solid #e1e4e8;">
                  <div style="margin-bottom: 8px; font-weight: bold; font-size: 14px;">
                    Hi <b>@Bloomwind</b>, how can I help you?
                  </div>
                  <div style="font-size: 12px; color: #666;">
                    I’m powered by AI, so surprises and mistakes are possible. Make sure to verify any generated code or suggestions,
                    and share feedback so that we can learn and improve.
                  </div>
                </div>
            """.trimIndent()
        )

        // 遍历对话记录
        conversationHistory.forEach { (user, ai) ->
            // ========== 用户消息 (右侧) ==========
            sb.append(
                """
                  <div style="display: flex; justify-content: flex-end; margin: 10px;">
                    <div style="background-color: #f0f0f0; border-radius: 6px; padding: 8px; max-width: 60%;">
                      <div style="font-size: 12px; font-weight: bold; color: #333; margin-bottom: 4px;">
            """.trimIndent()
            )
            if (USER_AVATAR_URL != null) {
                sb.append(
                    """<img src="$USER_AVATAR_URL" style="width: 20px; height: 20px; border-radius: 50%; vertical-align: middle; margin-right: 5px;" />"""
                )
            }
            sb.append(
                """You</div>
                      <div style="font-size: 13px; color: #333;">
                        ${escapeHtml(user)}
                      </div>
                    </div>
                  </div>
                """.trimIndent()
            )

            // ========== AI 回答 (左侧) ==========
            val aiContent = if (ai == "Loading...") {
                val spinnerHtml = if (SPINNER_IMAGE_URL != null) {
                    """<img src="$SPINNER_IMAGE_URL" alt="Loading" style="width:16px; height:16px; vertical-align:middle;" />"""
                } else {
                    """<span style="color:#999;">Loading...</span>"""
                }
                "$spinnerHtml <span style='margin-left:6px; color:#999;'>Thinking...</span>"
            } else {
                escapeHtml(ai)
            }

            sb.append(
                """
                  <div style="display: flex; justify-content: flex-start; margin: 10px;">
                    <div style="background-color: #f8f8f8; border-radius: 6px; padding: 8px; max-width: 60%;">
                      <div style="font-size: 12px; font-weight: bold; color: #555; margin-bottom: 4px;">
                """.trimIndent()
            )
            if (AI_AVATAR_URL != null) {
                sb.append(
                    """<img src="$AI_AVATAR_URL" style="width: 20px; height: 20px; border-radius: 50%; vertical-align: middle; margin-right: 5px;" />"""
                )
            }
            sb.append(
                """Copilot</div>
                      <div style="font-size: 13px; color: #333;">
                        $aiContent
                      </div>
                    </div>
                  </div>
                """.trimIndent()
            )
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    /**
     * 转义HTML
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
