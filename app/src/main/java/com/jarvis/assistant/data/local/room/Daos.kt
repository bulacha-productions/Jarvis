package com.jarvis.assistant.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY lastActivityTimestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("UPDATE conversations SET title = :title, lastActivityTimestamp = :timestamp WHERE id = :id")
    suspend fun updateConversation(id: String, title: String, timestamp: Long)

    @Query("UPDATE conversations SET lastActivityTimestamp = :timestamp WHERE id = :id")
    suspend fun updateLastActivity(id: String, timestamp: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: String)

    /**
     * Limpeza segura: apaga estritamente conversas com ZERO mensagens registradas.
     * Preserva 100% das conversas que possuem mensagens.
     */
    @Query("DELETE FROM conversations WHERE (SELECT COUNT(*) FROM messages WHERE messages.conversationId = conversations.id) = 0")
    suspend fun deleteEmptyConversations()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    /**
     * Remove mensagens a partir de um timestamp específico para permitir reenvio de edição limpo.
     */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND timestamp >= :timestamp")
    suspend fun deleteMessagesFromTimestamp(conversationId: String, timestamp: Long)
}
