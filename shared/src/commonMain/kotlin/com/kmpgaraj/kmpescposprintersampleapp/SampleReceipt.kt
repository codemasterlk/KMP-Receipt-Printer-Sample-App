package com.kmpgaraj.kmpescposprintersampleapp

import dev.escpos.core.*

/**
 * A small receipt built with the escpos-core DSL — a shop header, a couple of
 * line items, a total, and a QR code — following the pattern from the
 * TerminalPrinterKMP README quick start.
 *
 * Nothing here talks to a printer; [dev.escpos.render.ReceiptRenderer] turns
 * this into ESC/POS bytes, and [printSampleReceipt] sends those bytes out.
 */
fun buildSampleReceipt() = run {
    val body = TextStyle(fontSize = TextSize.Medium)
    val bold = body.copy(fontWeight = FontWeight.Bold)
    val small = body.copy(fontSize = TextSize.Small)

    return receipt(defaultStyle = body) {
        text("KMP Coffee House", style = bold, align = TextAlign.Center)
        text("123 Galle Road, Colombo", style = small, align = TextAlign.Center)
        rule()

        line {
            left("Item")
            right("Price")
        }
        line {
            left("Cappuccino")
            right("350.00")
        }
        line {
            left("Croissant")
            right("280.00")
        }
        line {
            left("Fresh Juice")
            right("420.00")
        }

        rule()
        line {
            left("Total", style = bold)
            right("1050.00", style = bold)
        }

        space(20)
        text("Scan to view this receipt online", align = TextAlign.Center, style = small)
        qr("https://example.com/receipt/123")

        space(20)
        text("Thank you for visiting!", align = TextAlign.Center, style = bold)
    }
}
