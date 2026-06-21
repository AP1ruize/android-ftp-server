package com.example.ftpembed

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FtpLogFormatter {
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun formatEntry(text: String): String = "[${format.format(Date())}] $text"

    fun currentTimestamp(): String = format.format(Date())
}
