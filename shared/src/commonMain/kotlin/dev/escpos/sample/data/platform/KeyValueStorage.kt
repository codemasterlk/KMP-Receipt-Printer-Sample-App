package dev.escpos.sample.data.platform

/**
 * The smallest persistent string key/value store this app needs — an interface so
 * [dev.escpos.sample.data.repository.SetupRepositoryImpl] can be tested against an in-memory fake,
 * with the real platform store behind [createKeyValueStorage].
 */
interface KeyValueStorage {
    fun getString(key: String): String?

    /** Writes every entry in one batch; a `null` value removes that key. */
    fun putStrings(values: Map<String, String?>)
}

/**
 * The platform's native small-settings store — `SharedPreferences` on Android, `java.util.prefs`
 * (the Windows registry on Windows) on desktop, `NSUserDefaults` on iOS. Platform APIs rather than
 * a multiplatform-settings dependency: this app stores a few dozen short strings, so a library
 * would be more moving parts than the problem has.
 */
expect fun createKeyValueStorage(context: PlatformContext): KeyValueStorage
