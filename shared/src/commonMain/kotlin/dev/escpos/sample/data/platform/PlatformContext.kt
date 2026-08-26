package dev.escpos.sample.data.platform

import androidx.compose.ui.text.TextMeasurer

/**
 * Whatever platform-specific object [platformTextMeasurer] needs to build a font resolver — an
 * Android `Context` (Android's `createFontFamilyResolver(Context)` overload needs one), nothing
 * on every other target (the JVM/iOS Skiko `createFontFamilyResolver()` overload takes none).
 * Each entry point (`MainActivity`, desktop's `main()`, iOS's `MainViewController`) supplies its
 * own actual value and passes it into `SampleApp(context)`, which forwards it into
 * [dev.escpos.sample.data.repository.PrinterRepositoryImpl].
 */
expect class PlatformContext

/**
 * Builds a [TextMeasurer] the way [dev.escpos.render.ReceiptRenderer] needs one — constructed
 * directly (`TextMeasurer(defaultFontFamilyResolver = ...)`), not the `rememberTextMeasurer()`
 * composable, and safe to call off the UI thread (FR-3.6/NFR-3.2).
 * [dev.escpos.sample.data.repository.PrinterRepositoryImpl.createRenderer] calls this fresh
 * before every render rather than caching one instance.
 */
expect suspend fun platformTextMeasurer(context: PlatformContext): TextMeasurer
