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
import kotlinx.coroutines.withContext

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRUST_NUMBER) return

        if (!SmsRoleUtils.isAppDefaultSmsHandler(context)) {
            Toast.makeText(context, context.getString(R.string.toast_requires_default_sms_app), Toast.LENGTH_SHORT).show()
            return
        }

        val sender = intent.getStringExtra(EXTRA_SENDER) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        val pendingResult = goAsync()
        val db = AppDatabase.getDatabase(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Numarayi Guvenli Listeye Ekle
                db.trustedNumberDao().insert(TrustedNumber(sender))

                // 2. Mesaji Inbox'a (Gelen Kutusuna) Tasi
                saveToInbox(context, sender, body)

                // 3. Bildirimi Kapat
                NotificationManagerCompat.from(context).cancel(notificationId)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_trusted_number_added, sender),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                pendingResult.finish()
            }
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

    companion object {
        const val ACTION_TRUST_NUMBER = "ACTION_TRUST_NUMBER"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_BODY = "body"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}