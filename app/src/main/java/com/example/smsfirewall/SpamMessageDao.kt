package com.example.smsfirewall

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpamMessageDao {
    @Query("SELECT * FROM spam_messages ORDER BY date DESC")
    suspend fun getAllSpam(): List<SpamMessage>

    @Query("SELECT COUNT(*) FROM spam_messages")
    suspend fun getSpamCount(): Int

    @Query("SELECT COUNT(*) FROM spam_messages")
    fun getSpamCountFlow(): Flow<Int>

    @Insert
    suspend fun insert(spam: SpamMessage)

    @Delete
    suspend fun delete(spam: SpamMessage)

    @Query("DELETE FROM spam_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM spam_messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
