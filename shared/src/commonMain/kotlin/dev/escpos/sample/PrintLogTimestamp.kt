package dev.escpos.sample

/** A short `HH:mm:ss`-style clock-time label for one [PrintLogEntry] — local wall-clock time,
 *  not a full date, since [SampleAppState.printLog] is a same-session, in-memory list only. */
public expect fun logTimestampNow(): String
