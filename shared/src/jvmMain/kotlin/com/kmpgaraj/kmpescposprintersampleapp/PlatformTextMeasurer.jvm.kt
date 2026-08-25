package com.kmpgaraj.kmpescposprintersampleapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver

@Composable
actual fun rememberEscPosTextMeasurerFactory(): suspend () -> TextMeasurer {
    return { TextMeasurer(defaultFontFamilyResolver = createFontFamilyResolver()) }
}
