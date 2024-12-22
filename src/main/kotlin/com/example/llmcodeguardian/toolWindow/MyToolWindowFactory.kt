package com.example.llmcodeguardian.toolWindow

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
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.BoxLayout

class MyToolWindowFactory : ToolWindowFactory, DumbAware {

    // Stores conversation history
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())

        // Chat pane to display messages
        val chatPane = JEditorPane().apply {
            contentType = "text/html"
            isEditable = false
            text = "<html><body style='font-family: sans-serif; margin:0; padding:0;'></body></html>"
        }

        val scrollPane = JBScrollPane(chatPane).apply {
            preferredSize = Dimension(400, 300)
        }

        val inputField = JBTextField().apply {
            toolTipText = "Type your question here"
        }

        val sendButton = JButton("Send").apply {
            toolTipText = "Click to send your message"
        }

        sendButton.addActionListener {
            val userInput = inputField.text.trim()
            if (userInput.isNotEmpty()) {
                conversationHistory.add(userInput to "Loading...")
                updateChatContent(chatPane)
                inputField.text = ""

                // Asynchronous API call
                ApplicationManager.getApplication().executeOnPooledThread {
                    val aiResponse = getAIResponse(userInput)

                    // Update message with AI response
                    conversationHistory[conversationHistory.lastIndex] = userInput to aiResponse

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
        return AIService.getAIResponse(userInput)
    }

    private fun updateChatContent(chatPane: JEditorPane) {
        val htmlContent = buildHtmlContent()
        chatPane.text = htmlContent
        chatPane.caretPosition = chatPane.document.length
    }

    private fun buildHtmlContent(): String {
        val sb = StringBuilder()
        sb.append("<html><body style='font-family: sans-serif;'>")

        conversationHistory.forEach { (user, ai) ->
            sb.append("<div style='margin:10px; padding:10px; border:1px solid #ccc; border-radius:8px;'>")
            sb.append("<b style='color: #007ACC;'>You:</b> ${escapeHtml(user)}")
            sb.append("</div>")

            sb.append("<div style='margin:10px; padding:10px; border:1px solid #ccc; border-radius:8px; background-color:#f9f9f9;'>")
            sb.append("<b style='color: #FF5722;'>AI:</b> ${escapeHtml(ai)}")
            sb.append("</div>")
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
