package dev.escpos.sample.data.platform

import android.content.Context

actual fun createKeyValueStorage(context: PlatformContext): KeyValueStorage =
    SharedPreferencesStorage(context.context.applicationContext)

private class SharedPreferencesStorage(context: Context) : KeyValueStorage {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    // apply(), not commit(): the in-memory copy updates immediately and the disk write happens on
    // SharedPreferences' own background thread — nothing here needs to block on it.
    override fun putStrings(values: Map<String, String?>) {
        prefs.edit().apply {
            values.forEach { (key, value) -> if (value == null) remove(key) else putString(key, value) }
        }.apply()
    }

    private companion object {
        const val FILE_NAME = "printer_setup"
    }
}
