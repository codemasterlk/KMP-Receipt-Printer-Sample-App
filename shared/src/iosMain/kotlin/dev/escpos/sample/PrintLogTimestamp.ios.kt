package dev.escpos.sample

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

public actual fun logTimestampNow(): String {
    val formatter = NSDateFormatter().apply { dateFormat = "HH:mm:ss" }
    return formatter.stringFromDate(NSDate())
}
