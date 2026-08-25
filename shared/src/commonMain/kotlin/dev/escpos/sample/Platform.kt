package dev.escpos.sample

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextMeasurer
import com.kmpgaraj.kmpescposprintersampleapp.getPlatform

/**
 * Whatever platform-specific object [platformTextMeasurer] needs to build a font resolver — an
 * Android `Context` (Android's `createFontFamilyResolver(Context)` overload needs one), nothing
 * on every other target (the JVM/iOS Skiko `createFontFamilyResolver()` overload takes none). See
 * [SampleApp]'s KDoc: each entry point (`MainActivity`, desktop's `main()`, iOS's
 * `MainViewController`) supplies its own actual value and passes it into `SampleApp(context)`.
 */
public expect class PlatformContext

/**
 * Builds a [TextMeasurer] the way [dev.escpos.render.ReceiptRenderer] needs one — constructed
 * directly (`TextMeasurer(defaultFontFamilyResolver = ...)`), not the `rememberTextMeasurer()`
 * composable, and safe to call off the UI thread (FR-3.6/NFR-3.2). [SampleAppState.newRenderer]
 * calls this fresh before every render rather than caching one instance.
 */
public expect suspend fun platformTextMeasurer(context: PlatformContext): TextMeasurer

/** The "Running on" badge's text — delegates to the sample scaffold's existing
 *  [com.kmpgaraj.kmpescposprintersampleapp.Platform] rather than duplicating per-platform detection. */
@Composable
public fun platformLabel(): String = getPlatform().name
