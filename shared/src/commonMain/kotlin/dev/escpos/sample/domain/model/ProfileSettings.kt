package dev.escpos.sample.domain.model

import dev.escpos.core.CutMode
import dev.escpos.core.PrinterProfile
import dev.escpos.core.RasterMode

/**
 * Everything [dev.escpos.sample.domain.repository.PrinterRepository.buildProfile] needs to build a
 * [PrinterProfile] — a domain-level stand-in for the profile fields presentation state holds as
 * text, so no domain type (this one, or a use case) ever has to depend on the presentation layer's
 * `SampleAppUiState` to do its job. `dotWidth`/`bandHeight`/`columnsOverride` stay `String` here,
 * same as in presentation: `buildProfile` is exactly the place unparseable input turns into "no
 * profile yet" (or, for [columnsOverride], "use the profile's own default") rather than a crash.
 *
 * @property columnsOverride text mode's characters-per-line; blank or unparseable means
 *   [PrinterProfile]'s own computed default (`dotWidth / 12`, assuming the printer's Font A).
 */
data class ProfileSettings(
    val name: String,
    val dotWidth: String,
    val rasterMode: RasterMode,
    val bandHeight: String,
    val cut: CutMode,
    val drawerPin: Int,
    val columnsOverride: String = "",
)
