package org.jetbrains.plugins.template.toolWindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea
import org.jetbrains.plugins.template.service.AIService

class MyToolWindowFactory : ToolWindowFactory, DumbAware {

    // 用于存储对话历史的变量
    private val conversationHistory = mutableListOf<Pair<String, String>>() // (用户输入, AI 响应)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 创建主面板
        val panel = JPanel(BorderLayout())

        // 对话显示区域
        val chatArea = JTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
        }

        // 滚动面板包裹对话区域
        val scrollPane = JBScrollPane(chatArea)

        // 文本输入框
        val inputField = JBTextField().apply {
            toolTipText = "Type your question here"
        }

        // 提交按钮
        val sendButton = JButton("Send").apply {
            toolTipText = "Click to send your message"
        }

        // 按钮点击事件监听器
        sendButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent?) {
                val userInput = inputField.text.trim()
                if (userInput.isNotEmpty()) {
                    // 添加用户输入到对话历史
                    val aiResponse = getAIResponse(userInput)
                    conversationHistory.add(userInput to aiResponse)

                    // 更新对话区域
                    chatArea.append("You: $userInput\n")
                    chatArea.append("AI: $aiResponse\n\n")

                    // 清空输入框
                    inputField.text = ""
                }
            }
        })

        // 输入面板（输入框 + 按钮）
        val inputPanel = JPanel(BorderLayout()).apply {
            add(inputField, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }

        // 将组件添加到主面板
        panel.add(scrollPane, BorderLayout.CENTER)
        panel.add(inputPanel, BorderLayout.SOUTH)

        // 将主面板嵌入到工具窗口
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }


    private fun getAIResponse(userInput: String): String {
        return AIService.getAIResponse(conversationHistory, userInput)
    }
}
