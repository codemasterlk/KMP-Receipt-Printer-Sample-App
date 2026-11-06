package dev.escpos.sample.data.platform

import java.util.prefs.Preferences

actual fun createKeyValueStorage(context: PlatformContext): KeyValueStorage = JavaPrefsStorage()

/** `java.util.prefs` — backed by the registry (`HKCU\Software\JavaSoft\Prefs`) on Windows. Values
 *  are capped at 8 KB and keys at 80 characters, far above anything stored here. */
private class JavaPrefsStorage : KeyValueStorage {
    private val prefs: Preferences = Preferences.userRoot().node(NODE)

    override fun getString(key: String): String? = prefs.get(key, null)

    override fun putStrings(values: Map<String, String?>) {
        values.forEach { (key, value) -> if (value == null) prefs.remove(key) else prefs.put(key, value) }
        // A failed flush (a read-only registry hive, a locked-down profile) must not crash the
        // app over a convenience feature — the value is still held in memory for this session.
        runCatching { prefs.flush() }
    }

    private companion object {
        const val NODE = "dev/escpos/sample"
    }
}
