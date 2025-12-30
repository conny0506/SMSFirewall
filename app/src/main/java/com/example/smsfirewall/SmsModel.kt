package com.example.smsfirewall

data class SmsModel(
    val id: Long,
    val sender: String,
    val messageBody: String,
    val date: Long,
    val type: Int,
    var isSelected: Boolean = false // Varsayılan olarak false
)