package dev.escpos.sample.data.platform

import dev.escpos.fonts.NotoFontLookup
import dev.escpos.render.FontLookup
import dev.escpos.sample.generated.resources.Res
import dev.escpos.sample.generated.resources.lato
import dev.escpos.sample.generated.resources.merriweather
import dev.escpos.sample.generated.resources.montserrat
import dev.escpos.sample.generated.resources.noto_sans
import dev.escpos.sample.generated.resources.nunito
import dev.escpos.sample.generated.resources.open_sans
import dev.escpos.sample.generated.resources.oswald
import dev.escpos.sample.generated.resources.poppins
import dev.escpos.sample.generated.resources.pt_sans
import dev.escpos.sample.generated.resources.raleway
import dev.escpos.sample.generated.resources.roboto
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * escpos-fonts ships no font bytes of its own (see [NotoFontLookup]'s KDoc) — this app bundles
 * its own copies under `composeResources/font/` (with OFL license texts under `licenses/`)
 * purely so the font picker shows [NotoFontLookup.CONTENT_FONTS]'s ten faces working out of the
 * box, without every real consumer of the library being forced to ship them too.
 *
 * Reads every bundled file once and hands the bytes to [NotoFontLookup.create] — exactly the
 * wiring any consumer app would do with its own fonts. An id [NotoFontLookup.create] isn't given
 * bytes for falls back to [NotoFontLookup.NOTO_SANS], so a font missing here degrades to the
 * default face rather than failing the render.
 */
suspend fun loadFontLookup(): FontLookup {
    val environment = getSystemResourceEnvironment()
    suspend fun bytes(resource: FontResource) = getFontResourceBytes(environment, resource)

    val fonts = mapOf(
        NotoFontLookup.NOTO_SANS to bytes(Res.font.noto_sans),
        NotoFontLookup.ROBOTO to bytes(Res.font.roboto),
        NotoFontLookup.OPEN_SANS to bytes(Res.font.open_sans),
        NotoFontLookup.LATO to bytes(Res.font.lato),
        NotoFontLookup.MONTSERRAT to bytes(Res.font.montserrat),
        NotoFontLookup.OSWALD to bytes(Res.font.oswald),
        NotoFontLookup.RALEWAY to bytes(Res.font.raleway),
        NotoFontLookup.POPPINS to bytes(Res.font.poppins),
        NotoFontLookup.NUNITO to bytes(Res.font.nunito),
        NotoFontLookup.PT_SANS to bytes(Res.font.pt_sans),
        NotoFontLookup.MERRIWEATHER to bytes(Res.font.merriweather),
    )
    return NotoFontLookup.create(fonts)
}
