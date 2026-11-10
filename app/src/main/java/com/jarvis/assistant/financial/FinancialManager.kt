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

/**
 * Gestor financeiro e de restrição de custos do Jarvis.
 * 
 * V3.1.1: Preço desconhecido NUNCA é classificado como gratuito.
 * No modo FREE_ONLY, apenas modelos com gratuidade comprovada oficialmente são autorizados.
 */
object FinancialManager {

    fun isModelAllowed(
        model: AIModelInfo,
        policy: FinancialPolicy,
        currentSpend: Double = 0.0,
        monthlyBudget: Double = 5.0,
        estimatedCost: Double = 0.0
    ): Boolean {
        val isConfirmedFree = model.isFree || model.id.endsWith(":free") ||
                (model.promptPricePerMillion == 0.0 && model.completionPricePerMillion == 0.0)

        return when (policy) {
            FinancialPolicy.FREE_ONLY -> {
                // Regra estrita: Preço desconhecido ou não confirmado é bloqueado em FREE_ONLY
                isConfirmedFree
            }
            FinancialPolicy.PRIORITIZE_FREE -> {
                if (isConfirmedFree) true
                else (currentSpend + estimatedCost <= monthlyBudget)
            }
            FinancialPolicy.FREE_AND_PAID -> {
                if (isConfirmedFree) true
                else (currentSpend + estimatedCost <= monthlyBudget)
            }
        }
    }

    /**
     * Estimativa prévia de custo em USD. Modelos com preço desconhecido utilizam taxa padrão de proteção.
     */
    fun estimateCostUsd(model: AIModelInfo, promptLength: Int, expectedCompletionTokens: Int = 800): Double {
        val isConfirmedFree = model.isFree || model.id.endsWith(":free") ||
                (model.promptPricePerMillion == 0.0 && model.completionPricePerMillion == 0.0)
        if (isConfirmedFree) return 0.0

        val promptTokens = (promptLength / 3.8).toInt().coerceAtLeast(1)
        // Se o preço for desconhecido, utiliza tarifa de proteção de US$ 2.00 / 1M prompt e US$ 6.00 / 1M completion
        val promptPricePerMillion = model.promptPricePerMillion ?: 2.0
        val completionPricePerMillion = model.completionPricePerMillion ?: 6.0

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
                    reason = "Política restrita a modelos 100% gratuitos. Requisição com custo ou preço não confirmado bloqueada.",
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
                reason = "Limite de orçamento atingido (Gasto atual: US$ " + String.format("%.2f", currentSpend) + " / Orçamento: US$ " + String.format("%.2f", monthlyBudget) + ").",
                currentSpend = currentSpend,
                monthlyBudget = monthlyBudget,
                estimatedCost = estimatedCost
            )
        }

        return BudgetCheckResult(
            isAllowed = true,
            reason = "Custo estimado (US$ " + String.format("%.4f", estimatedCost) + ") aprovado dentro do orçamento mensal.",
            currentSpend = currentSpend,
            monthlyBudget = monthlyBudget,
            estimatedCost = estimatedCost
        )
    }
}
