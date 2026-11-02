package com.jarvis.assistant.repositories

import com.jarvis.assistant.data.local.room.ConversationDao
import com.jarvis.assistant.data.local.room.ConversationEntity
import com.jarvis.assistant.data.local.room.MessageDao
import com.jarvis.assistant.data.local.room.MessageEntity
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
            entities.map { Message(it.id, it.conversationId, it.content, it.isFromUser, it.timestamp) }
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

    suspend fun saveUserMessage(conversationId: String, content: String): Message {
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            content = content,
            isFromUser = true,
            timestamp = System.currentTimeMillis()
        )
        messageDao.insertMessage(message)
        
        // Atualiza timestamp da conversa
        conversationDao.insertConversation(
            ConversationEntity(
                id = conversationId,
                title = content.take(30).trim() + if (content.length > 30) "..." else "",
                lastActivityTimestamp = System.currentTimeMillis()
            )
        )
        return Message(message.id, message.conversationId, message.content, message.isFromUser, message.timestamp)
    }
}
