package dev.escpos.sample.domain.model

import dev.escpos.core.CutMode
import dev.escpos.core.PrinterProfile
import dev.escpos.core.RasterMode

/**
 * Everything [dev.escpos.sample.domain.repository.PrinterRepository.buildProfile] needs to build a
 * [PrinterProfile] — a domain-level stand-in for the profile fields presentation state holds as
 * text, so no domain type (this one, or a use case) ever has to depend on the presentation layer's
 * `SampleAppUiState` to do its job. `dotWidth`/`bandHeight` stay `String` here, same as in
 * presentation: `buildProfile` is exactly the place unparseable input turns into "no profile yet"
 * rather than a crash, and that parsing has to happen somewhere.
 */
data class ProfileSettings(
    val name: String,
    val dotWidth: String,
    val rasterMode: RasterMode,
    val bandHeight: String,
    val cut: CutMode,
    val drawerPin: Int,
)
