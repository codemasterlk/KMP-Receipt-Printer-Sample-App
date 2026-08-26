package dev.escpos.sample.data.platform

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun logTimestampNow(): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
