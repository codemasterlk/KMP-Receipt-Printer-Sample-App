package dev.escpos.sample

import dev.escpos.core.FontWeight
import dev.escpos.core.PrinterProfile
import dev.escpos.core.RasterImage
import dev.escpos.core.Receipt
import dev.escpos.core.TextAlign
import dev.escpos.core.TextStyle
import dev.escpos.core.content
import dev.escpos.core.receipt
import dev.escpos.fonts.NotoFontLookup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One printed line item — plain data now that nothing edits a field in place (see this file's
 *  KDoc: everything the receipt prints is fixed, the only remaining variable is how many of
 *  [HARDCODED_ITEMS] to include). */
internal data class ReceiptLineItem(val name: String, val qty: Int, val price: Double)

// ---------------------------------------------------------------------------------------------
// Hardcoded receipt content. Nothing below is user-editable — the only thing a caller varies is
// how many of HARDCODED_ITEMS to print, via customInvoiceReceipt's itemCount parameter (clamped
// to HARDCODED_ITEMS.size by SampleAppState.itemCount).
// ---------------------------------------------------------------------------------------------

internal const val STORE_NAME = "Supiri1 Grocery"
private const val STORE_ADDRESS = "42 Galle Road, Colombo 03"
private const val STORE_PHONE = "011 234 5678"
internal const val INVOICE_NUMBER = "INV-2026-004517"
private const val CASHIER = "Nimali"
private const val FOOTER_MESSAGE = "Thank you, please come again"

/** Percentage of the subtotal, not a flat amount — a flat discount stopped making sense once the
 *  item count became a user-adjustable 1-100 instead of a fixed hand-typed list. */
private const val DISCOUNT_PERCENT = 5.0
private const val VAT_PERCENT = 18.0

/** 25 literal (name, unit price) pairs [HARDCODED_ITEMS] cycles through — real product names, not
 *  meant to be exhaustive or a catalog of anything real (same disclaimer the old random-item pool
 *  carried). */
private val ITEM_CATALOG: List<Pair<String, Double>> = listOf(
    "Coconut oil 500ml" to 625.00, "Basmati rice 5kg" to 3990.00, "Ceylon tea 250g" to 810.00,
    "Milk powder 400g" to 990.00, "Sugar 1kg" to 295.00, "Wheat flour 1kg" to 410.00,
    "Instant noodles" to 180.00, "Red dhal 1kg" to 540.00, "Chili powder 100g" to 380.00,
    "Turmeric powder 100g" to 220.00, "Bread loaf" to 220.00, "Eggs (12)" to 590.00,
    "Butter 200g" to 450.00, "Yogurt cup" to 120.00, "Bathing soap" to 150.00,
    "Toothpaste" to 340.00, "Shampoo 200ml" to 480.00, "Dish soap 500ml" to 310.00,
    "Biscuit pack" to 190.00, "Chocolate bar" to 210.00, "Bottled water 1L" to 90.00,
    "Instant coffee 100g" to 620.00, "Onions 1kg" to 260.00, "Potatoes 1kg" to 240.00,
    "Canned fish" to 480.00,
)

/**
 * 100 fixed line items — [ITEM_CATALOG] cycled with a deterministic (not random) quantity, so the
 * list is exactly the same every run rather than freshly randomized. "Hardcoded" per the
 * confirmed "no user input, required data hardcoded" request: this replaces the old
 * editable/add/remove item list and the "Generate random items" quick-fill entirely.
 */
internal val HARDCODED_ITEMS: List<ReceiptLineItem> = List(100) { index ->
    val (name, price) = ITEM_CATALOG[index % ITEM_CATALOG.size]
    ReceiptLineItem(name = name, qty = 1 + (index % 5), price = price)
}

/**
 * Builds the receipt shown in the preview panel and sent by "Print receipt". Every field is fixed
 * (see the constants/[HARDCODED_ITEMS] above) except [itemCount] and [fontFamilyId] —
 * [SampleAppState.itemCount] lets the user dial the former between 1 and [HARDCODED_ITEMS].size,
 * and [SampleAppState.contentFontId] the latter between [NotoFontLookup.NOTO_SANS] (default) and
 * any of [NotoFontLookup.CONTENT_FONTS]. Subtotal/discount/VAT/total are computed from the
 * included items, never separately editable, so they can't drift out of arithmetic sync.
 *
 * QR and a Code 39 barcode both encode digits taken from [INVOICE_NUMBER] and print as the very
 * last two blocks, after the footer message — "bottom of the receipt" per the confirmed request.
 * See `dev.escpos.render.barcode.Code39Encoder`'s KDoc for the barcode's digits-only scope and
 * its "unverified against a real scanner" caveat.
 */
internal fun customInvoiceReceipt(
    profile: PrinterProfile,
    itemCount: Int,
    fontFamilyId: String = NotoFontLookup.NOTO_SANS,
): Receipt {
    val body = TextStyle(fontFamily = fontFamilyId, fontWeight = FontWeight.SemiBold, fontSize = 24)
    val small = body.copy(fontSize = 24)
    val title = body.copy(fontSize = 28, fontWeight = FontWeight.Bold)
    val totalStyle = body.copy(fontSize = 24, fontWeight = FontWeight.Bold)

    val items = HARDCODED_ITEMS.take(itemCount.coerceIn(1, HARDCODED_ITEMS.size))
    val lineTotals = items.map { it.qty * it.price }
    val subtotal = lineTotals.sum()
    val discountAmount = subtotal * (DISCOUNT_PERCENT / 100.0)
    val vatAmount = (subtotal - discountAmount) * (VAT_PERCENT / 100.0)
    val total = subtotal - discountAmount + vatAmount

    val invoiceDigits = INVOICE_NUMBER.filter { it.isDigit() }
    val invoiceDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date())

    return receipt(body) {
        space(4)
        image(solidSquareLogo(), align = TextAlign.Center)
        space(4)
        text(STORE_NAME, style = title, align = TextAlign.Center)
        text(STORE_ADDRESS, style = small, align = TextAlign.Center)
        text(STORE_PHONE, style = small, align = TextAlign.Center)
        space(8)
        rule()
        space(8)

        line { left("Invoice", style = small); right(INVOICE_NUMBER, style = small) }
        line { left("Date", style = small); right(invoiceDate, style = small) }
        line { left("Cashier", style = small); right(CASHIER, style = small) }
        space(8)
        rule()
        space(8)

        // Each item prints as two lines: the numbered name on its own full-width line, then a
        // price / qty / line-total row below it. Uses line { } (FR-2.1) rather than table() —
        // line's left/center/right cells sit at fixed weights of the line width, so the three
        // numbers land in the same relative position for every item regardless of digit count,
        // where table()'s content() column would instead size itself to its widest cell.
        items.forEachIndexed { index, item ->
            text("${index + 1}.${item.name}", style = small)
            line(gap = 20) {
                left(money(item.price), style = small)
                center(item.qty.toString(), style = small)
                right(money(lineTotals[index]), style = small)
            }
            if (index != items.lastIndex) space(6)
        }
        space(8)
        rule()
        space(8)

        line { left("Subtotal", style = small); right(money(subtotal), style = small) }
        line { left("Discount $DISCOUNT_PERCENT%", style = small); right("-" + money(discountAmount), style = small) }
        line { left("VAT $VAT_PERCENT%", style = small); right(money(vatAmount), style = small) }
        space(4)
        line { left("Total", style = totalStyle); right(money(total), style = totalStyle) }
        space(12)

        text(FOOTER_MESSAGE, style = small, align = TextAlign.Center)
        space(12)
        qr(invoiceDigits, align = TextAlign.Center)
        space(8)
        barcode(invoiceDigits, align = TextAlign.Center)
    }
}

private fun money(value: Double): String = "%,.2f".format(value)

/** A plain solid square standing in for a store logo — this sample has no image asset pipeline,
 *  just enough to show [dev.escpos.core.ReceiptBuilder.image] placement in the preview. */
private fun solidSquareLogo(): RasterImage {
    val side = 200
    return RasterImage(side, side, IntArray(side * side) { BLACK_ARGB })
}

private const val BLACK_ARGB = 0xFF000000.toInt()
