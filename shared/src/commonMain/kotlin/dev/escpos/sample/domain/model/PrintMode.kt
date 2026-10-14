package dev.escpos.sample.domain.model

/**
 * Which pipeline a job is sent through — escpos-core's two alternatives: [Graphic] renders the
 * receipt to a raster image (any script, needs escpos-render and bundled fonts); [Text] formats it
 * with the printer's own built-in font via native ESC/POS text commands (English/Latin only, but
 * works on cheap printers with no working `GS v 0`/`ESC *` implementation). Not a
 * [dev.escpos.core.PrinterProfile] field: the same printer profile (dot width, columns, cut, …)
 * applies to either pipeline — this is a per-job choice, not a printer capability the library
 * tracks for you.
 */
enum class PrintMode(val label: String, val description: String) {
    Graphic(
        "Graphic",
        "Renders to an image — any language, needs a printer with working GS v 0 / ESC * support.",
    ),
    Text(
        "Text",
        "Native ESC/POS text commands — English/Latin only, but works on printers with no " +
            "graphic support at all.",
    ),
}
