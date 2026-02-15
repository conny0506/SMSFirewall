package com.example.smsfirewall

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "ACTION_TRUST_NUMBER") {
            val sender = intent.getStringExtra("sender") ?: return
            val body = intent.getStringExtra("body") ?: ""
            val notificationId = intent.getIntExtra("notificationId", 0)

            val db = AppDatabase.getDatabase(context)

            CoroutineScope(Dispatchers.IO).launch {
                // 1. Numarayı Güvenli Listeye Ekle
                db.trustedNumberDao().insert(TrustedNumber(sender))

                // 2. Mesajı Inbox'a (Gelen Kutusuna) Taşı
                saveToInbox(context, sender, body)

                // 3. Spam Kutusundan Sil (Opsiyonel, temizlik için)
                // (Burada veritabanından son eklenen spamı bulup silebiliriz ama
                // şimdilik Inbox'a kopyalamak yeterli)

                // 4. Bildirimi Kapat
                NotificationManagerCompat.from(context).cancel(notificationId)
            }

            Toast.makeText(context, "$sender güvenli listeye eklendi.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToInbox(context: Context, sender: String, body: String) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sender)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
    }
}