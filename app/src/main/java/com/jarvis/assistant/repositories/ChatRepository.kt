package com.jarvis.assistant.repositories

import com.jarvis.assistant.data.local.room.ConversationDao
import com.jarvis.assistant.data.local.room.ConversationEntity
import com.jarvis.assistant.data.local.room.MessageDao
import com.jarvis.assistant.data.local.room.MessageEntity
import com.jarvis.assistant.domain.model.Attachment
import com.jarvis.assistant.domain.model.Conversation
import com.jarvis.assistant.domain.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    fun getAllConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations().map { entities ->
            entities.map { Conversation(it.id, it.title, it.lastActivityTimestamp) }
        }
    }

    fun getMessages(conversationId: String?): Flow<List<Message>> {
        if (conversationId.isNullOrBlank()) return flowOf(emptyList())
        return messageDao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { entity ->
                val attachment = if (entity.attachmentName != null && entity.attachmentMimeType != null) {
                    Attachment(
                        id = UUID.randomUUID().toString(),
                        name = entity.attachmentName,
                        mimeType = entity.attachmentMimeType,
                        sizeBytes = entity.attachmentSize ?: 0L,
                        uriString = entity.attachmentUri
                    )
                } else null

                Message(
                    id = entity.id,
                    conversationId = entity.conversationId,
                    content = entity.content,
                    isFromUser = entity.isFromUser,
                    timestamp = entity.timestamp,
                    attachment = attachment
                )
            }
        }
    }

    /**
     * Limpeza segura que remove apenas conversas residuais vazias (0 mensagens).
     */
    suspend fun cleanupEmptyConversations() {
        try {
            conversationDao.deleteEmptyConversations()
        } catch (_: Exception) {}
    }

    suspend fun deleteConversation(conversationId: String) {
        conversationDao.deleteConversationById(conversationId)
    }

    suspend fun pruneMessagesFromTimestamp(conversationId: String, timestamp: Long) {
        messageDao.deleteMessagesFromTimestamp(conversationId, timestamp)
    }

    /**
     * Criação tardia (Lazy Creation): A conversa só é persistida no Room DB quando a primeira mensagem é enviada!
     */
    suspend fun saveUserMessage(
        targetConversationId: String?,
        content: String,
        attachment: Attachment? = null
    ): Pair<String, Message> {
        val now = System.currentTimeMillis()
        val convId = if (targetConversationId.isNullOrBlank()) UUID.randomUUID().toString() else targetConversationId

        val existing = conversationDao.getConversationById(convId)
        val titleSnippet = content.take(32).trim() + if (content.length > 32) "..." else ""
        val finalTitle = if (titleSnippet.isNotBlank()) titleSnippet else "Nova Conversa"

        if (existing == null) {
            // Conversa criada no primeiro envio
            conversationDao.insertConversation(
                ConversationEntity(
                    id = convId,
                    title = finalTitle,
                    lastActivityTimestamp = now
                )
            )
        } else {
            val count = messageDao.getMessageCount(convId)
            val updatedTitle = if (count == 0 && (existing.title == "Nova Conversa" || existing.title.isBlank())) {
                finalTitle
            } else existing.title
            conversationDao.updateConversation(convId, updatedTitle, now)
        }

        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = convId,
            content = content,
            isFromUser = true,
            timestamp = now,
            attachmentName = attachment?.name,
            attachmentMimeType = attachment?.mimeType,
            attachmentSize = attachment?.sizeBytes,
            attachmentUri = attachment?.uriString
        )
        messageDao.insertMessage(message)

        val domainMsg = Message(
            id = message.id,
            conversationId = convId,
            content = message.content,
            isFromUser = true,
            timestamp = message.timestamp,
            attachment = attachment
        )

        return Pair(convId, domainMsg)
    }

    suspend fun saveAssistantMessage(conversationId: String, content: String): Message {
        val now = System.currentTimeMillis()
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            content = content,
            isFromUser = false,
            timestamp = now
        )
        messageDao.insertMessage(message)
        conversationDao.updateLastActivity(conversationId, now)
        return Message(message.id, message.conversationId, message.content, false, now)
    }
}
