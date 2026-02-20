package com.example.smsfirewall

import androidx.room.Entity
import androidx.room.PrimaryKey

// Bu sinif veritabaninda bir tabloya donusur
@Entity(tableName = "blocked_words_table")
data class BlockedWord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,       // Her kelimenin benzersiz bir numarasi olacak
    val word: String       // Yasaklanan kelime (Orn: "bahis")
)