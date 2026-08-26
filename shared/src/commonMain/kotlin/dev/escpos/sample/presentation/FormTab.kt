package dev.escpos.sample.presentation

/**
 * Which of the three panes `FormPane` shows at once — see [SampleApp]'s KDoc for why this is a
 * plain tab switch, separate from the form/preview split that stays visible regardless of which
 * tab is active.
 */
enum class FormTab(val label: String) {
    /** Connection (transport/device) plus the printer profile — `ConnectionCard` + `ProfileCard`. */
    PrinterSettings("Printer"),
    /** The fixed receipt content: item count and font pickers — `ReceiptContentCard`. */
    ReceiptContent("Receipt"),
    /** This session's print history — `PrintLogCard`. */
    PrintLog("Log"),
}
