package com.jarvis.assistant.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ai.ProviderStatus
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.ui.components.JarvisMascot
import com.jarvis.assistant.ui.theme.ElectricOrange
import com.jarvis.assistant.ui.wizard.ModelConfigWizardDialog
import com.jarvis.assistant.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val selectedAILevel by viewModel.aiLevel.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()

    if (settingsState.isWizardOpen) {
        ModelConfigWizardDialog(
            onDismiss = { viewModel.setWizardOpen(false) },
            onSaveAssignments = { /* Salvo no state */ }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações", fontWeight = FontWeight.Bold) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // SEÇÃO: PROVEDORES DE IA (NOVO V1.1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PROVEDORES DE IA",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricOrange,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = { viewModel.setWizardOpen(true) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Questionário de Modelos", style = MaterialTheme.typography.labelSmall, color = ElectricOrange, fontWeight = FontWeight.Bold)
                }
            }

            settingsState.testResultMessage?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                settingsState.providers.forEach { provider ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = provider.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    when (provider.status) {
                                        ProviderStatus.CONNECTED -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                        ProviderStatus.ERROR -> Icon(Icons.Default.Error, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFFF44336), modifier = Modifier.size(16.dp))
                                        else -> {}
                                    }
                                }
                                Switch(
                                    checked = provider.isEnabled,
                                    onCheckedChange = { viewModel.toggleProviderEnabled(provider.id, it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = ElectricOrange)
                                )
                            }

                            // Chave de API protegida
                            var keyText by remember(provider.apiKey) { mutableStateOf(provider.apiKey) }
                            OutlinedTextField(
                                value = keyText,
                                onValueChange = {
                                    keyText = it
                                    viewModel.updateApiKey(provider.id, it)
                                },
                                label = { Text("Chave de API (Protegida no Android Keystore)") },
                                placeholder = { Text("Cole sua chave aqui...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            // Botão de Testar Conexão
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (provider.availableModels.isNotEmpty()) "${provider.availableModels.size} modelos disponíveis" else "Nenhum modelo verificado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = { viewModel.testConnection(provider.id) },
                                    enabled = keyText.isNotBlank() && !settingsState.isTestingConnection,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Testar Conexão", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }

            // SEÇÃO: APARÊNCIA
            Text(
                text = "APARÊNCIA",
                style = MaterialTheme.typography.labelSmall,
                color = ElectricOrange,
                fontWeight = FontWeight.Bold
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Tema Escuro", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (isDarkTheme) "Fundo preto e grafite ativado" else "Fundo branco e gelo ativado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { viewModel.toggleTheme(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = ElectricOrange)
                    )
                }
            }

            // SEÇÃO: NÍVEL DE CAPACIDADE DE IA
            Text(
                text = "NÍVEL DE CAPACIDADE DE IA",
                style = MaterialTheme.typography.labelSmall,
                color = ElectricOrange,
                fontWeight = FontWeight.Bold
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AILevel.entries.forEach { level ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.updateAILevel(level) }
                            .border(
                                width = if (level == selectedAILevel) 1.5.dp else 0.dp,
                                color = if (level == selectedAILevel) ElectricOrange else androidx.compose.ui.graphics.Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (level == selectedAILevel) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = level == selectedAILevel,
                                onClick = { viewModel.updateAILevel(level) },
                                colors = RadioButtonDefaults.colors(selectedColor = ElectricOrange)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = level.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(text = level.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // SEÇÃO: SOBRE O JARVIS
            Text(
                text = "SOBRE O JARVIS",
                style = MaterialTheme.typography.labelSmall,
                color = ElectricOrange,
                fontWeight = FontWeight.Bold
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    JarvisMascot(size = 50.dp)
                    Column {
                        Text(text = "Jarvis Assistant", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Versão 1.1.0 (Build 2) — Atualização V1.1", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "Multi-provedor IA • Keystore • Room • Speech", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
