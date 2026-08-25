package dev.escpos.sample

/**
 * Every transport this sample family knows how to drive. Not every platform actually supports
 * every entry — `escpos-transport-serial`/`-spooler` stub Unavailable on Android/iOS, and
 * `escpos-transport-bt` stubs Unavailable on iOS (see each module's build.gradle.kts) — so
 * [availableTransportKinds] reports which subset a given platform's sample app should actually
 * offer a picker for, rather than showing every entry everywhere and letting most of them sit
 * permanently empty.
 */
public enum class TransportKind(public val label: String) {
    Tcp("TCP / Wi-Fi"),
    Bluetooth("Bluetooth SPP"),
    Usb("USB"),
    Serial("Serial / COM"),
    Spooler("Print spooler (RAW)"),
}

/**
 * The [TransportKind]s worth showing a device picker for on the current platform — see
 * [TransportKind]'s KDoc for why this isn't just `TransportKind.entries` everywhere. An `expect`
 * function rather than an `expect enum class`: Kotlin requires an `expect enum class`'s `actual`
 * to declare the exact same entries on every target, which is exactly the constraint this needs
 * to NOT have.
 */
public expect fun availableTransportKinds(): List<TransportKind>
