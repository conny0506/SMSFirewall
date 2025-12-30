package com.ornek.smsfirewall // Kendi paket ismin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Buraya daha sonra SMS yakalama kodlarını yazacağız.
    }
}