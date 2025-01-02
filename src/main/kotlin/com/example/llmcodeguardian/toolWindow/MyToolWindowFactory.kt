package com.example.llmcodeguardian.toolWindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.example.llmcodeguardian.services.AIService
import com.intellij.openapi.application.ApplicationManager
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

class MyToolWindowFactory : ToolWindowFactory, DumbAware {

    private val conversationMessages = mutableListOf(
        AIService.Message(role = "system", content = "You are a helpful assistant.")
    )

    private val previousConversations = mutableListOf<List<AIService.Message>>()

    private val initialWelcomeHtml = """
        <html>
          <body style="font-family: sans-serif; margin: 0; padding: 0;">
            <div style="padding: 10px;">
              <div style="margin-bottom: 8px; font-weight: bold; font-size: 14px;">
                你好，<b>开发者</b>，我可以如何帮助您？
              </div>
              <div style="font-size: 12px; color: #666;">
                我由 AI 驱动，因此可能会带来一些惊喜和错误。请务必验证生成的代码或建议，
                如果有任何反馈，欢迎分享以帮助我们不断改进。
              </div>
            </div>
          </body>
        </html>
    """.trimIndent()

    private val USER_AVATAR_URL = MyToolWindowFactory::class.java.getResource("/icon/user.png")?.toExternalForm()
    private val AI_AVATAR_URL = MyToolWindowFactory::class.java.getResource("/icon/ai.png")?.toExternalForm()
    private val SPINNER_IMAGE_URL = MyToolWindowFactory::class.java.getResource("/icon/spinner.gif")?.toExternalForm()

    // ========== 新增：标记是否暗色模式 ==========
    private var isDarkMode = false

    private lateinit var chatPane: JEditorPane

    // ========== 新增：这几个面板，需要在切换主题时修改颜色，所以提出来做成员变量 ==========
    private lateinit var topPanel: JPanel
    private lateinit var inputPanel: JPanel
    private lateinit var titleLabel: JLabel
    private lateinit var newConversationLabel: JLabel

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val mainPanel = JPanel(BorderLayout())

        // ========== 顶部栏 ==========
        topPanel = JPanel(BorderLayout()).apply {
            background = Color(0xF5, 0xF5, 0xF5)
            border = BorderFactory.createEmptyBorder(8, 16, 8, 16)
            preferredSize = Dimension(400, 60)
        }

        titleLabel = JLabel("<html><b style='font-size:14px;'>CodeGuardian</b></html>").apply {
            foreground = Color(0x33, 0x33, 0x33)
        }
        topPanel.add(titleLabel, BorderLayout.WEST)

        // “New Conversation” 链接
        newConversationLabel = JLabel("<html><a href='#' style='text-decoration:none; font-size:12px;'>新建对话</a></html>").apply {
            foreground = Color(0x00, 0x7A, 0xCC)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (conversationMessages.size > 1) {
                        previousConversations.add(conversationMessages.toList())
                    }
                    conversationMessages.retainAll { it.role == "system" }
                    chatPane.text = initialWelcomeHtml
                }
            })
        }

        // ========== 新增：切换主题的按钮 ==========
        val toggleThemeButton = JButton("切换明/暗主题").apply {
            toolTipText = "Switch between light/dark theme"
            addActionListener {
                isDarkMode = !isDarkMode
                applyThemeColors()     // 调整顶栏、底栏等颜色
                updateChatContent()    // 重新刷新聊天 HTML
            }
        }

        // 把“New Conversation”+“Toggle Theme”一起放到顶部右侧
        val rightPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(newConversationLabel)
            add(Box.createHorizontalStrut(16))
            add(toggleThemeButton)
            // 背景可设为透明或跟 topPanel 一样
            isOpaque = false
        }
        topPanel.add(rightPanel, BorderLayout.EAST)

        mainPanel.add(topPanel, BorderLayout.NORTH)

        // ========== 聊天窗口 ==========
        chatPane = JEditorPane().apply {
            contentType = "text/html"
            isEditable = false
            text = initialWelcomeHtml
            addHyperlinkListener(copyLinkListener)
        }
        val scrollPane = JBScrollPane(chatPane).apply {
            preferredSize = Dimension(500, 400)
        }

        // ========== 底部输入面板 ==========
        val inputField = JBTextField().apply {
            toolTipText = "Ask Copilot a question or type '/' for commands"
            text = ""
        }
        val sendButton = JButton("发送").apply {
            toolTipText = "Send your message"
        }
        inputField.addActionListener { sendButton.doClick() }
        sendButton.addActionListener {
            val userInput = inputField.text.trim()
            if (userInput.isNotEmpty()) {
                conversationMessages.add(AIService.Message(role = "user", content = userInput))
                conversationMessages.add(AIService.Message(role = "assistant", content = "Loading..."))
                updateChatContent()
                inputField.text = ""

                SwingUtilities.invokeLater {
                    callAiAsync()
                }
            }
        }
        inputPanel = JPanel(BorderLayout()).apply {
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
     * ========== 新增：切换顶栏/底栏等面板的颜色 ==========
     */
    private fun applyThemeColors() {
        if (isDarkMode) {
            // 顶部背景
            topPanel.background = Color(0x3C, 0x3F, 0x41)
            titleLabel.foreground = Color(0xDD, 0xDD, 0xDD)
            newConversationLabel.foreground = Color(0x2A, 0x92, 0xD6)

            // 底部
            inputPanel.background = Color(0x3C, 0x3F, 0x41)
        } else {
            // 恢复到默认
            topPanel.background = Color(0xF5, 0xF5, 0xF5)
            titleLabel.foreground = Color(0x33, 0x33, 0x33)
            newConversationLabel.foreground = Color(0x00, 0x7A, 0xCC)

            inputPanel.background = Color.WHITE
        }
        topPanel.repaint()
        inputPanel.repaint()
    }

    private fun callAiAsync() {
        val loadingIndex = conversationMessages.lastIndex
        ApplicationManager.getApplication().executeOnPooledThread {
            val responseText = AIService.getAnswerResponse(conversationMessages)
            if (conversationMessages.size - 1 == loadingIndex &&
                conversationMessages[loadingIndex].role == "assistant" &&
                conversationMessages[loadingIndex].content == "Loading..."
            ) {
                conversationMessages[loadingIndex] = AIService.Message(role = "assistant", content = responseText)
            }
            SwingUtilities.invokeLater {
                updateChatContent()
            }
        }
    }

    private fun updateChatContent() {
        chatPane.text = buildHtmlContent()
        chatPane.caretPosition = chatPane.document.length
    }

    /**
     * 这里改动最少，只是把原本写死的颜色用 if (isDarkMode) ... else ... 来区分
     */
    private fun buildHtmlContent(): String {
        // 如果暗色模式，替换相应的颜色
        val borderColor = if (isDarkMode) "#444444" else "#e1e4e8"
        val headerTextColor = if (isDarkMode) "#DDDDDD" else "#333333"
        val subTextColor = if (isDarkMode) "#BBBBBB" else "#666666"

        val userBubbleBg = if (isDarkMode) "#3A3A3A" else "#f0f0f0"
        val userTextColor = if (isDarkMode) "#EEEEEE" else "#333333"

        val aiBubbleBg = if (isDarkMode) "#2A2A2A" else "#f8f8f8"
        val aiTitleColor = if (isDarkMode) "#BBBBBB" else "#555555"
        val aiTextColor = if (isDarkMode) "#DDDDDD" else "#333333"

        val sb = StringBuilder()
        sb.append("<html><body style='font-family: sans-serif; margin:0; padding:0;'>")

        // 顶部提示
        sb.append(
            """
            <div style="padding: 10px; border-bottom: 1px solid #cccccc; background-color: #f9f9f9;">
              <div style="margin-bottom: 8px; font-weight: bold; font-size: 16px; color: #333333;">
                你好，<b>开发者</b>，我可以如何帮助您？
              </div>
              <div style="font-size: 14px; color: #666666; line-height: 1.5;">
                我由 AI 驱动，因此可能会带来一些惊喜和错误。请务必验证生成的代码或建议，
                如果有任何反馈，欢迎分享以帮助我们不断改进。
              </div>
            </div>
            """.trimIndent()
        )

        // 逐条渲染消息(从第1条开始，跳过 system)
        for (i in 1 until conversationMessages.size) {
            val msg = conversationMessages[i]
            if (msg.role == "user") {
                sb.append(renderUserMessage(msg.content, userBubbleBg, userTextColor))
            } else {
                val aiHtml = if (msg.content == "Loading...") {
                    val spinner = SPINNER_IMAGE_URL?.let {
                        """<img src="$it" alt="Loading" style="width:16px; height:16px; vertical-align:middle;" />"""
                    } ?: "<span style='color:#999;'>Loading...</span>"
                    "$spinner <span style='margin-left:6px; color:#999;'>Thinking...</span>"
                } else {
                    formatAiResponse(msg.content, isDarkMode)
                }
                sb.append(renderAssistantMessage(aiHtml, aiBubbleBg, aiTitleColor, aiTextColor))
            }
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    /**
     * 这里也稍做改动：将背景、文字颜色作为参数
     */
    private fun renderUserMessage(content: String, bubbleBg: String, textColor: String): String {
        val builder = StringBuilder()
        builder.append(
            """
            <div style="display: flex; justify-content: flex-end; margin: 10px;">
              <div style="background-color: $bubbleBg; border-radius: 6px; padding: 8px; max-width: 60%;">
                <div style="font-size: 12px; font-weight: bold; color: $textColor; margin-bottom: 4px;">
            """.trimIndent()
        )
        USER_AVATAR_URL?.let {
            builder.append("<img src=\"$it\" style=\"width: 20px; height: 20px; border-radius: 50%; vertical-align: middle; margin-right: 5px;\" />")
        }
        builder.append("You</div>")
        builder.append(
            """
                <div style="font-size: 13px; color: $textColor;">
                  ${escapeHtml(content)}
                </div>
              </div>
            </div>
            """.trimIndent()
        )
        return builder.toString()
    }

    private fun renderAssistantMessage(aiHtml: String, bubbleBg: String, titleColor: String, textColor: String): String {
        val builder = StringBuilder()
        builder.append(
            """
            <div style="display: flex; justify-content: flex-start; margin: 10px;">
              <div style="background-color: $bubbleBg; border-radius: 6px; padding: 8px; max-width: 60%;">
                <div style="font-size: 12px; font-weight: bold; color: $titleColor; margin-bottom: 4px;">
            """.trimIndent()
        )
        AI_AVATAR_URL?.let {
            builder.append("<img src=\"$it\" style=\"width: 20px; height: 20px; border-radius: 50%; vertical-align: middle; margin-right: 5px;\" />")
        }
        builder.append("Copilot</div>")
        builder.append(
            """
                <div style="font-size: 13px; color: $textColor;">
                  $aiHtml
                </div>
              </div>
            </div>
            """.trimIndent()
        )
        return builder.toString()
    }

    /**
     * 这里同理，根据是否暗色模式切换代码块背景/文字
     */
    private fun formatAiResponse(raw: String, isDark: Boolean): String {
        val escaped = escapeHtml(raw)
        val codeRegex = Regex("(?s)```(.*?)```")
        var index = 0

        // 不同主题的颜色
        val codeBg = if (isDark) "#3A3A3A" else "#f5f5f5"
        val codeBorder = if (isDark) "#666666" else "#dddddd"
        val codeText = if (isDark) "#EEEEEE" else "#333333"
        val copyBtnColor = if (isDark) "#AAAAAA" else "#007ACC"
        val copyBtnBg = if (isDark) "#444444" else "#e6f2ff"

        val replaced = codeRegex.replace(escaped) { match ->
            val codeContent = match.groups[1]?.value ?: ""
            index++
            val codeId = "codeblock-$index"
            val codeHtml = codeContent.replace("<", "&lt;").replace(">", "&gt;")

            """
            <div style="position: relative; border: 1px solid $codeBorder; border-radius:4px; margin: 8px 0; padding: 4px;">
              <pre style="margin:0; background:$codeBg; color:$codeText; padding: 6px; border-radius:4px;">
                <code id="$codeId" style="font-family: Consolas, monospace; font-size: 12px;">$codeHtml</code>
              </pre>
              <a href="copy:$codeId"
                 style="position:absolute; top:4px; right:4px; font-size:12px; color:$copyBtnColor;
                        text-decoration:none; border:1px solid $copyBtnColor; padding:2px 4px;
                        border-radius:4px; background-color:$copyBtnBg;">
                Copy
              </a>
            </div>
            """.trimIndent()
        }
        return replaced.replace("\n", "<br/>")
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private val copyLinkListener = HyperlinkListener { e ->
        if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            val desc = e.description
            if (desc.startsWith("copy:")) {
                val codeId = desc.substringAfter("copy:")
                val doc = chatPane.document
                val htmlDoc = doc as? javax.swing.text.html.HTMLDocument ?: return@HyperlinkListener
                val elem = htmlDoc.getElement(codeId) ?: return@HyperlinkListener

                val codeText = htmlDoc.getText(elem.startOffset, elem.endOffset - elem.startOffset)
                val sel = StringSelection(codeText)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
                JOptionPane.showMessageDialog(chatPane, "Code copied to clipboard!", "Copy", JOptionPane.INFORMATION_MESSAGE)
            }
        }
    }
}
