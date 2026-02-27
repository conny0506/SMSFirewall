package com.example.smsfirewall

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.toDayTime(): String {
    val format = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    return format.format(Date(this))
}
