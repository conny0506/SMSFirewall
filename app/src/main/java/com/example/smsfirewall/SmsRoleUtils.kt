package com.example.smsfirewall

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.provider.Telephony

object SmsRoleUtils {
    fun isAppDefaultSmsHandler(context: Context): Boolean {
        val packageIsDefault = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            val roleHeld = roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
            return roleHeld || packageIsDefault
        }
        return packageIsDefault
    }
}
