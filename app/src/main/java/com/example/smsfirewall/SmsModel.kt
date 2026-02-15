package com.example.smsfirewall

data class SmsModel(
    val id: String,        // Mesajın benzersiz kimliği
    val address: String,   // Telefon numarası
    val body: String,      // Mesaj içeriği
    val date: Long,        // Tarih
    val type: Int,         // 1 = Gelen Mesaj, 2 = Giden Mesaj
    val threadId: String   // Konuşma ID'si
)