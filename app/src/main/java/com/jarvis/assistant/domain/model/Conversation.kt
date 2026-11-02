package com.jarvis.assistant.domain.model

data class Conversation(
    val id: String,
    val title: String,
    val lastActivityTimestamp: Long
)

data class Message(
    val id: String,
    val conversationId: String,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long
)
