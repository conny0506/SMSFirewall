package com.ornek.smsfirewall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // MMS işlemleri burada yapılır ama biz şimdilik boş bırakacağız.
    }
}