package com.example.smsfirewall

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrustedNumberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trustedNumber: TrustedNumber)

    @Query("SELECT EXISTS(SELECT 1 FROM trusted_numbers WHERE phoneNumber = :number)")
    suspend fun isTrusted(number: String): Boolean
}