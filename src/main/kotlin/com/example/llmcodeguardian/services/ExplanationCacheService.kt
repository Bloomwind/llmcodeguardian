package com.example.llmcodeguardian.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ExplanationCacheService {

    private val explanationMap = ConcurrentHashMap<Int, String>()

    companion object {
        fun getInstance(project: Project): ExplanationCacheService {
            return project.getService(ExplanationCacheService::class.java)
        }
    }

    fun putExplanationForCaret(offset: Int, explanation: String) {
        explanationMap[offset] = explanation
    }

    fun getExplanationForCaret(offset: Int): String? {
        return explanationMap[offset]
    }

    fun clearExplanation(offset: Int) {
        explanationMap.remove(offset)
    }

    fun clearAll() {
        explanationMap.clear()
    }
}
