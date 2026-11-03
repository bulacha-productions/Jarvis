package com.jarvis.assistant.ui.wizard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvis.assistant.ai.AIProviderConfig
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.AIModelInfo
import com.jarvis.assistant.domain.model.ModelTask
import com.jarvis.assistant.domain.model.TaskAssignment
import com.jarvis.assistant.ui.theme.ElectricOrange

enum class WizardStage {
    IDENTIFICATION,
    QUESTIONNAIRE,
    CONFIRMATION
}

@Composable
fun ModelConfigWizardDialog(
    providers: List<AIProviderConfig>,
    currentAssignments: Map<String, TaskAssignment>,
    onSaveAssignments: (Map<String, TaskAssignment>) -> Unit,
    onDismiss: () -> Unit
) {
    var stage by remember { mutableStateOf(if (currentAssignments.isNotEmpty()) WizardStage.CONFIRMATION else WizardStage.IDENTIFICATION) }
    var currentQuestionIndex by remember { mutableStateOf(0) }
    val userPreferences = remember { mutableStateMapOf<ModelTask, AILevel>() }
    val activeAssignments = remember { mutableStateMapOf<String, TaskAssignment>().apply { putAll(currentAssignments) } }

    val allTasks = remember { ModelTask.values().toList() }
    val currentTask = allTasks.getOrNull(currentQuestionIndex) ?: allTasks[0]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ElectricOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = ElectricOrange)
                        }
                        Column {
                            Text(
                                text = "Questionário Inteligente",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (stage) {
                                    WizardStage.IDENTIFICATION -> "Etapa 1: Identificação de Modelos"
                                    WizardStage.QUESTIONNAIRE -> "Etapa 2: Pergunta ${currentQuestionIndex + 1} de ${allTasks.size}"
                                    WizardStage.CONFIRMATION -> "Etapa 3: Resumo & Confirmação"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                // Content according to current stage
                Box(modifier = Modifier.weight(1f)) {
                    when (stage) {
                        WizardStage.IDENTIFICATION -> {
                            IdentificationStageView(
                                providers = providers,
                                onProceed = { stage = WizardStage.QUESTIONNAIRE }
                            )
                        }
                        WizardStage.QUESTIONNAIRE -> {
                            QuestionnaireStepView(
                                task = currentTask,
                                selectedLevel = userPreferences[currentTask] ?: currentTask.defaultLevel,
                                onSelectLevel = { level -> userPreferences[currentTask] = level },
                                onNext = {
                                    if (currentQuestionIndex < allTasks.size - 1) {
                                        currentQuestionIndex++
                                    } else {
                                        // Compute distribution
                                        computeDistribution(providers, userPreferences, activeAssignments)
                                        stage = WizardStage.CONFIRMATION
                                    }
                                },
                                onPrevious = {
                                    if (currentQuestionIndex > 0) {
                                        currentQuestionIndex--
                                    } else {
                                        stage = WizardStage.IDENTIFICATION
                                    }
                                }
                            )
                        }
                        WizardStage.CONFIRMATION -> {
                            ConfirmationStageView(
                                assignments = activeAssignments,
                                providers = providers,
                                onUpdateAssignment = { taskId, newAssign ->
                                    activeAssignments[taskId] = newAssign
                                },
                                onRestartQuestionnaire = {
                                    currentQuestionIndex = 0
                                    stage = WizardStage.QUESTIONNAIRE
                                },
                                onConfirm = {
                                    onSaveAssignments(activeAssignments.toMap())
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun computeDistribution(
    providers: List<AIProviderConfig>,
    userPrefs: Map<ModelTask, AILevel>,
    outAssignments: MutableMap<String, TaskAssignment>
) {
    ModelTask.values().forEach { task ->
        val level = userPrefs[task] ?: task.defaultLevel
        val (providerId, modelName, reason) = when (task) {
            ModelTask.GENERAL_CHAT -> Triple("gemini", "gemini-2.5-flash", "Equilíbrio ideal entre velocidade, naturalidade de diálogo e fluência.")
            ModelTask.QUICK_QUESTIONS -> Triple("gemini", "gemini-1.5-flash", "Baixíssima latência para consultas e comandos rápidos.")
            ModelTask.CODING -> Triple("claude", "claude-3-5-sonnet", "Líder em precisão sintática, refatoração e depuração de código.")
            ModelTask.COMPLEX_REASONING -> Triple("openai", "o3-mini", "Encadeamento dedutivo, resolução lógica e matemática avançada.")
            ModelTask.RESEARCH -> Triple("gemini", "gemini-2.5-flash", "Janela ultra-longa de contexto para síntese de documentos densos.")
            ModelTask.CREATIVE_WRITING -> Triple("claude", "claude-3-5-sonnet", "Prosa expressiva e vocabulário narrativo apurado.")
            ModelTask.IMAGE_ANALYSIS -> Triple("gemini", "gemini-2.5-flash", "Visão computacional multimodal de alta sensibilidade e OCR.")
            ModelTask.DOCUMENT_ANALYSIS -> Triple("gemini", "gemini-2.5-pro", "Compreensão aprofundada de arquivos densos, tabelas e PDFs.")
        }
        outAssignments[task.id] = TaskAssignment(task, providerId, modelName, level, reason)
    }
}

@Composable
private fun IdentificationStageView(
    providers: List<AIProviderConfig>,
    onProceed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Mapeamento dos Provedores & Modelos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "O Jarvis analisará automaticamente as capacidades de cada modelo (Texto, Código, Raciocínio, Imagens, Documentos) para direcionar suas solicitações sem que você precise digitar comandos como @GPT ou @Gemini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(providers) { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = p.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (p.apiKey.isNotBlank()) "Chave configurada (${p.availableModels.size} modelos)" else "Chave não configurada",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (p.apiKey.isNotBlank()) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (p.apiKey.isNotBlank()) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        Button(
            onClick = onProceed,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricOrange, contentColor = Color.Black),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Iniciar Questionário de Preferências", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuestionnaireStepView(
    task: ModelTask,
    selectedLevel: AILevel,
    onSelectLevel: (AILevel) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ElectricOrange
            )
            Text(
                text = task.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Qual é a sua prioridade de desempenho para esta responsabilidade?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LevelOptionCard(
                    level = AILevel.LIGHT,
                    title = "LIGHT — Baixa Latência",
                    description = "Prioriza respostas ultrarrápidas, menor tempo de espera e economia de cota.",
                    isSelected = selectedLevel == AILevel.LIGHT,
                    onClick = { onSelectLevel(AILevel.LIGHT) }
                )

                LevelOptionCard(
                    level = AILevel.MEDIUM,
                    title = "MEDIUM — Equilíbrio Inteligente",
                    description = "Padrão oficial do Jarvis. O melhor ponto entre velocidade e profundidade de resposta.",
                    isSelected = selectedLevel == AILevel.MEDIUM,
                    onClick = { onSelectLevel(AILevel.MEDIUM) }
                )

                LevelOptionCard(
                    level = AILevel.HIGH,
                    title = "HIGH — Máxima Capacidade",
                    description = "Utiliza o modelo mais potente disponível para máxima precisão técnica.",
                    isSelected = selectedLevel == AILevel.HIGH,
                    onClick = { onSelectLevel(AILevel.HIGH) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onPrevious) {
                Text(text = "Voltar", color = MaterialTheme.colorScheme.onSurface)
            }

            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricOrange, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Avançar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LevelOptionCard(
    level: AILevel,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) ElectricOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) ElectricOrange else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = ElectricOrange)
        )
        Column {
            Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConfirmationStageView(
    assignments: Map<String, TaskAssignment>,
    providers: List<AIProviderConfig>,
    onUpdateAssignment: (String, TaskAssignment) -> Unit,
    onRestartQuestionnaire: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Distribuição Recomendada de Modelos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Revise e personalize a atribuição de modelos. O Jarvis roteará as mensagens automaticamente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(assignments.values.toList()) { assign ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = assign.task.title,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Surface(
                                    color = ElectricOrange.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${assign.providerId.uppercase()} • ${assign.level}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricOrange,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = "Modelo: ${assign.modelName}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (assign.recommendationReason.isNotBlank()) {
                                Text(
                                    text = "💡 ${assign.recommendationReason}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricOrange, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Confirmar e Ativar Distribuição", fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = onRestartQuestionnaire,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Refazer Questionário do Início", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
