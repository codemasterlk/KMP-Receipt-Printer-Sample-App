package dev.escpos.sample.data.platform

/** A short `HH:mm:ss`-style clock-time label for one print-log entry — local wall-clock time,
 *  not a full date, since the print log is a same-session, in-memory list only. */
expect fun logTimestampNow(): String
