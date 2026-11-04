package com.jarvis.assistant

import com.jarvis.assistant.agent.AgentPlan
import com.jarvis.assistant.agent.AgentRole
import com.jarvis.assistant.agent.AgentTaskStep
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.ModelTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiAgentSynthesisTest {

    @Test
    fun testSubTaskSuccessDetection() {
        val validOutput = "Aqui está a análise detalhada dos dados fornecidos."
        val isError1 = validOutput.startsWith("Erro ") || validOutput.startsWith("Falha ")
        assertFalse(isError1)

        val errorOutput = "Erro 404: Modelo não localizado"
        val isError2 = errorOutput.startsWith("Erro ") || errorOutput.startsWith("Falha ")
        assertTrue(isError2)
    }

    @Test
    fun testRolesContainCoordinator() {
        val coordinator = AgentRole.COORDINATOR
        assertEquals("Jarvis Coordenador Central", coordinator.displayName)
        assertEquals("Jarvis Central", coordinator.shortBadge)
        assertEquals("⚡", coordinator.iconEmoji)
    }

    @Test
    fun testSubTaskDecompositionLimiting() {
        val maxSteps = 3
        val simulatedSteps = listOf(
            AgentTaskStep(1, AgentRole.RESEARCHER, ModelTask.RESEARCH, "Pesquisar documentação", AILevel.MEDIUM, "Instrução"),
            AgentTaskStep(2, AgentRole.PROGRAMMER, ModelTask.CODING, "Gerar código", AILevel.HIGH, "Instrução"),
            AgentTaskStep(3, AgentRole.ANALYST, ModelTask.COMPLEX_REASONING, "Validar cobertura", AILevel.HIGH, "Instrução"),
            AgentTaskStep(4, AgentRole.CREATIVE, ModelTask.CREATIVE_WRITING, "Escrever documentação", AILevel.MEDIUM, "Instrução")
        )

        val plan = AgentPlan(
            isMultiAgentNeeded = true,
            executionGoal = "Meta composta",
            participatingAgents = listOf(AgentRole.RESEARCHER, AgentRole.PROGRAMMER, AgentRole.ANALYST, AgentRole.COORDINATOR),
            steps = simulatedSteps.take(maxSteps),
            explanation = "Decomposição em 3 passos com limite estrito anti-loop"
        )

        assertEquals(3, plan.steps.size)
        assertTrue(plan.steps.size <= maxSteps)
        assertTrue(plan.participatingAgents.contains(AgentRole.COORDINATOR))
    }
}
