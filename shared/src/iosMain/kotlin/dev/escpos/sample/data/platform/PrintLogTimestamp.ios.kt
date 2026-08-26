package dev.escpos.sample.data.platform

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

actual fun logTimestampNow(): String {
    val formatter = NSDateFormatter().apply { dateFormat = "HH:mm:ss" }
    return formatter.stringFromDate(NSDate())
}
