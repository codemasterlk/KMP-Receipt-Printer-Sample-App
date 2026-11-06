package dev.escpos.sample.data.platform

import platform.Foundation.NSUserDefaults

actual fun createKeyValueStorage(context: PlatformContext): KeyValueStorage = UserDefaultsStorage()

private class UserDefaultsStorage : KeyValueStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putStrings(values: Map<String, String?>) {
        values.forEach { (key, value) ->
            if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
        }
    }
}
