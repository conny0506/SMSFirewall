package com.example.smsfirewall

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TrashMessageDao {
    @Query("SELECT * FROM trash_messages ORDER BY deletedAt DESC")
    suspend fun getAllTrash(): List<TrashMessage>

    @Insert
    suspend fun insert(item: TrashMessage)

    @Insert
    suspend fun insertAll(items: List<TrashMessage>)

    @Delete
    suspend fun delete(item: TrashMessage)

    @Query("DELETE FROM trash_messages WHERE id = :id")
    suspend fun deleteById(id: Long)
}
