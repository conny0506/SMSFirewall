package com.example.smsfirewall

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [BlockedWord::class, SpamMessage::class, TrustedNumber::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockedWordDao(): BlockedWordDao
    abstract fun spamMessageDao(): SpamMessageDao
    abstract fun trustedNumberDao(): TrustedNumberDao // YENİ

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
                    .fallbackToDestructiveMigration()
                    .addCallback(SmsDatabaseCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    // Callback sınıfı aynı kalabilir (blocked words ekleyen kısım)
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