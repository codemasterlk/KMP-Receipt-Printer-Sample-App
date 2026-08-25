package com.kmpgaraj.kmpescposprintersampleapp

import androidx.compose.runtime.Composable
import dev.escpos.sample.PlatformContext
import dev.escpos.sample.SampleApp

/**
 * Entry point every platform's app module calls into (`MainActivity`, desktop's `main()`, iOS's
 * `MainViewController`) — a thin forward to [SampleApp], the full printer-settings/receipt-
 * content/print-log sample UI. [context] is whatever [dev.escpos.sample.platformTextMeasurer]
 * needs on the calling platform (an Android `Context`, `Unit` everywhere else).
 */
@Composable
fun App(context: PlatformContext) {
    SampleApp(context)
}
