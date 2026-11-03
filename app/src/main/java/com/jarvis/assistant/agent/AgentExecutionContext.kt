package com.jarvis.assistant.agent

import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.ModelTask

data class AgentTaskStep(
    val stepIndex: Int,
    val agent: AgentRole,
    val assignedTask: ModelTask,
    val specificGoal: String,
    val targetLevel: AILevel,
    val promptInstruction: String
)

data class AgentStepResult(
    val stepIndex: Int,
    val agent: AgentRole,
    val summary: String,
    val content: String,
    val isSuccess: Boolean
)

data class AgentPlan(
    val isMultiAgentNeeded: Boolean,
    val executionGoal: String,
    val participatingAgents: List<AgentRole>,
    val steps: List<AgentTaskStep>,
    val explanation: String
)

data class MultiAgentProgressEvent(
    val activeAgent: AgentRole,
    val currentStep: Int,
    val totalSteps: Int,
    val statusMessage: String
)
