package dev.escpos.sample.domain.model

import dev.escpos.core.RuleStyle

/**
 * Everything that changes what [dev.escpos.sample.domain.receipt.customInvoiceReceipt] builds
 * beyond the profile, item count, and font — grouped into one domain-level value so use cases
 * don't each grow five more parameters, and so presentation can map its own state in one place.
 *
 * Everything except [mode] is diagnostic-only, added while chasing a real printer's rough paper
 * feed; the defaults are the receipt's original, unchanged look.
 *
 * @property mode [PrintMode.Text] has no raster path, so the logo/QR/barcode are left out of the
 *   receipt entirely rather than sent and made to fail.
 * @property ruleStyle [RuleStyle.Dashed] avoids a solid full-width black row of dots.
 * @property includeLogoQrBarcode `false` drops the logo, QR, and barcode (all high dot-density
 *   blocks) so text-and-rules content can be tested in isolation.
 * @property boldHeadings `false` prints the store name and Total at normal weight, isolating
 *   whether bold stroke density is a contributor.
 * @property itemGapDots blank dots between back-to-back line groups.
 */
data class ReceiptOptions(
    val mode: PrintMode = PrintMode.Graphic,
    val ruleStyle: RuleStyle = RuleStyle.Solid,
    val includeLogoQrBarcode: Boolean = true,
    val boldHeadings: Boolean = true,
    val itemGapDots: Int = DEFAULT_ITEM_GAP_DOTS,
) {
    companion object {
        const val DEFAULT_ITEM_GAP_DOTS: Int = 6
    }
}
