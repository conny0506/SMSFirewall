package com.example.smsfirewall

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trash_messages")
data class TrashMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalSmsId: String,
    val sender: String,
    val body: String,
    val date: Long,
    val type: Int,
    val threadId: String,
    val deletedAt: Long
)
