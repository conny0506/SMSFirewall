package com.example.smsfirewall

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trash_messages")
data class TrashMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val date: Long,
    val type: Int // 1=Gelen, 2=Giden
)