package com.jarvis.assistant.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.core.content.ContextCompat
import com.jarvis.assistant.audio.VoiceRecognitionManager
import com.jarvis.assistant.audio.VoiceState
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.Attachment
import com.jarvis.assistant.domain.model.Message
import com.jarvis.assistant.ui.components.JarvisMascot
import com.jarvis.assistant.JarvisVersion
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
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Voice Manager instance
    val voiceManager = remember { VoiceRecognitionManager(context) }
    val voiceState by voiceManager.voiceState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.stopAndDestroy()
        }
    }

    // Audio Permission Launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceManager.startListening(
                onPartial = { partial ->
                    viewModel.onInputTextChanged(partial)
                },
                onResult = { text ->
                    val current = uiState.inputText
                    viewModel.onInputTextChanged(if (current.isBlank()) text else "$current $text")
                    viewModel.setRecordingVoice(false)
                },
                onError = {
                    viewModel.setRecordingVoice(false)
                }
            )
            viewModel.setRecordingVoice(true)
        } else {
            viewModel.setRecordingVoice(false)
        }
    }

    // 1. Camera Capture Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Camera permission granted
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            val file = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                it.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            viewModel.setAttachedFile(
                Attachment(
                    id = UUID.randomUUID().toString(),
                    name = "Foto_${System.currentTimeMillis()}.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = file.length(),
                    uriString = Uri.fromFile(file).toString()
                )
            )
        }
    }

    // 2. Photos Picker Launcher (Images only)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setAttachedFile(
                Attachment(
                    id = UUID.randomUUID().toString(),
                    name = it.lastPathSegment ?: "imagem.jpg",
                    mimeType = context.contentResolver.getType(it) ?: "image/jpeg",
                    sizeBytes = 2048L,
                    uriString = it.toString()
                )
            )
        }
    }

    // 3. Document/File Picker Launcher (PDF, TXT, DOCX)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val mime = context.contentResolver.getType(it) ?: "application/octet-stream"
            viewModel.setAttachedFile(
                Attachment(
                    id = UUID.randomUUID().toString(),
                    name = it.lastPathSegment ?: "documento",
                    mimeType = mime,
                    sizeBytes = 4096L,
                    uriString = it.toString()
                )
            )
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        JarvisMascot(size = 32.dp)
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "JARVIS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(ElectricOrange)
                                )
                                Text(
                                    text = uiState.activeProviderName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.startNewChat() }) {
                        Icon(Icons.Default.Add, contentDescription = "Nova Conversa", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "Histórico", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = MaterialTheme.colorScheme.onSurface)
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
            // Main Feed Area
            if (uiState.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        JarvisMascot(size = 120.dp)
                        Text(
                            text = "Como posso ajudar você hoje?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Toque no botão + para Câmera, Fotos, Arquivos ou selecione a Capacidade.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { msg ->
                        MessageBubble(message = msg)
                    }

                    if (uiState.isSending) {
                        item {
                            ThinkingBubble(onCancel = { viewModel.cancelActiveRequest() })
                        }
                    }
                }
            }

            // Voice Recording Bar
            AnimatedVisibility(visible = uiState.isRecordingVoice) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ElectricOrange.copy(alpha = 0.15f))
                        .border(1.dp, ElectricOrange.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                        Text(
                            text = "Gravando áudio... Fale agora",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ElectricOrange
                        )
                    }

                    TextButton(onClick = {
                        voiceManager.cancel()
                        viewModel.setRecordingVoice(false)
                    }) {
                        Text(text = "Cancelar", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Per-Message Capacity Chip (Section 6)
            AnimatedVisibility(visible = uiState.perMessageCapacity != null) {
                uiState.perMessageCapacity?.let { capLevel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Capacidade nesta mensagem: ${capLevel.name}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ElectricOrange
                            )
                        }

                        IconButton(
                            onClick = { viewModel.setPerMessageCapacity(null) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remover override", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Attachment Preview Chip (Section 5.4)
            AnimatedVisibility(visible = uiState.attachedFile != null) {
                uiState.attachedFile?.let { attachment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (attachment.mimeType.startsWith("image/")) Icons.Default.Image else Icons.Default.Description,
                                contentDescription = null,
                                tint = ElectricOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = attachment.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = { viewModel.removeAttachedFile() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remover anexo", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // New Menu + Popup / Bottom Drawer (Section 5)
            AnimatedVisibility(visible = uiState.isPlusMenuOpen) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Adicionar à Mensagem",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            // 1. Câmera
                            MenuPlusItem(
                                icon = Icons.Default.CameraAlt,
                                label = "Câmera",
                                onClick = {
                                    viewModel.togglePlusMenu(false)
                                    val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                    if (hasCam) {
                                        takePictureLauncher.launch(null)
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            )

                            // 2. Fotos (Only images!)
                            MenuPlusItem(
                                icon = Icons.Default.Image,
                                label = "Fotos",
                                onClick = {
                                    viewModel.togglePlusMenu(false)
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )

                            // 3. Arquivos
                            MenuPlusItem(
                                icon = Icons.Default.Folder,
                                label = "Arquivos",
                                onClick = {
                                    viewModel.togglePlusMenu(false)
                                    filePickerLauncher.launch("*/*")
                                }
                            )

                            // 4. Capacidade
                            MenuPlusItem(
                                icon = Icons.Default.Bolt,
                                label = "Capacidade",
                                onClick = {
                                    viewModel.toggleCapacitySubmenu(!uiState.isCapacitySubmenuOpen)
                                }
                            )
                        }

                        // Submenu de Capacidade (Section 6)
                        AnimatedVisibility(visible = uiState.isCapacitySubmenuOpen) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = uiState.perMessageCapacity == AILevel.LIGHT,
                                    onClick = { viewModel.setPerMessageCapacity(AILevel.LIGHT) },
                                    label = { Text("LIGHT") }
                                )
                                FilterChip(
                                    selected = uiState.perMessageCapacity == AILevel.MEDIUM,
                                    onClick = { viewModel.setPerMessageCapacity(AILevel.MEDIUM) },
                                    label = { Text("MEDIUM") }
                                )
                                FilterChip(
                                    selected = uiState.perMessageCapacity == AILevel.HIGH,
                                    onClick = { viewModel.setPerMessageCapacity(AILevel.HIGH) },
                                    label = { Text("HIGH") }
                                )
                            }
                        }
                    }
                }
            }

            // Input bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botão de Adição (+)
                    IconButton(
                        onClick = { viewModel.togglePlusMenu(!uiState.isPlusMenuOpen) },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            if (uiState.isPlusMenuOpen) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "Menu de Anexos e Capacidade",
                            tint = ElectricOrange
                        )
                    }

                    // Botão de Áudio (Microfone Repetível)
                    IconButton(
                        onClick = {
                            if (uiState.isRecordingVoice) {
                                voiceManager.cancel()
                                viewModel.setRecordingVoice(false)
                            } else {
                                val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (hasAudio) {
                                    voiceManager.startListening(
                                        onPartial = { partial -> viewModel.onInputTextChanged(partial) },
                                        onResult = { text ->
                                            val current = uiState.inputText
                                            viewModel.onInputTextChanged(if (current.isBlank()) text else "$current $text")
                                            viewModel.setRecordingVoice(false)
                                        },
                                        onError = { viewModel.setRecordingVoice(false) }
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

                    // Text Input
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

                    // Botão Enviar
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
fun MessageBubble(message: Message) {
    val isUser = message.isFromUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormatter.format(Date(message.timestamp)) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // Agent Badges (V2.0.0 Multiagente)
        val agentBadges = remember(message.content) {
            if (!isUser) AgentBadgeHelper.extractBadges(message.content) else emptyList()
        }
        if (agentBadges.isNotEmpty()) {
            AgentBadgesRow(badges = agentBadges)
        }

        // Attachment inside bubble
        message.attachment?.let { att ->
            Surface(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .widthIn(max = 280.dp),
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

        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
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
                    color = if (isUser) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) Color.Black else MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = formattedTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
        )
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
