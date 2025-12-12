package com.example.smsfirewall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import android.widget.Toast

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 1. Gelen yayının SMS olup olmadığını kontrol et
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {

            // 2. Mesajları paketten çıkar
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            for (sms in messages) {
                val sender = sms.originatingAddress ?: "Bilinmiyor"
                val messageBody = sms.messageBody ?: ""

                Log.d("SMS_FIREWALL", "SMS Yakalandı! Gönderen: $sender, Mesaj: $messageBody")

                // 3. BURASI FİLTRELEME NOKTASI
                // Basit bir test filtresi yapalım:
                if (messageBody.contains("bahis", ignoreCase = true) || messageBody.contains("bet", ignoreCase = true)) {
                    // Normalde burada mesajı veritabanına kaydetmeden silerdik veya sessize alırdık.
                    // Varsayılan uygulama olduğumuz için bildirimi biz yönetiyoruz.
                    // Eğer burada bir bildirim (Notification) kodu yazmazsak, kullanıcı SESSİZCE engellemiş oluruz.

                    Log.d("SMS_FIREWALL", "SPAM TESPİT EDİLDİ! Mesaj engellendi.")
                    // Kullanıcıya hissettirmemek için Toast göstermiyoruz ama testi görmek için açabilirsin:
                    Toast.makeText(context, "Spam Engellendi: $sender", Toast.LENGTH_LONG).show()

                } else {
                    // Spam değilse ne olacak?
                    // Gerçek bir uygulamada burada veritabanına kaydeder ve kullanıcıya Bildirim (Notification) göndeririz.
                    Log.d("SMS_FIREWALL", "Temiz mesaj.")
                }
            }
        }
    }
}