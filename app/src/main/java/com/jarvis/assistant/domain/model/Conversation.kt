package com.jarvis.assistant.domain.model

data class Attachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val uriString: String? = null
)

data class Message(
    val id: String,
    val conversationId: String,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long,
    val attachment: Attachment? = null
)

data class Conversation(
    val id: String,
    val title: String,
    val lastActivityTimestamp: Long
)
