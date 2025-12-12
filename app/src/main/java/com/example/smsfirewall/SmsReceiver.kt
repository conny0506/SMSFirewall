package com.example.smsfirewall

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {

            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = AppDatabase.getDatabase(context).blockedWordDao()
                    val blockedWords = dao.getWordListRaw()
                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

                    for (sms in messages) {
                        val messageBody = sms.messageBody?.lowercase() ?: ""
                        val sender = sms.originatingAddress ?: "?"
                        val timestamp = sms.timestampMillis

                        // KONTROL: Yasaklı kelime var mı?
                        val isSpam = blockedWords.any { messageBody.contains(it.lowercase()) }

                        if (isSpam) {
                            Log.d("SMS_FIREWALL", "🚫 SPAM ENGELLENDİ: $sender")
                            // Spam ise HİÇBİR ŞEY yapmıyoruz.
                            // Veritabanına kaydetmiyoruz, bildirim göstermiyoruz.
                            // Böylece mesaj sonsuzluğa karışıyor.
                        } else {
                            Log.d("SMS_FIREWALL", "✅ Temiz mesaj. Kaydediliyor...")

                            // 1. Mesajı Android'in SMS Veritabanına Kaydet (Inbox'a yaz)
                            saveSmsToDeviceInbox(context, sender, sms.messageBody, timestamp)

                            // 2. Kullanıcıya Bildirim Göster
                            showNotification(context, sender, sms.messageBody)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SMS_FIREWALL", "Hata: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    // Mesajı telefonun gelen kutusuna kaydeden fonksiyon
    private fun saveSmsToDeviceInbox(context: Context, sender: String, body: String, date: Long) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, sender)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, date)
                put(Telephony.Sms.READ, 0) // 0 = Okunmadı, 1 = Okundu
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e("SMS_FIREWALL", "Mesaj kaydedilemedi: ${e.message}")
        }
    }

    // Bildirim gösteren fonksiyon
    private fun showNotification(context: Context, sender: String, body: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Bildirime tıklayınca uygulamayı açmak için Intent
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, "sms_channel_id")
            .setSmallIcon(android.R.drawable.sym_action_chat) // Varsayılan bir ikon kullandık
            .setContentTitle(sender)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(Color.BLUE)
            .build()

        // Her bildirim için rastgele bir ID (veya timestamp) kullanarak üst üste binmesini önleyebiliriz
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}