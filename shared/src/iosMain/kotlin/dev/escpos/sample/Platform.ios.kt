package dev.escpos.sample

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Nothing is needed to build a font resolver on iOS/Skiko — `createFontFamilyResolver()` takes
 *  no argument there, same as desktop. */
public actual typealias PlatformContext = Unit

public actual suspend fun platformTextMeasurer(context: PlatformContext): TextMeasurer =
    TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(),
        // FR-3.2 — one layout unit equals one printer dot; see dev.escpos.render.ReceiptRenderer's
        // own Density(1f, 1f), which this default matches.
        defaultDensity = Density(1f),
        defaultLayoutDirection = LayoutDirection.Ltr,
    )
