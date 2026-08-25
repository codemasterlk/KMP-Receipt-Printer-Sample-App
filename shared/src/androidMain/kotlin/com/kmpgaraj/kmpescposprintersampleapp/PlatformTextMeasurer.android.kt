package com.kmpgaraj.kmpescposprintersampleapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver

@Composable
actual fun rememberEscPosTextMeasurerFactory(): suspend () -> TextMeasurer {
    // Context must be read on the UI thread during composition; the resolver
    // itself is built lazily off-thread by the caller.
    val context = LocalContext.current.applicationContext
    return { TextMeasurer(defaultFontFamilyResolver = createFontFamilyResolver(context)) }
}
