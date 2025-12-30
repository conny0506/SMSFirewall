package com.example.smsfirewall

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TrashMessageDao {
    @Insert
    suspend fun insertAll(messages: List<TrashMessage>)

    @Query("SELECT * FROM trash_messages ORDER BY date DESC")
    suspend fun getAllTrash(): List<TrashMessage>

    @Delete
    suspend fun delete(message: TrashMessage)

    @Query("DELETE FROM trash_messages WHERE sender = :sender")
    suspend fun deleteBySender(sender: String)

    // YENİ EKLENDİ: Tabloyu tamamen temizler
    @Query("DELETE FROM trash_messages")
    suspend fun deleteAll()
}