package com.example.smsfirewall

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Version 2 yaptık ve SpamMessage sınıfını ekledik
@Database(entities = [BlockedWord::class, SpamMessage::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockedWordDao(): BlockedWordDao
    abstract fun spamMessageDao(): SpamMessageDao // YENİ

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_firewall_database"
                )
                    .fallbackToDestructiveMigration() // Veritabanı değişirse eskisini silip yenisini kurar
                    .addCallback(SmsDatabaseCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class SmsDatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                val dao = getDatabase(context).blockedWordDao()
                val defaultBlockList = listOf("bahis", "metin2", "bet", "casino", "bonus", "kazan")
                for (word in defaultBlockList) {
                    dao.insert(BlockedWord(word = word))
                }
            }
        }
    }
}