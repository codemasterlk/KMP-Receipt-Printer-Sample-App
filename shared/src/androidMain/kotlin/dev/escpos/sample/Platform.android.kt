package dev.escpos.sample

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Wraps the Android `Context` — `createFontFamilyResolver(Context)` needs one; the androidApp
 * module's `MainActivity` supplies it (its `applicationContext`) when it calls
 * `App(context = PlatformContext(applicationContext))`.
 *
 * A wrapper class, not `actual typealias PlatformContext = android.content.Context`: `Context` is
 * an `abstract class`, and Kotlin requires an `expect`/`actual` pair's modality to match — the
 * plain `expect class PlatformContext` on the common side is implicitly `final`, so aliasing
 * straight to `Context` fails with "the modalities are different ('final' vs 'abstract')".
 */
public actual class PlatformContext(public val context: android.content.Context)

public actual suspend fun platformTextMeasurer(context: PlatformContext): TextMeasurer =
    TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(context.context.applicationContext),
        // FR-3.2 — one layout unit equals one printer dot; see dev.escpos.render.ReceiptRenderer's
        // own Density(1f, 1f), which this default matches.
        defaultDensity = Density(1f),
        defaultLayoutDirection = LayoutDirection.Ltr,
    )
