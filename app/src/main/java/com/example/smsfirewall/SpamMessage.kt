package com.example.smsfirewall

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spam_messages")
data class SpamMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String,
    val body: String,
    val date: Long
)