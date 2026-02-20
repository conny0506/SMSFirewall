package com.example.smsfirewall

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BlockedWord::class, SpamMessage::class, TrustedNumber::class, TrashMessage::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockedWordDao(): BlockedWordDao
    abstract fun spamMessageDao(): SpamMessageDao
    abstract fun trustedNumberDao(): TrustedNumberDao
    abstract fun trashMessageDao(): TrashMessageDao

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
                    .addMigrations(MIGRATION_1_4, MIGRATION_2_4, MIGRATION_3_4)
                    .addCallback(SmsDatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private fun createTablesIfMissing(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS blocked_words_table (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word TEXT NOT NULL
                    )
                """.trimIndent()
            )
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS spam_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sender TEXT NOT NULL,
                        body TEXT NOT NULL,
                        date INTEGER NOT NULL
                    )
                """.trimIndent()
            )
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS trusted_numbers (
                        phoneNumber TEXT NOT NULL PRIMARY KEY
                    )
                """.trimIndent()
            )
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS trash_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        originalSmsId TEXT NOT NULL,
                        sender TEXT NOT NULL,
                        body TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        type INTEGER NOT NULL,
                        threadId TEXT NOT NULL,
                        deletedAt INTEGER NOT NULL
                    )
                """.trimIndent()
            )
        }

        private val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createTablesIfMissing(db)
            }
        }

        private val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createTablesIfMissing(db)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createTablesIfMissing(db)
            }
        }
    }

    private class SmsDatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            val defaultBlockList = listOf("bahis", "metin2", "bet", "casino", "bonus", "kazan")
            db.beginTransaction()
            try {
                for (word in defaultBlockList) {
                    db.execSQL(
                        "INSERT INTO blocked_words_table (word) VALUES (?)",
                        arrayOf(word)
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

}
