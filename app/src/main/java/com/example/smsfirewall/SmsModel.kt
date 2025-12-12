package com.example.smsfirewall

// Bir SMS'in temel özelliklerini tutan basit bir kutu
data class SmsModel(
    val sender: String,      // Gönderen (Örn: +90555...)
    val messageBody: String, // Mesaj içeriği
    val date: Long           // Tarih (Sıralama yapmak istersek diye)
)