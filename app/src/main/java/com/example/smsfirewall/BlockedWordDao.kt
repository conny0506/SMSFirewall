package com.example.smsfirewall

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedWordDao {

    // Yeni bir yasakli kelime ekle
    @Insert
    suspend fun insert(blockedWord: BlockedWord)

    // Bir kelimeyi sil
    @Delete
    suspend fun delete(blockedWord: BlockedWord)

    // Tum yasakli kelimeleri getir (Canli veri olarak)
    // Flow: Liste degistigi an uygulamaya haber veren modern bir yontemdir.
    @Query("SELECT * FROM blocked_words_table ORDER BY id DESC")
    fun getAllWords(): Flow<List<BlockedWord>>

    // Kelime listesini normal liste olarak al (Receiver icin lazim olacak)
    @Query("SELECT word FROM blocked_words_table")
    suspend fun getWordListRaw(): List<String>
}