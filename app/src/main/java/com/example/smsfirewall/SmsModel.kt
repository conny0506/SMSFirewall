package com.example.smsfirewall

// type: Int parametresini eklemeyi unutma!
data class SmsModel(
    val sender: String,
    val messageBody: String,
    val date: Long,
    val type: Int // <-- BU SATIR EKSİK OLABİLİR
)