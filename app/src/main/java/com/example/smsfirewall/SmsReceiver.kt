package com.example.smsfirewall

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Sadece varsayılan SMS uygulamasıyken gelen mesajları dinle
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {

            // İşlemi arka planda yapacağımızı sisteme bildiriyoruz (Sistem receiver'ı öldürmesin diye)
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Veritabanı bağlantısını burada, güvenli alanda açıyoruz
                    val db = AppDatabase.getDatabase(context)
                    val blockedDao = db.blockedWordDao()
                    val spamDao = db.spamMessageDao()

                    // Mesaj parçalarını al
                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

                    if (messages.isNotEmpty()) {
                        // 1. ÖNEMLİ DÜZELTME: Parçalı mesajları birleştir
                        val fullMessageBody = StringBuilder()
                        for (sms in messages) {
                            sms.messageBody?.let { fullMessageBody.append(it) }
                        }

                        // İlk parçadan gönderen bilgisini ve zamanı al (hepsi aynıdır)
                        val sender = messages[0].originatingAddress ?: "Bilinmeyen"
                        val timestamp = messages[0].timestampMillis
                        val messageContent = fullMessageBody.toString()

                        // Spam Kontrolü (Kelime listesini çek)
                        val blockedWords = blockedDao.getWordListRaw()
                        val isSpam = blockedWords.any { messageContent.lowercase().contains(it.lowercase()) }

                        if (isSpam) {
                            Log.d("SMS_FIREWALL", "🚫 SPAM YAKALANDI: $sender")
                            // Spam ise özel tabloya kaydet
                            spamDao.insert(SpamMessage(sender = sender, body = messageContent, date = timestamp))
                            // Inbox'a kaydetmiyoruz, böylece ana ekrana düşmüyor.
                        } else {
                            // Temiz mesaj: Sistemin Inbox'ına kaydet
                            saveSmsToDeviceInbox(context, sender, messageContent, timestamp)

                            // Kullanıcıya bildirim göster
                            showNotification(context, sender, messageContent)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SMS_FIREWALL", "Receiver Hatası: ${e.message}")
                    e.printStackTrace()
                } finally {
                    // İşlem bitti, sistemi serbest bırak
                    pendingResult.finish()
                }
            }
        }
    }

    private fun saveSmsToDeviceInbox(context: Context, sender: String, body: String, date: Long) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, sender)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, date)
                put(Telephony.Sms.READ, 0) // 0 = Okunmadı
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX) // Gelen Kutusu
                // Not: thread_id vermiyoruz, Android otomatik eşleştiriyor.
            }

            // İçerik sağlayıcı (ContentResolver) aracılığıyla SMS veritabanına yaz
            val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            Log.d("SMS_FIREWALL", "Mesaj Inbox'a kaydedildi: $uri")

        } catch (e: Exception) {
            Log.e("SMS_FIREWALL", "Mesaj kaydetme hatası: ${e.message}")
        }
    }

    private fun showNotification(context: Context, sender: String, body: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sms_channel_id"

        // Bildirim kanalı oluştur (Android 8.0+ için zorunlu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Gelen Mesajlar",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Gelen SMS bildirimleri"
            channel.enableLights(true)
            channel.lightColor = Color.BLUE
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(), // Benzersiz RequestCode
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.sym_action_chat) // Kendi ikonunu (R.drawable.ic_notification) koyabilirsin
            .setContentTitle(sender)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}