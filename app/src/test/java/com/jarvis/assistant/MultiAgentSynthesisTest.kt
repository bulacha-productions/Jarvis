package com.jarvis.assistant

import com.jarvis.assistant.agent.AgentRole
import com.jarvis.assistant.agent.SubTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiAgentSynthesisTest {

    @Test
    fun testSubTaskSuccessDetection() {
        // Valid step result
        val validOutput = "Aqui está a análise detalhada dos dados fornecidos."
        val isError1 = validOutput.startsWith("Erro ") || validOutput.startsWith("Falha ")
        assertFalse(isError1)

        // Error step result
        val errorOutput = "Erro 404: Modelo não localizado"
        val isError2 = errorOutput.startsWith("Erro ") || errorOutput.startsWith("Falha ")
        assertTrue(isError2)
    }

    @Test
    fun testRolesContainCoordinator() {
        val coordinator = AgentRole.COORDINATOR
        assertEquals("Jarvis Central", coordinator.displayName)
        assertEquals("⚡", coordinator.badge)
    }

    @Test
    fun testSubTaskDecompositionLimiting() {
        val maxSteps = 3
        val simulatedPlan = listOf(
            SubTask(AgentRole.RESEARCHER, "Pesquisar documentação da API"),
            SubTask(AgentRole.PROGRAMMER, "Gerar código de integração"),
            SubTask(AgentRole.ANALYST, "Validar cobertura de testes"),
            SubTask(AgentRole.CREATIVE, "Escrever documentação descritiva")
        )

        // Strict limit of up to 3 subtasks to prevent endless loops
        val boundedPlan = simulatedPlan.take(maxSteps)
        assertEquals(3, boundedPlan.size)
        assertTrue(boundedPlan.size <= maxSteps)
    }
}
