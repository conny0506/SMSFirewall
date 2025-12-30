package com.example.smsfirewall

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SpamMessageDao {
    @Query("SELECT * FROM spam_messages ORDER BY date DESC")
    suspend fun getAllSpam(): List<SpamMessage>

    @Insert
    suspend fun insert(spam: SpamMessage)

    @Delete
    suspend fun delete(spam: SpamMessage)
}