package com.example.smsfirewall

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [BlockedWord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockedWordDao(): BlockedWordDao

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
                    // Callback'i buraya ekliyoruz
                    .addCallback(SmsDatabaseCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    // Veritabanı İLK KEZ oluşturulduğunda çalışacak sınıf
    private class SmsDatabaseCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)

            // Arka planda varsayılan kelimeleri ekle
            CoroutineScope(Dispatchers.IO).launch {
                val dao = getDatabase(context).blockedWordDao()

                // İSTEDİĞİN HAZIR LİSTE BURADA:
                val defaultBlockList = listOf(
                    "bahis",
                    "metin2",
                    "metin 2",
                    "b4his",
                    "bet",
                    "b3t",
                    "metin iki",
                    "casino",
                    "bonus",
                    "freespin",
                    "kazan",
                    "slot"
                )

                for (word in defaultBlockList) {
                    dao.insert(BlockedWord(word = word))
                }
            }
        }
    }
}