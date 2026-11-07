package com.jarvis.assistant.financial

import com.jarvis.assistant.domain.model.AIModelInfo
import com.jarvis.assistant.domain.model.FinancialPolicy

data class BudgetCheckResult(
    val isAllowed: Boolean,
    val reason: String,
    val currentSpend: Double,
    val monthlyBudget: Double,
    val estimatedCost: Double
)

object FinancialManager {

    /**
     * Regra inegociável: no modo FREE_ONLY, jamais poderá ser selecionado um modelo pago.
     */
    fun isModelAllowed(
        model: AIModelInfo,
        policy: FinancialPolicy,
        currentSpend: Double = 0.0,
        monthlyBudget: Double = 5.0,
        estimatedCost: Double = 0.0
    ): Boolean {
        return when (policy) {
            FinancialPolicy.FREE_ONLY -> {
                model.isFree || model.id.endsWith(":free") || (model.promptPricePerMillion == 0.0 && model.completionPricePerMillion == 0.0)
            }
            FinancialPolicy.PRIORITIZE_FREE -> {
                // Permite gratuitos diretamente; pagos apenas se dentro do orçamento
                if (model.isFree || model.id.endsWith(":free")) true
                else (currentSpend + estimatedCost <= monthlyBudget)
            }
            FinancialPolicy.FREE_AND_PAID -> {
                if (model.isFree || model.id.endsWith(":free")) true
                else (currentSpend + estimatedCost <= monthlyBudget)
            }
        }
    }

    /**
     * Estimativa prévia de custo em USD com base no tamanho do prompt e expectativa de tokens.
     */
    fun estimateCostUsd(model: AIModelInfo, promptLength: Int, expectedCompletionTokens: Int = 800): Double {
        if (model.isFree || model.id.endsWith(":free")) return 0.0
        val promptTokens = (promptLength / 3.8).toInt().coerceAtLeast(1)
        val promptPricePerMillion = model.promptPricePerMillion ?: 1.0
        val completionPricePerMillion = model.completionPricePerMillion ?: 3.0

        val promptCost = (promptTokens.toDouble() / 1_000_000.0) * promptPricePerMillion
        val completionCost = (expectedCompletionTokens.toDouble() / 1_000_000.0) * completionPricePerMillion
        return promptCost + completionCost
    }

    fun validateBudget(
        estimatedCost: Double,
        currentSpend: Double,
        monthlyBudget: Double,
        policy: FinancialPolicy
    ): BudgetCheckResult {
        if (policy == FinancialPolicy.FREE_ONLY) {
            if (estimatedCost > 0.0) {
                return BudgetCheckResult(
                    isAllowed = false,
                    reason = "Política de uso restrita a modelos 100% gratuitos. Requisição paga bloqueada.",
                    currentSpend = currentSpend,
                    monthlyBudget = monthlyBudget,
                    estimatedCost = estimatedCost
                )
            }
            return BudgetCheckResult(true, "Modelo gratuito aprovado.", currentSpend, monthlyBudget, 0.0)
        }

        if (currentSpend + estimatedCost > monthlyBudget) {
            return BudgetCheckResult(
                isAllowed = false,
                reason = "Limite de orçamento atingido (Gasto atual: US$ ${String.format("%.2f", currentSpend)} / Orçamento: US$ ${String.format("%.2f", monthlyBudget)}).",
                currentSpend = currentSpend,
                monthlyBudget = monthlyBudget,
                estimatedCost = estimatedCost
            )
        }

        return BudgetCheckResult(
            isAllowed = true,
            reason = "Custo estimado (US$ ${String.format("%.4f", estimatedCost)}) aprovado dentro do orçamento.",
            currentSpend = currentSpend,
            monthlyBudget = monthlyBudget,
            estimatedCost = estimatedCost
        )
    }
}
