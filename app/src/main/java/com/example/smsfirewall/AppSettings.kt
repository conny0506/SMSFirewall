package com.example.smsfirewall

import android.content.Context

object AppSettings {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_SHOW_UNREAD_BADGES = "show_unread_badges"
    private const val KEY_CHAT_BACKGROUND_KEY = "chat_background_key"

    const val CHAT_BG_CLASSIC = "classic"
    const val CHAT_BG_OCEAN = "ocean"
    const val CHAT_BG_MINT = "mint"
    const val CHAT_BG_SUNSET = "sunset"

    fun isUnreadBadgesEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_UNREAD_BADGES, true)
    }

    fun setUnreadBadgesEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_UNREAD_BADGES, enabled)
            .apply()
    }

    fun getChatBackgroundKey(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CHAT_BACKGROUND_KEY, CHAT_BG_CLASSIC)
            ?: CHAT_BG_CLASSIC
    }

    fun setChatBackgroundKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CHAT_BACKGROUND_KEY, key)
            .apply()
    }
}
