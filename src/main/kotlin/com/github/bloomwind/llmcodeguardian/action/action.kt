package com.github.bloomwind.llmcodeguardian.action


import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class AskAIAction : AnAction("Ask AI", "Simulates an AI response", null) {
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showMessageDialog(
            e.project,
            "This is a simulated response from AI.",
            "Ask AI",
            Messages.getInformationIcon()
        )
    }
}
