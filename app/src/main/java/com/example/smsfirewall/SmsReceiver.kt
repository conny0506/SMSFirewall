package com.example.smsfirewall

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            if (messages.isNotEmpty()) {
                // Gelen mesajı yakaladık, şimdi veritabanına kaydedelim
                saveSmsToInbox(context, messages)
            }
        }
    }

    private fun saveSmsToInbox(context: Context, messages: Array<SmsMessage>) {
        // Veritabanı işlemi olduğu için arka planda (IO) yapıyoruz
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // SMS parçalı gelebilir (uzun mesajlar), onları birleştiriyoruz
                val sender = messages[0].displayOriginatingAddress
                val body = StringBuilder()
                for (msg in messages) {
                    body.append(msg.messageBody)
                }
                val date = messages[0].timestampMillis

                // Veritabanına yazılacak değerleri hazırlıyoruz
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, sender)
                    put(Telephony.Sms.BODY, body.toString())
                    put(Telephony.Sms.DATE, date)
                    put(Telephony.Sms.READ, 0) // 0 = Okunmadı
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX) // 1 = Gelen Kutusu
                }

                // Android SMS veritabanına ekliyoruz
                context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}