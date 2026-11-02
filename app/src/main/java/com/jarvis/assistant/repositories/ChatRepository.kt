package com.jarvis.assistant.repositories

import com.jarvis.assistant.data.local.room.ConversationDao
import com.jarvis.assistant.data.local.room.ConversationEntity
import com.jarvis.assistant.data.local.room.MessageDao
import com.jarvis.assistant.data.local.room.MessageEntity
import com.jarvis.assistant.domain.model.Attachment
import com.jarvis.assistant.domain.model.Conversation
import com.jarvis.assistant.domain.model.Message
import kotlinx.coroutines.flow.Flow
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

    fun getMessages(conversationId: String): Flow<List<Message>> {
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

    suspend fun createNewConversation(title: String = "Nova Conversa"): String {
        val newId = UUID.randomUUID().toString()
        val conversation = ConversationEntity(
            id = newId,
            title = title,
            lastActivityTimestamp = System.currentTimeMillis()
        )
        conversationDao.insertConversation(conversation)
        return newId
    }

    suspend fun deleteConversation(conversationId: String) {
        conversationDao.deleteConversationById(conversationId)
    }

    suspend fun saveUserMessage(
        conversationId: String,
        content: String,
        attachment: Attachment? = null
    ): Message {
        val now = System.currentTimeMillis()
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            content = content,
            isFromUser = true,
            timestamp = now,
            attachmentName = attachment?.name,
            attachmentMimeType = attachment?.mimeType,
            attachmentSize = attachment?.sizeBytes,
            attachmentUri = attachment?.uriString
        )

        // Ensure parent conversation exists first
        val existing = conversationDao.getConversationById(conversationId)
        if (existing == null) {
            val titleSnippet = content.take(30).trim() + if (content.length > 30) "..." else ""
            conversationDao.insertConversation(
                ConversationEntity(
                    id = conversationId,
                    title = if (titleSnippet.isNotBlank()) titleSnippet else "Nova Conversa",
                    lastActivityTimestamp = now
                )
            )
        } else {
            // In-place update to prevent SQLite ON DELETE CASCADE!
            val count = messageDao.getMessageCount(conversationId)
            val updatedTitle = if (count == 0 && (existing.title == "Nova Conversa" || existing.title.isBlank())) {
                content.take(30).trim() + if (content.length > 30) "..." else ""
            } else existing.title

            conversationDao.updateConversation(conversationId, updatedTitle, now)
        }

        messageDao.insertMessage(message)

        return Message(
            id = message.id,
            conversationId = message.conversationId,
            content = message.content,
            isFromUser = message.isFromUser,
            timestamp = message.timestamp,
            attachment = attachment
        )
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
