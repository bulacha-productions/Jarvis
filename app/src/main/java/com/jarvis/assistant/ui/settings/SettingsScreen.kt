package com.jarvis.assistant.ui.settings


import com.jarvis.assistant.domain.model.RoutingMode
import com.jarvis.assistant.domain.model.MultiAgentMode
import com.jarvis.assistant.domain.model.FinancialPolicy
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ai.AIProviderConfig
import com.jarvis.assistant.ai.ProviderStatus
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.AIModelInfo
import com.jarvis.assistant.domain.model.ModelCapability
import com.jarvis.assistant.ui.components.JarvisMascot
import com.jarvis.assistant.JarvisVersion
import androidx.compose.ui.draw.scale
import androidx.compose.material3.RadioButton
import com.jarvis.assistant.ui.theme.ElectricOrange
import com.jarvis.assistant.ui.wizard.ModelConfigWizardDialog
import com.jarvis.assistant.viewmodels.SettingsViewModel
import com.jarvis.assistant.audio.JarvisVoiceState
import com.jarvis.assistant.ui.settings.VoiceDiagnosticsDialog
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val isMultiAgentEnabled by viewModel.isMultiAgentEnabled.collectAsState()
    val multiAgentMode by viewModel.multiAgentMode.collectAsState()
    val routingMode by viewModel.routingMode.collectAsState()
    val preferredProvider by viewModel.preferredProvider.collectAsState()
    val financialPolicy by viewModel.financialPolicy.collectAsState()
    val monthlyBudget by viewModel.monthlyBudget.collectAsState()
    val currentSpend by viewModel.currentSpend.collectAsState()
    val isTtsEnabled by viewModel.isTtsEnabled.collectAsState()
    val isTtsAutoSpeak by viewModel.isTtsAutoSpeak.collectAsState()
    val aiLevel by viewModel.aiLevel.collectAsState()
    val manualProviderOverride by viewModel.manualProviderOverride.collectAsState()
    val enabledProviders by viewModel.enabledProviders.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()
    val isWakeWordEnabled by viewModel.isWakeWordEnabled.collectAsState()
    val isWakeWordSpokenFeedback by viewModel.isWakeWordSpokenFeedback.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val isVoiceServiceRunning by viewModel.isVoiceServiceRunning.collectAsState()
    val isVoiceDiagnosticsOpen by viewModel.isVoiceDiagnosticsOpen.collectAsState()
    val diagnosticsItems by viewModel.diagnosticsItems.collectAsState()


    var providerToAddModel by remember { mutableStateOf<AIProviderConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Configurações", fontWeight = FontWeight.Bold)
                        Surface(
                            color = ElectricOrange.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "v${JarvisVersion.VERSION_NAME}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricOrange,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // SEÇÃO 1: SISTEMA MULTIAGENTE V2.0.1
            item {
                SectionHeader(title = "SISTEMA MULTIAGENTE (V${JarvisVersion.VERSION_NAME})", icon = Icons.Default.Psychology)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Coordenação Inteligente Multiagente",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = if (isMultiAgentEnabled) "Ativo: Decomposição por especialistas com síntese final obrigatória pelo Jarvis Central" else "Desativado: Execução padrão direta",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isMultiAgentEnabled) ElectricOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isMultiAgentEnabled,
                                onCheckedChange = { viewModel.toggleMultiAgent(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = ElectricOrange
                                )
                            )
                        }
                        // Seleção de Quantidade de Especialistas (V3.0.0)
                        Text(
                            text = "Quantidade de Especialistas em Colaboração:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = multiAgentMode == MultiAgentMode.ONE_AGENT,
                                onClick = { viewModel.setMultiAgentMode(MultiAgentMode.ONE_AGENT) },
                                label = { Text("1 Especialista", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = multiAgentMode == MultiAgentMode.TWO_AGENTS,
                                onClick = { viewModel.setMultiAgentMode(MultiAgentMode.TWO_AGENTS) },
                                label = { Text("2 Especialistas", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = multiAgentMode == MultiAgentMode.AUTOMATIC,
                                onClick = { viewModel.setMultiAgentMode(MultiAgentMode.AUTOMATIC) },
                                label = { Text("Automático", style = MaterialTheme.typography.labelSmall) }
                            )
                        }

                        // Agentes da Equipe Jarvis
                        Text(
                            text = "Agentes Especializados Disponíveis:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        AgentPillRow("⚡ Jarvis Central", "Orquestração & Síntese Final", ElectricOrange)
                        AgentPillRow("💻 Programador", "Engenharia de Software, Código & Debug", Color(0xFF4FC3F7))
                        AgentPillRow("🔍 Pesquisador", "Investigação Factual & Síntese Analítica", Color(0xFF81C784))
                        AgentPillRow("✨ Criativo", "Narrativas, Ideação & Redação Expressiva", Color(0xFFBA68C8))
                        AgentPillRow("📊 Analítico", "Raciocínio Matemático, Lógica & Validação", Color(0xFFFFB74D))
                    }
                }
            }

            // SEÇÃO NOVO: JARVIS SMART ROUTER (V3.0.0)
            item {
                SectionHeader(title = "JARVIS SMART ROUTER (V3.0.0)", icon = Icons.Default.SmartToy)
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Modo de Roteamento Inteligente:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RoutingMode.values().forEach { mode ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.setRoutingMode(mode) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = routingMode == mode,
                                        onClick = { viewModel.setRoutingMode(mode) },
                                        colors = RadioButtonDefaults.colors(selectedColor = ElectricOrange)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(text = mode.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        Text(text = mode.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SEÇÃO NOVO: POLÍTICA FINANCEIRA & PROTEÇÃO DE CUSTOS
            item {
                SectionHeader(title = "POLÍTICA FINANCEIRA & PROTEÇÃO DE CUSTOS", icon = Icons.Default.AttachMoney)
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FinancialPolicy.values().forEach { pol ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setFinancialPolicy(pol) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = financialPolicy == pol,
                                    onClick = { viewModel.setFinancialPolicy(pol) },
                                    colors = RadioButtonDefaults.colors(selectedColor = ElectricOrange)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(text = pol.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text(text = pol.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Teto Orçamentário Mensal:", style = MaterialTheme.typography.labelMedium)
                            Text(text = "US$ " + String.format("%.2f", monthlyBudget), fontWeight = FontWeight.Bold, color = ElectricOrange)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Gasto Acumulado Estimado:", style = MaterialTheme.typography.labelMedium)
                            Text(text = "US$ " + String.format("%.4f", currentSpend), fontWeight = FontWeight.Bold, color = if (currentSpend >= monthlyBudget) Color.Red else Color.Green)
                        }
                    }
                }
            }

            // SEÇÃO 5: SISTEMA DE VOZ & ATIVAÇÃO POR PALAVRA-CHAVE ("JARVIS")
            item {
                SectionHeader(title = "SISTEMA DE VOZ & WAKE WORD (\"JARVIS\")", icon = Icons.Default.RecordVoiceOver)
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Status Card em Tempo Real
                        val (statusBadgeBg, statusBadgeFg, statusIcon) = when (voiceState) {
                            is JarvisVoiceState.LISTENING_FOR_WAKE_WORD -> Triple(Color(0xFF22C55E).copy(alpha = 0.15f), Color(0xFF22C55E), Icons.Default.Hearing)
                            is JarvisVoiceState.WAKE_WORD_DETECTED -> Triple(ElectricOrange.copy(alpha = 0.2f), ElectricOrange, Icons.Default.CheckCircle)
                            is JarvisVoiceState.LISTENING_FOR_COMMAND -> Triple(ElectricOrange.copy(alpha = 0.2f), ElectricOrange, Icons.Default.Mic)
                            is JarvisVoiceState.PROCESSING -> Triple(Color(0xFF3B82F6).copy(alpha = 0.2f), Color(0xFF3B82F6), Icons.Default.Sync)
                            is JarvisVoiceState.SPEAKING -> Triple(Color(0xFF06B6D4).copy(alpha = 0.2f), Color(0xFF06B6D4), Icons.Default.VolumeUp)
                            is JarvisVoiceState.PAUSED -> Triple(Color(0xFFF59E0B).copy(alpha = 0.2f), Color(0xFFF59E0B), Icons.Default.Pause)
                            is JarvisVoiceState.ERROR -> Triple(Color(0xFFEF4444).copy(alpha = 0.2f), Color(0xFFEF4444), Icons.Default.Error)
                            else -> Triple(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.MicOff)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(statusBadgeBg, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = statusIcon, contentDescription = null, tint = statusBadgeFg, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text(text = "Estado da Escuta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = voiceState.label, fontWeight = FontWeight.Bold, color = statusBadgeFg, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }

                                Surface(
                                    color = if (isVoiceServiceRunning) Color(0xFF22C55E).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (isVoiceServiceRunning) "SERVIÇO ATIVO" else "SERVIÇO PARADO",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isVoiceServiceRunning) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Toggle Ativação Automática
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Ativação Contínua por Palavra-Chave", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Permite chamar o assistente dizendo \"Jarvis\" mesmo com o app em segundo plano ou tela bloqueada.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isWakeWordEnabled,
                                onCheckedChange = { viewModel.setWakeWordEnabled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = ElectricOrange)
                            )
                        }

                        // Informações do Reconhecedor
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = "Palavra-Chave:", style = MaterialTheme.typography.labelMedium)
                                Surface(color = ElectricOrange.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = "\"Jarvis\"",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricOrange,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = "Idioma:", style = MaterialTheme.typography.labelMedium)
                                Text(text = "Português (Brasil)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Respostas Faladas por Voz (TTS)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Respostas Faladas em Voz Alta (TTS)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(text = "Lê a resposta do assistente em português brasileiro após processar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isTtsEnabled,
                                onCheckedChange = { viewModel.setTtsEnabled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = ElectricOrange)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Confirmação Verbal (\"Estou ouvindo\")", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(text = "Jarvis responde verbalmente ao ouvir a palavra-chave.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isWakeWordSpokenFeedback,
                                onCheckedChange = { viewModel.setWakeWordSpokenFeedback(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = ElectricOrange)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Botões de Ação do Serviço
                        Text(text = "Controles Rápidos do Serviço:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isVoiceServiceRunning || voiceState is JarvisVoiceState.INACTIVE || voiceState is JarvisVoiceState.PAUSED) {
                                Button(
                                    onClick = { viewModel.startVoiceListening() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricOrange),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (voiceState is JarvisVoiceState.PAUSED) "Retomar" else "Iniciar", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.pauseVoiceListening() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Pausar")
                                }
                            }

                            if (isVoiceServiceRunning) {
                                Button(
                                    onClick = { viewModel.stopVoiceListening() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.85f)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Encerrar", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Testes e Diagnóstico
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.testTtsQuick() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Testar TTS", style = MaterialTheme.typography.labelSmall)
                            }

                            Button(
                                onClick = {
                                    viewModel.setVoiceDiagnosticsOpen(true)
                                    viewModel.runVoiceDiagnostics()
                                },
                                modifier = Modifier.weight(1.3f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricOrange)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Diagnóstico (10 Testes)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            
            // SEÇÃO 2: PROVEDORES DE IA & APIS
            item {
                SectionHeader(title = "PROVEDORES DE IA & APIS", icon = Icons.Default.Key)
            }

            item {
                Text(
                    text = "As credenciais são protegidas com criptografia de hardware via Android Keystore (AES-256 GCM sem armazenamento em texto puro).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    settingsState.providers.forEach { provider ->
                        ProviderCard(
                            provider = provider,
                            onSaveKey = { newKey -> viewModel.saveProviderKey(provider.id, newKey) },
                            onToggleEnabled = { isEnabled -> viewModel.toggleProviderEnabled(provider.id, isEnabled) },
                            onTestConnection = { draftKey -> viewModel.testConnection(provider.id, draftKey) },
                            onRemoveKey = { viewModel.removeApiKey(provider.id) },
                            onOpenAddModel = { providerToAddModel = provider },
                            onToggleModel = { modelId, isEnabled ->
                                viewModel.toggleModelEnabled(provider.id, modelId, isEnabled)
                            },
                            onSelectModel = { modelId ->
                                viewModel.selectModel(provider.id, modelId)
                            },
                            onRefreshModels = {
                                viewModel.refreshModels(provider.id)
                            }
                        )
                    }

                    // Test result banner
                    settingsState.testResultMessage?.let { message ->
                        val isSuccess = settingsState.lastTestSuccess == true
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSuccess) Color(0xFF1B5E20).copy(alpha = 0.2f) else Color(0xFFB71C1C).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSuccess) Color(0xFF4CAF50) else Color(0xFFEF5350)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFEF5350)
                                )
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // SEÇÃO 3: ROTEAMENTO MANUAL DE PROVEDOR
            item {
                SectionHeader(title = "ROTEAMENTO MANUAL (DIRETO)", icon = Icons.Default.Route)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Forçar Provedor Específico",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Permite fixar o processamento em um provedor ou deixar o Jarvis orquestrar automaticamente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val routingOptions = listOf(
                            null to "Automático (Orquestrador Jarvis Multiagente)",
                            "gemini" to "Google Gemini (Exclusivo)",
                            "openai" to "OpenAI (Exclusivo)",
                            "claude" to "Anthropic Claude (Exclusivo)",
                            "openrouter" to "OpenRouter (Exclusivo)"
                        )

                        routingOptions.forEach { (provId, label) ->
                            val isSelected = manualProviderOverride == provId
                            val isProvDisabled = provId != null && !enabledProviders.contains(provId)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.setManualProvider(provId) },
                                color = if (isSelected) ElectricOrange.copy(alpha = 0.15f) else Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 1.5.dp else 0.5.dp,
                                    if (isSelected) ElectricOrange else MaterialTheme.colorScheme.outlineVariant
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.setManualProvider(provId) },
                                        colors = RadioButtonDefaults.colors(selectedColor = ElectricOrange)
                                    )
                                    Column {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isProvDisabled) {
                                            Text(
                                                text = "Aviso: Provedor desativado nas opções acima",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFEF5350)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SEÇÃO 4: QUESTIONÁRIO DE MODELOS
            item {
                SectionHeader(title = "DISTRIBUIÇÃO INTELIGENTE DE MODELOS", icon = Icons.Default.Tune)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Questionário de Responsabilidades",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configure qual modelo atende cada tipo de necessidade (Conversas gerais, Código, Raciocínio, Imagens, Documentos) para que o Jarvis responda de forma fluida e automática.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { viewModel.setWizardOpen(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricOrange, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Abrir Questionário de Modelos", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // SEÇÃO 5: NÍVEL GLOBAL PADRÃO
            item {
                SectionHeader(title = "NÍVEL GLOBAL PADRÃO", icon = Icons.Default.Speed)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AILevel.values().forEach { level ->
                        val isSelected = aiLevel == level
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.updateAILevel(level) },
                            color = if (isSelected) ElectricOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) ElectricOrange else MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.updateAILevel(level) },
                                    colors = RadioButtonDefaults.colors(selectedColor = ElectricOrange)
                                )
                                Column {
                                    Text(
                                        text = level.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = when (level) {
                                            AILevel.LIGHT -> "Respostas rápidas e menor latência."
                                            AILevel.MEDIUM -> "Equilíbrio padrão do Jarvis para o dia a dia."
                                            AILevel.HIGH -> "Máximo poder computacional e raciocínio aprofundado."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SEÇÃO 6: DIAGNÓSTICO DO SISTEMA
            item {
                SectionHeader(title = "DIAGNÓSTICO E SEGURANÇA", icon = Icons.Default.Security)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Diagnóstico do Sistema Jarvis v${JarvisVersion.VERSION_NAME}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        DiagnosticRow(
                            label = "Keystore Criptográfico (AES-256)",
                            status = if (settingsState.keystoreHealthy) "Ativo & Saudável" else "Atenção",
                            isSuccess = settingsState.keystoreHealthy
                        )

                        DiagnosticRow(
                            label = "Provedores Habilitados",
                            status = enabledProviders.joinToString(", ") { it.uppercase() }.ifEmpty { "Nenhum" },
                            isSuccess = enabledProviders.isNotEmpty()
                        )

                        DiagnosticRow(
                            label = "Roteamento Manual",
                            status = manualProviderOverride?.uppercase() ?: "AUTOMÁTICO (MULTIAGENTE)",
                            isSuccess = true
                        )

                        DiagnosticRow(
                            label = "Backup de Aplicação",
                            status = "Desativado (Proteção Anti-Vazamento)",
                            isSuccess = true
                        )
                    }
                }
            }

            // SEÇÃO 7: APARÊNCIA
            item {
                SectionHeader(title = "APARÊNCIA", icon = Icons.Default.Palette)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Tema Escuro", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (isDarkTheme) "Preto (#000000) e Grafite (#171719)" else "Branco Gelo (#F2F2F2)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { viewModel.toggleTheme(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = ElectricOrange
                            )
                        )
                    }
                }
            }

            // SEÇÃO 8: SOBRE
            item {
                SectionHeader(title = "SOBRE O JARVIS", icon = Icons.Default.Info)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            JarvisMascot(size = 48.dp)
                            Column {
                                Text(text = "Jarvis Assistant", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Versão ${JarvisVersion.VERSION_NAME} (Build Code: ${JarvisVersion.VERSION_CODE})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ElectricOrange,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Nativo Kotlin • Jetpack Compose • Multiagente V${JarvisVersion.VERSION_NAME} • Room 2.6.1 • Keystore AES-256",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

        if (isVoiceDiagnosticsOpen) {
        VoiceDiagnosticsDialog(
            items = diagnosticsItems,
            onRunAll = { viewModel.runVoiceDiagnostics() },
            onDismiss = { viewModel.setVoiceDiagnosticsOpen(false) }
        )
    }

    if (settingsState.isWizardOpen) {
        ModelConfigWizardDialog(
            providers = settingsState.providers,
            currentAssignments = settingsState.taskAssignments,
            onSaveAssignments = { newAssignments ->
                viewModel.saveTaskAssignments(newAssignments)
                viewModel.setWizardOpen(false)
            },
            onDismiss = { viewModel.setWizardOpen(false) }
        )
    }

    providerToAddModel?.let { provider ->
        AddCustomModelDialog(
            provider = provider,
            onDismiss = { providerToAddModel = null },
            onAdd = { id, name, lvl, caps ->
                viewModel.addCustomModel(provider.id, id, name, lvl, caps)
                providerToAddModel = null
            }
        )
    }
}

@Composable
private fun DiagnosticRow(label: String, status: String, isSuccess: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFEF5350)
        )
    }
}

@Composable
private fun AgentPillRow(title: String, desc: String, badgeColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = badgeColor.copy(alpha = 0.2f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = desc,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = ElectricOrange
        )
    }
}

@Composable
private fun ProviderCard(
    provider: AIProviderConfig,
    onSaveKey: (String) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTestConnection: (String?) -> Unit,
    onRemoveKey: () -> Unit,
    onOpenAddModel: () -> Unit,
    onToggleModel: (String, Boolean) -> Unit,
    onSelectModel: (String) -> Unit,
    onRefreshModels: () -> Unit
) {
    var expandedModels by remember { mutableStateOf(false) }
    var draftKey by remember(provider.id) { mutableStateOf("") }
    var isDraftVisible by remember(provider.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (provider.isEnabled) ElectricOrange.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (provider.status) {
                                    ProviderStatus.CONEXAO_VALIDADA, ProviderStatus.CONNECTED -> Color(0xFF4CAF50)
                                    ProviderStatus.CREDENCIAL_SALVA, ProviderStatus.CREDENCIAL_RECUPERADA -> Color(0xFF29B6F6)
                                    ProviderStatus.TESTING -> Color(0xFFFFA000)
                                    ProviderStatus.SEM_CREDENCIAL, ProviderStatus.UNCONFIGURED -> Color.Gray
                                    ProviderStatus.FALHA_DESCRIPTOGRAFIA -> Color(0xFFD32F2F)
                                    else -> Color(0xFFEF5350)
                                }
                            )
                    )
                    Column {
                        Text(text = provider.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = provider.status.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (provider.status) {
                                ProviderStatus.CONEXAO_VALIDADA, ProviderStatus.CONNECTED -> Color(0xFF4CAF50)
                                ProviderStatus.CREDENCIAL_SALVA, ProviderStatus.CREDENCIAL_RECUPERADA -> Color(0xFF29B6F6)
                                ProviderStatus.TESTING -> Color(0xFFFFA000)
                                ProviderStatus.SEM_CREDENCIAL, ProviderStatus.UNCONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant
                                ProviderStatus.FALHA_DESCRIPTOGRAFIA -> Color(0xFFD32F2F)
                                else -> Color(0xFFEF5350)
                            }
                        )
                    }
                }
                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = ElectricOrange
                    )
                )
            }

            if (provider.isKeySaved) {
                Surface(
                    color = Color(0xFF1B5E20).copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Chave Protegida no Keystore: ${provider.maskedKey}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4CAF50)
                            )
                            provider.keyFingerprint?.let { fp ->
                                Text(
                                    text = "Fingerprint: $fp",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(onClick = onRemoveKey) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remover Chave", tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remover", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF5350))
                        }
                    }
                }
            }

            if (provider.isDecryptionFailed || !provider.lastError.isNullOrBlank()) {
                Surface(
                    color = Color(0xFFB71C1C).copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = provider.lastError ?: "Falha de descriptografia no Android Keystore. Reinsira sua chave abaixo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFEF5350),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            OutlinedTextField(
                value = draftKey,
                onValueChange = { draftKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (provider.isKeySaved) "Substituir Chave de API" else "Nova Chave de API") },
                placeholder = { Text("Cole sua chave para salvar com AES-256...") },
                visualTransformation = if (isDraftVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isDraftVisible = !isDraftVisible }) {
                        Icon(
                            if (isDraftVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Mostrar/Ocultar Digitação"
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val toSave = draftKey.trim()
                        if (toSave.isNotEmpty()) {
                            onSaveKey(toSave)
                            draftKey = ""
                            isDraftVisible = false
                        }
                    },
                    enabled = draftKey.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricOrange, contentColor = Color.Black)
                ) {
                    Text(text = "Salvar Chave", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val candidate = draftKey.trim().takeIf { it.isNotEmpty() }
                        onTestConnection(candidate)
                        if (candidate != null) {
                            draftKey = ""
                            isDraftVisible = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricOrange)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Testar Conexão", style = MaterialTheme.typography.labelMedium)
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Modelo Ativo: ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = provider.selectedModel.ifBlank { "Nenhum selecionado" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (provider.selectedModel.isNotBlank()) ElectricOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    TextButton(onClick = onRefreshModels) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp), tint = ElectricOrange)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Atualizar", style = MaterialTheme.typography.labelSmall, color = ElectricOrange)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { expandedModels = !expandedModels }) {
                    Text(
                        text = if (expandedModels) "Ocultar Modelos" else "Gerenciar Modelos (${provider.availableModels.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricOrange
                    )
                }
            }

            AnimatedVisibility(visible = expandedModels) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (provider.availableModels.isEmpty()) {
                        Text(
                            text = "Nenhum modelo carregado ainda. Salve uma chave válida e toque em 'Testar Conexão' para consultar os modelos reais da API.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    provider.availableModels.forEach { model ->
                        val isCurrentSelected = provider.selectedModel == model.id
                        val priceLabel = when {
                            model.isFree -> "GRATUITO"
                            model.promptPricePerMillion != null -> "PAGO"
                            else -> "PREÇO DESCONHECIDO"
                        }
                        val priceColor = when {
                            model.isFree -> Color(0xFF4CAF50)
                            model.promptPricePerMillion != null -> ElectricOrange
                            else -> Color(0xFFFFA000)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isCurrentSelected) ElectricOrange.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                )
                                .clickable { onSelectModel(model.id) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = isCurrentSelected,
                                    onClick = { onSelectModel(model.id) },
                                    colors = RadioButtonDefaults.colors(selectedColor = ElectricOrange)
                                )
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = model.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isCurrentSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isCurrentSelected) ElectricOrange else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isCurrentSelected) {
                                            Surface(
                                                color = ElectricOrange,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "ATIVO",
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Black,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${model.id} • $priceLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = priceColor
                                    )
                                    Text(
                                        text = model.capabilities.joinToString(", ") { it.displayName },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1
                                    )
                                }
                            }
                            Switch(
                                checked = model.isEnabled,
                                onCheckedChange = { isChecked -> onToggleModel(model.id, isChecked) },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }
                    TextButton(
                        onClick = onOpenAddModel,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Cadastrar Modelo Manualmente", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCustomModelDialog(
    provider: AIProviderConfig,
    onDismiss: () -> Unit,
    onAdd: (String, String, AILevel, Set<ModelCapability>) -> Unit
) {
    var modelId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(AILevel.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Adicionar Modelo em ${provider.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("ID do Modelo (ex: gemini-3.1-flash-lite)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Nome de Exibição") },
                    singleLine = true
                )
                Text(text = "Nível de Capacidade:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = level == AILevel.LIGHT,
                        onClick = { level = AILevel.LIGHT },
                        label = { Text("LIGHT") }
                    )
                    FilterChip(
                        selected = level == AILevel.MEDIUM,
                        onClick = { level = AILevel.MEDIUM },
                        label = { Text("MEDIUM") }
                    )
                    FilterChip(
                        selected = level == AILevel.HIGH,
                        onClick = { level = AILevel.HIGH },
                        label = { Text("HIGH") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (modelId.isNotBlank()) {
                        onAdd(modelId, displayName, level, setOf(ModelCapability.TEXT, ModelCapability.CODING))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricOrange, contentColor = Color.Black)
            ) {
                Text("Adicionar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
