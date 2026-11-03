package com.jarvis.assistant.router

import com.jarvis.assistant.domain.model.Attachment
import com.jarvis.assistant.domain.model.ModelCapability
import com.jarvis.assistant.domain.model.ModelTask

object CapabilityDetector {

    fun detectTask(prompt: String, attachment: Attachment?): ModelTask {
        // 1. Check attachments first
        if (attachment != null) {
            val mime = attachment.mimeType.lowercase()
            val name = attachment.name.lowercase()
            if (mime.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")) {
                return ModelTask.IMAGE_ANALYSIS
            }
            if (mime.contains("pdf") || mime.contains("document") || mime.contains("text") ||
                name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".txt")) {
                return ModelTask.DOCUMENT_ANALYSIS
            }
        }

        val text = prompt.lowercase().trim()

        // 2. Check for coding indicators
        val codeKeywords = listOf(
            "fun ", "def ", "class ", "import ", "const ", "let ", "val ", "var ",
            "interface ", "public static", "SELECT ", "print(", "console.log",
            "```", "código", "codigo", "algoritmo", "debug", "refatore", "compile"
        )
        if (codeKeywords.any { text.contains(it) }) {
            return ModelTask.CODING
        }

        // 3. Check for complex reasoning & logic
        val reasoningKeywords = listOf(
            "resolva o problema", "calcule", "prove que", "deduza", "passo a passo",
            "lógica", "paradoxo", "teorema", "raciocínio", "raciocinio", "x + y"
        )
        if (reasoningKeywords.any { text.contains(it) }) {
            return ModelTask.COMPLEX_REASONING
        }

        // 4. Check for creative writing
        val writingKeywords = listOf(
            "escreva uma história", "crie um poema", "redija", "escreva um conto",
            "poesia", "roteiro", "copywriting", "texto criativo"
        )
        if (writingKeywords.any { text.contains(it) }) {
            return ModelTask.CREATIVE_WRITING
        }

        // 5. Check for research
        val researchKeywords = listOf(
            "pesquise sobre", "artigo científico", "história de", "investigue",
            "estudo aprofundado", "resumo detalhado", "bibliografia"
        )
        if (researchKeywords.any { text.contains(it) }) {
            return ModelTask.RESEARCH
        }

        // 6. Check for quick questions (short queries, definitions)
        if (text.length < 35 && (text.startsWith("o que é") || text.startsWith("quem foi") || text.startsWith("quanto é") || text.startsWith("quando"))) {
            return ModelTask.QUICK_QUESTIONS
        }

        // Default to General Chat
        return ModelTask.GENERAL_CHAT
    }
}
