package com.jarvis.assistant.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ai.AIProviderConfig
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.AIModelInfo
import com.jarvis.assistant.domain.model.ModelCapability
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
    val aiLevel by viewModel.aiLevel.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()

    var showKeyForProviderId by remember { mutableStateOf<String?>(null) }
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
                                text = "v1.1.1",
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
            // SEÇÃO 1: PROVEDORES DE IA (Item 2)
            item {
                SectionHeader(title = "PROVEDORES DE IA & APIS", icon = Icons.Default.Key)
            }

            item {
                Text(
                    text = "As credenciais são protegidas com criptografia de hardware via Android Keystore (AES256).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    settingsState.providers.forEach { provider ->
                        ProviderCard(
                            provider = provider,
                            isKeyVisible = showKeyForProviderId == provider.id,
                            onToggleKeyVisibility = {
                                showKeyForProviderId = if (showKeyForProviderId == provider.id) null else provider.id
                            },
                            onKeyChanged = { newKey -> viewModel.updateApiKey(provider.id, newKey) },
                            onToggleEnabled = { isEnabled -> viewModel.toggleProviderEnabled(provider.id, isEnabled) },
                            onTestConnection = { viewModel.testConnection(provider.id) },
                            onRemoveKey = { viewModel.removeApiKey(provider.id) },
                            onOpenAddModel = { providerToAddModel = provider },
                            onToggleModel = { modelId, isEnabled ->
                                viewModel.toggleModelEnabled(provider.id, modelId, isEnabled)
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

            // SEÇÃO 2: QUESTIONÁRIO DE MODELOS (Item 3)
            item {
                SectionHeader(title = "DISTRIBUIÇÃO INTELIGENTE DE MODELOS", icon = Icons.Default.Psychology)
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

            // SEÇÃO 3: NÍVEL PADRÃO DE INTELIGÊNCIA
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

            // SEÇÃO 4: APARÊNCIA
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

            // SEÇÃO 5: SOBRE
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
                                    text = "Versão 1.1.1 (Build Code: 3)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ElectricOrange,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Nativo Kotlin • Jetpack Compose • Room 2.6.1",
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

    // Modal de Questionário Completo (Item 3)
    if (settingsState.isWizardOpen) {
        ModelConfigWizardDialog(
            providers = settingsState.providers,
            currentAssignments = settingsState.taskAssignments,
            onSaveAssignments = { newAssignments ->
                viewModel.saveTaskAssignments(newAssignments)
            },
            onDismiss = { viewModel.setWizardOpen(false) }
        )
    }

    // Dialog para adicionar modelo manual (Item 2)
    providerToAddModel?.let { p ->
        AddCustomModelDialog(
            provider = p,
            onDismiss = { providerToAddModel = null },
            onAdd = { modelId, name, level, capabilities ->
                viewModel.addCustomModel(p.id, modelId, name, level, capabilities)
                providerToAddModel = null
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = ElectricOrange
        )
    }
}

@Composable
private fun ProviderCard(
    provider: AIProviderConfig,
    isKeyVisible: Boolean,
    onToggleKeyVisibility: () -> Unit,
    onKeyChanged: (String) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onRemoveKey: () -> Unit,
    onOpenAddModel: () -> Unit,
    onToggleModel: (String, Boolean) -> Unit
) {
    var expandedModels by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (provider.apiKey.isNotBlank()) ElectricOrange.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header: Name & Enable Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ElectricOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = provider.displayName.first().toString(),
                            fontWeight = FontWeight.Bold,
                            color = ElectricOrange
                        )
                    }
                    Column {
                        Text(text = provider.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (provider.apiKey.isNotBlank()) "Credencial salva no Keystore" else "Sem credencial",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (provider.apiKey.isNotBlank()) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
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

            // API Key field with mask toggle
            OutlinedTextField(
                value = provider.apiKey,
                onValueChange = onKeyChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Chave de API") },
                placeholder = { Text("Insira sua chave...") },
                visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = onToggleKeyVisibility) {
                            Icon(
                                if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Mostrar/Ocultar"
                            )
                        }
                        if (provider.apiKey.isNotBlank()) {
                            IconButton(onClick = onRemoveKey) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Remover Chave", tint = Color.Red)
                            }
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Actions: Testar Conexão + Ver Modelos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onTestConnection,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricOrange)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Testar Conexão", style = MaterialTheme.typography.labelMedium)
                }

                TextButton(onClick = { expandedModels = !expandedModels }) {
                    Text(
                        text = if (expandedModels) "Ocultar Modelos" else "Gerenciar Modelos (${provider.availableModels.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricOrange
                    )
                }
            }

            // Models list & manual addition (Item 2)
            AnimatedVisibility(visible = expandedModels) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    provider.availableModels.forEach { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = model.displayName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = model.capabilities.joinToString(", ") { it.displayName },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
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

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.padding((1f - scale) * 4.dp)
)

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
                    label = { Text("ID do Modelo (ex: gpt-4.5-preview)") },
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
