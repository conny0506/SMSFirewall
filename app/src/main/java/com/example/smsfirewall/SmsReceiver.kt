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
        // Sadece varsayilan SMS uygulamasiyken gelen mesajlari dinle
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {

            // Islemi arka planda yapacagimizi sisteme bildiriyoruz (Sistem receiver'i oldurmesin diye)
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Veritabani baglantisini burada, guvenli alanda aciyoruz
                    val db = AppDatabase.getDatabase(context)
                    val blockedDao = db.blockedWordDao()
                    val spamDao = db.spamMessageDao()

                    // Mesaj parcalarini al
                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

                    if (messages.isNotEmpty()) {
                        // Parcali mesajlari birlestir
                        val fullMessageBody = StringBuilder()
                        for (sms in messages) {
                            sms.messageBody?.let { fullMessageBody.append(it) }
                        }

                        // Ilk parcadan gonderen bilgisini ve zamani al (hepsi aynidir)
                        val sender = messages[0].originatingAddress ?: context.getString(R.string.label_unknown_sender)
                        val timestamp = messages[0].timestampMillis
                        val messageContent = fullMessageBody.toString()

                        // Spam Kontrolu (Kelime listesini cek)
                        val blockedWords = blockedDao.getWordListRaw()
                        val isSpam = blockedWords.any { messageContent.lowercase().contains(it.lowercase()) }

                        if (isSpam) {
                            if (BuildConfig.DEBUG) {
                                Log.d("SMS_FIREWALL", "SPAM YAKALANDI: $sender")
                            }
                            // Spam ise ozel tabloya kaydet
                            spamDao.insert(SpamMessage(sender = sender, body = messageContent, date = timestamp))
                            // Inbox'a kaydetmiyoruz, boylece ana ekrana dusmuyor.
                        } else {
                            // Temiz mesaj: Sistemin Inbox'ina kaydet
                            saveSmsToDeviceInbox(context, sender, messageContent, timestamp)

                            // Kullaniciya bildirim goster
                            showNotification(context, sender, messageContent)
                        }
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.e("SMS_FIREWALL", "Receiver Hatasi: ${e.message}", e)
                    }
                } finally {
                    // Islem bitti, sistemi serbest birak
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
                put(Telephony.Sms.READ, 0) // 0 = Okunmadi
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX) // Gelen Kutusu
                // Not: thread_id vermiyoruz, Android otomatik eslestiriyor.
            }

            // Icerik saglayici (ContentResolver) araciligiyla SMS veritabanina yaz
            val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            if (BuildConfig.DEBUG) {
                Log.d("SMS_FIREWALL", "Mesaj Inbox'a kaydedildi: $uri")
            }

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("SMS_FIREWALL", "Mesaj kaydetme hatasi: ${e.message}", e)
            }
        }
    }

    private fun showNotification(context: Context, sender: String, body: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sms_channel_id"
        val showContent = AppSettings.isNotificationContentVisible(context)

        // Bildirim kanali olustur (Android 8.0+ icin zorunlu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                context.getString(R.string.notification_channel_inbox_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = context.getString(R.string.notification_channel_inbox_description)
            channel.enableLights(true)
            channel.lightColor = Color.BLUE
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentTitle = if (showContent) sender else context.getString(R.string.label_new_message)
        val contentText = if (showContent) body else context.getString(R.string.label_new_message)
        val visibility = if (showContent) {
            NotificationCompat.VISIBILITY_PRIVATE
        } else {
            NotificationCompat.VISIBILITY_SECRET
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(visibility)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
