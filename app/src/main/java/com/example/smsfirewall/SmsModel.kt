package com.example.smsfirewall

data class SmsModel(
    val id: Long,            // Silme işlemi için gerekli ID
    val sender: String,
    val messageBody: String,
    val date: Long,
    val type: Int            // 1: Gelen, 2: Giden
)