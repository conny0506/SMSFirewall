package com.example.smsfirewall

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedWordDao {

    // Yeni bir yasaklı kelime ekle
    @Insert
    suspend fun insert(blockedWord: BlockedWord)

    // Bir kelimeyi sil
    @Delete
    suspend fun delete(blockedWord: BlockedWord)

    // Tüm yasaklı kelimeleri getir (Canlı veri olarak)
    // Flow: Liste değiştiği an uygulamaya haber veren modern bir yöntemdir.
    @Query("SELECT * FROM blocked_words_table ORDER BY id DESC")
    fun getAllWords(): Flow<List<BlockedWord>>

    // Kelime listesini normal liste olarak al (Receiver için lazım olacak)
    @Query("SELECT word FROM blocked_words_table")
    suspend fun getWordListRaw(): List<String>
}