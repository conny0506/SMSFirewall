package com.example.smsfirewall

import android.content.Context
import androidx.core.content.edit

object AppSettings {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_SHOW_UNREAD_BADGES = "show_unread_badges"
    private const val KEY_CHAT_BACKGROUND_KEY = "chat_background_key"
    private const val KEY_SHOW_NOTIFICATION_CONTENT = "show_notification_content"
    private const val KEY_PINNED_THREAD_IDS = "pinned_thread_ids"
    private const val KEY_MUTED_THREAD_IDS = "muted_thread_ids"

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
            .edit { putBoolean(KEY_SHOW_UNREAD_BADGES, enabled) }
    }

    fun getChatBackgroundKey(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CHAT_BACKGROUND_KEY, CHAT_BG_CLASSIC)
            ?: CHAT_BG_CLASSIC
    }

    fun setChatBackgroundKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_CHAT_BACKGROUND_KEY, key) }
    }

    fun isNotificationContentVisible(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_NOTIFICATION_CONTENT, true)
    }

    fun setNotificationContentVisible(context: Context, visible: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_SHOW_NOTIFICATION_CONTENT, visible) }
    }

    fun getPinnedThreadIds(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_PINNED_THREAD_IDS, emptySet())
            ?.toSet()
            ?: emptySet()
    }

    fun setPinnedThreadIds(context: Context, threadIds: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putStringSet(KEY_PINNED_THREAD_IDS, threadIds) }
    }

    fun getMutedThreadIds(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_MUTED_THREAD_IDS, emptySet())
            ?.toSet()
            ?: emptySet()
    }

    fun setMutedThreadIds(context: Context, threadIds: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putStringSet(KEY_MUTED_THREAD_IDS, threadIds) }
    }
}
