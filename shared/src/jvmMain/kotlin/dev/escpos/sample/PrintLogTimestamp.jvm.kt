package dev.escpos.sample

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

public actual fun logTimestampNow(): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
