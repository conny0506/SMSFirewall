package com.example.smsfirewall

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trusted_numbers")
data class TrustedNumber(
    @PrimaryKey val phoneNumber: String
)