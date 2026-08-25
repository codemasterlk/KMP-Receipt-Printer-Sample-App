package com.kmpgaraj.kmpescposprintersampleapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextMeasurer

/**
 * escpos-render needs a [TextMeasurer] built directly by the caller
 * (`TextMeasurer(defaultFontFamilyResolver = ...)`), not the
 * `rememberTextMeasurer()` composable, and expects that construction to
 * happen off the UI thread.
 *
 * Android's `createFontFamilyResolver(context)` needs a [android.content.Context],
 * which is only available from composition, while the JVM/iOS (Skiko) form
 * takes no argument. This factory captures whatever each platform needs while
 * still in composition and defers the actual (possibly slow) construction to
 * the returned suspend lambda, which callers should run off the main thread —
 * see [printSampleReceipt].
 */
@Composable
expect fun rememberEscPosTextMeasurerFactory(): suspend () -> TextMeasurer
