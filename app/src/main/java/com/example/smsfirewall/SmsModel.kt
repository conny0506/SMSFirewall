package com.example.smsfirewall

data class SmsModel(
    val id: String,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val threadId: String,
    val unreadCount: Int = 0
)
