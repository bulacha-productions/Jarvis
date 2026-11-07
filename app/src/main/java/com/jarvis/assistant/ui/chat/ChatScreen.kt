package com.jarvis.assistant.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jarvis.assistant.JarvisVersion
import com.jarvis.assistant.audio.VoiceRecognitionManager
import com.jarvis.assistant.audio.VoiceState
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.Attachment
import com.jarvis.assistant.domain.model.Message
import com.jarvis.assistant.ui.components.JarvisMascot
import com.jarvis.assistant.ui.theme.ElectricOrange
import com.jarvis.assistant.viewmodels.ChatViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    conversationId: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(conversationId) {
        viewModel.loadConversation(conversationId)
    }

    LaunchedEffect(uiState.messages.size, uiState.isSending) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Voice Recognition Manager
    val voiceManager = remember { VoiceRecognitionManager(context) }
    val voiceState by voiceManager.voiceState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.stopAndDestroy()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceManager.startListening(
                onPartial = { partial ->
                    if (partial.isNotBlank()) viewModel.onInputTextChanged(partial)
                },
                onResult = { text ->
                    if (text.isNotBlank()) {
                        viewModel.onInputTextChanged(text)
                        viewModel.sendMessage()
                    }
                    viewModel.setRecordingVoice(false)
                },
                onError = { err ->
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    viewModel.setRecordingVoice(false)
                }
            )
            viewModel.setRecordingVoice(true)
        } else {
            Toast.makeText(context, "Permissão de microfone necessária para falar com o Jarvis.", Toast.LENGTH_SHORT).show()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val contentResolver = context.contentResolver
            val type = contentResolver.getType(it) ?: "image/jpeg"
            val attachment = Attachment(
                id = UUID.randomUUID().toString(),
                name = "foto_${System.currentTimeMillis()}.jpg",
                mimeType = type,
                sizeBytes = 1024L,
                uriString = it.toString()
            )
            viewModel.setAttachedFile(attachment)
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val contentResolver = context.contentResolver
            val type = contentResolver.getType(it) ?: "application/pdf"
            val attachment = Attachment(
                id = UUID.randomUUID().toString(),
                name = "documento_${System.currentTimeMillis()}",
                mimeType = type,
                sizeBytes = 2048L,
                uriString = it.toString()
            )
            viewModel.setAttachedFile(attachment)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        JarvisMascot(isThinking = uiState.isSending, size = 36.dp)
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Jarvis",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "v${JarvisVersion.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricOrange,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = if (uiState.isSending) "Coordenando tarefas..." else "Smart Router Ativo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Botão Parar Leitura TTS se ativo
                    if (uiState.isSpeakingTTS) {
                        IconButton(onClick = { viewModel.stopSpeaking() }) {
                            Icon(
                                Icons.Default.VolumeOff,
                                contentDescription = "Parar Leitura",
                                tint = ElectricOrange
                            )
                        }
                    }

                    // Botão Nova Conversa (Criação tardia / Lazy)
                    IconButton(onClick = { viewModel.startNewChat() }) {
                        Icon(
                            Icons.Default.AddComment,
                            contentDescription = "Nova Conversa",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "Histórico de Conversas",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Configurações",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Lista de Mensagens do Chat
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (uiState.messages.isEmpty() && !uiState.isSending) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        JarvisMascot(isThinking = false, size = 72.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Jarvis Neural Core v${JarvisVersion.VERSION_NAME}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ElectricOrange
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Smart Router com roteamento dinâmico, colaboração multiagente, proteção financeira e renderização Markdown nativa.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                isSpeaking = uiState.isSpeakingTTS,
                                onEdit = { viewModel.editUserMessage(message) },
                                onSpeak = { viewModel.speakMessage(message.content) },
                                onStopSpeak = { viewModel.stopSpeaking() }
                            )
                        }

                        if (uiState.isSending) {
                            item {
                                ThinkingBubble(onCancel = { viewModel.cancelActiveRequest() })
                            }
                        }
                    }
                }
            }

            // Barra Inferior de Entrada e Comandos
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Indicador de mensagem em edição
                    if (uiState.editingMessageTimestamp != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "✏️ Editando mensagem anterior...",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricOrange,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Cancelar",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable {
                                    viewModel.onInputTextChanged("")
                                }
                            )
                        }
                    }

                    // Anexo em preparação
                    uiState.attachedFile?.let { att ->
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(16.dp))
                            Text(text = att.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remover",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.removeAttachedFile() }
                            )
                        }
                    }

                    // Menu Mais (+)
                    AnimatedVisibility(visible = uiState.isPlusMenuOpen) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                MenuPlusItem(icon = Icons.Default.CameraAlt, label = "Foto") {
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                                MenuPlusItem(icon = Icons.Default.AttachFile, label = "Documento") {
                                    docPickerLauncher.launch("application/pdf")
                                }
                                MenuPlusItem(icon = Icons.Default.Speed, label = "Capacidade") {
                                    viewModel.toggleCapacitySubmenu(!uiState.isCapacitySubmenuOpen)
                                }
                            }
                        }
                    }

                    // Submenu de Capacidade
                    AnimatedVisibility(visible = uiState.isCapacitySubmenuOpen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.perMessageCapacity == AILevel.LIGHT,
                                onClick = { viewModel.setPerMessageCapacity(AILevel.LIGHT) },
                                label = { Text("⚡ Light") }
                            )
                            FilterChip(
                                selected = uiState.perMessageCapacity == AILevel.MEDIUM,
                                onClick = { viewModel.setPerMessageCapacity(AILevel.MEDIUM) },
                                label = { Text("⚖️ Medium") }
                            )
                            FilterChip(
                                selected = uiState.perMessageCapacity == AILevel.HIGH,
                                onClick = { viewModel.setPerMessageCapacity(AILevel.HIGH) },
                                label = { Text("🧠 High") }
                            )
                        }
                    }

                    // Campo de Texto e Botões de Ação
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.togglePlusMenu(!uiState.isPlusMenuOpen) },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Menu de Ações", tint = ElectricOrange)
                        }

                        IconButton(
                            onClick = {
                                if (uiState.isRecordingVoice) {
                                    voiceManager.cancel()
                                    viewModel.setRecordingVoice(false)
                                } else {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        voiceManager.startListening(
                                            onPartial = { partial ->
                                                if (partial.isNotBlank()) viewModel.onInputTextChanged(partial)
                                            },
                                            onResult = { text ->
                                                if (text.isNotBlank()) {
                                                    viewModel.onInputTextChanged(text)
                                                    viewModel.sendMessage()
                                                }
                                                viewModel.setRecordingVoice(false)
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                                viewModel.setRecordingVoice(false)
                                            }
                                        )
                                        viewModel.setRecordingVoice(true)
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isRecordingVoice) Color.Red.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                if (uiState.isRecordingVoice) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Falar por Voz",
                                tint = if (uiState.isRecordingVoice) Color.Red else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        TextField(
                            value = uiState.inputText,
                            onValueChange = { viewModel.onInputTextChanged(it) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(max = 120.dp),
                            placeholder = { Text("Pergunte algo ao Jarvis...") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(20.dp),
                            maxLines = 4
                        )

                        IconButton(
                            onClick = { viewModel.sendMessage() },
                            enabled = (uiState.inputText.isNotBlank() || uiState.attachedFile != null) && !uiState.isSending,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if ((uiState.inputText.isNotBlank() || uiState.attachedFile != null) && !uiState.isSending) ElectricOrange
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar",
                                tint = if ((uiState.inputText.isNotBlank() || uiState.attachedFile != null) && !uiState.isSending) Color.Black
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuPlusItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(ElectricOrange.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = ElectricOrange, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isSpeaking: Boolean = false,
    onEdit: () -> Unit = {},
    onSpeak: () -> Unit = {},
    onStopSpeak: () -> Unit = {}
) {
    val isUser = message.isFromUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormatter.format(Date(message.timestamp)) }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // Badges multiagente em respostas do Jarvis
        val agentBadges = remember(message.content) {
            if (!isUser) AgentBadgeHelper.extractBadges(message.content) else emptyList()
        }
        if (agentBadges.isNotEmpty()) {
            AgentBadgesRow(badges = agentBadges)
        }

        // Anexo dentro da bolha
        message.attachment?.let { att ->
            Surface(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .widthIn(max = 300.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (att.mimeType.startsWith("image/")) Icons.Default.Image else Icons.Default.Description,
                        contentDescription = null,
                        tint = ElectricOrange
                    )
                    Text(
                        text = att.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }

        // Bolha de Texto com SelectionContainer para seleção e cópia de qualquer trecho
        SelectionContainer {
            Box(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isUser) ElectricOrange else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        width = if (isUser) 0.dp else 1.dp,
                        color = if (isUser) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                } else {
                    MarkdownRenderer(
                        content = message.content,
                        textColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Barra de Ações da Mensagem: Hora, Copiar, Editar (User) e Voz (Assistant)
        Row(
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Copiar mensagem inteira
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copiar Mensagem",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(13.dp)
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Jarvis", message.content))
                        Toast.makeText(context, "Mensagem copiada!", Toast.LENGTH_SHORT).show()
                    }
            )

            // Compartilhar
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Compartilhar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(13.dp)
                    .clickable {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, message.content)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Compartilhar mensagem")
                        context.startActivity(shareIntent)
                    }
            )

            // Ação de Edição exclusiva para mensagens do usuário
            if (isUser) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Editar Mensagem",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(13.dp)
                        .clickable { onEdit() }
                )
            } else {
                // Ação de Leitura por Voz (TTS) para respostas do Jarvis
                Icon(
                    imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (isSpeaking) "Parar Leitura" else "Ouvir Resposta",
                    tint = if (isSpeaking) ElectricOrange else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(13.dp)
                        .clickable {
                            if (isSpeaking) onStopSpeak() else onSpeak()
                        }
                )
            }
        }
    }
}

@Composable
fun ThinkingBubble(onCancel: () -> Unit) {
    Surface(
        modifier = Modifier.widthIn(max = 280.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = ElectricOrange,
                strokeWidth = 2.dp
            )
            Text(
                text = "Jarvis processando...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onCancel) {
                Text(text = "Cancelar", style = MaterialTheme.typography.labelSmall, color = Color.Red)
            }
        }
    }
}
