package dev.escpos.sample.domain.receipt

import dev.escpos.core.FontWeight
import dev.escpos.core.PrinterProfile
import dev.escpos.core.RasterImage
import dev.escpos.core.Receipt
import dev.escpos.core.RuleStyle
import dev.escpos.core.TextAlign
import dev.escpos.core.TextSize
import dev.escpos.core.TextStyle
import dev.escpos.core.receipt
import dev.escpos.fonts.NotoFontLookup
import dev.escpos.sample.domain.model.PrintMode

// The short test prints the setup wizard sends. Each is built so the user can judge the result by
// looking at the paper alone — labelled edges, a known sentence to find — because the printer
// never reports anything back. All are deliberately short: they cost paper every time.

private fun bodyStyle() = TextStyle(
    fontFamily = NotoFontLookup.NOTO_SANS,
    fontWeight = FontWeight.SemiBold,
    fontSize = 24,
)

/** Connection check: a heading and a plain sentence — enough to tell "printed", "blank",
 *  "garbled" and "black" apart. */
internal fun wizardConnectionTestReceipt(@Suppress("UNUSED_PARAMETER") profile: PrinterProfile): Receipt {
    val body = bodyStyle()
    return receipt(body) {
        space(4)
        text("PRINTER TEST", style = body.copy(fontSize = TextSize.Large, fontWeight = FontWeight.Bold), align = TextAlign.Center)
        space(8)
        text("If you can read this, the printer is connected.", style = body, align = TextAlign.Center)
        space(8)
        rule(style = RuleStyle.Dashed)
        space(8)
    }
}

/** Height of the smoothness test's graphic, in dots — about 10 cm of paper, long enough that a
 *  printer which stutters, pauses between bands or drops rows can't hide it. */
private const val SMOOTHNESS_IMAGE_HEIGHT_DOTS = 800

/** Rows per pattern block in the smoothness graphic. */
private const val SMOOTHNESS_BLOCK_ROWS = 100

/** Lines in the text-mode version of the smoothness test. */
private const val SMOOTHNESS_TEXT_LINES = 40

private const val PIXEL_BLACK = 0xFF000000.toInt()
private const val PIXEL_WHITE = 0xFFFFFFFF.toInt()

/**
 * Smoothness check. In [PrintMode.Graphic] this is one large, tall image: four different patterns
 * stacked (checkerboard, fine horizontal lines, vertical stripes, diagonal stripes) inside a
 * border, so a feed that stutters, a band that drops, or a raster command the printer half
 * understands each leave a visible mark. Every pattern is about 50% ink — nothing is a solid black
 * slab, which is hard on the print head and on cheap printers' power supplies.
 *
 * In [PrintMode.Text] there is no image to send, so it is a long run of numbered text lines
 * instead — the equivalent for a feed that has to keep up with the printer's own font.
 */
internal fun wizardSmoothnessTestReceipt(profile: PrinterProfile, mode: PrintMode): Receipt {
    val body = bodyStyle()
    return receipt(body) {
        space(4)
        text("SMOOTHNESS TEST", style = body.copy(fontSize = TextSize.Large, fontWeight = FontWeight.Bold), align = TextAlign.Center)
        space(6)
        if (mode == PrintMode.Graphic) {
            image(smoothnessPattern(profile.dotWidth), align = TextAlign.Center)
        } else {
            repeat(SMOOTHNESS_TEXT_LINES) { index ->
                text("Line ${index + 1} - the feed should not pause", style = body)
                space(2)
            }
        }
        space(6)
        text("END OF TEST", style = body.copy(fontWeight = FontWeight.Bold), align = TextAlign.Center)
        space(4)
    }
}

/** The tall test pattern, a little narrower than the paper so the border is never clipped. */
private fun smoothnessPattern(dotWidth: Int): RasterImage {
    val width = ((dotWidth - 16) / 8 * 8).coerceAtLeast(64)
    val height = SMOOTHNESS_IMAGE_HEIGHT_DOTS
    val pixels = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        val border = x < 4 || y < 4 || x >= width - 4 || y >= height - 4
        val dark = when ((y / SMOOTHNESS_BLOCK_ROWS) % 4) {
            0 -> ((x / 8) + (y / 8)) % 2 == 0
            1 -> y % 8 < 2
            2 -> (x / 8) % 2 == 0
            else -> ((x + y) / 8) % 2 == 0
        }
        if (border || dark) PIXEL_BLACK else PIXEL_WHITE
    }
    return RasterImage(width, height, pixels)
}

/** Width check: LEFT and RIGHT markers pinned to the two edges of the configured width, with
 *  dashed rules above and below spanning it. */
internal fun wizardWidthTestReceipt(@Suppress("UNUSED_PARAMETER") profile: PrinterProfile): Receipt {
    val body = bodyStyle()
    return receipt(body) {
        space(4)
        rule(style = RuleStyle.Dashed)
        space(6)
        line { left("|LEFT", style = body); right("RIGHT|", style = body) }
        space(6)
        rule(style = RuleStyle.Dashed)
        space(6)
        text("Both markers should be visible", style = body, align = TextAlign.Center)
        space(8)
    }
}

/** Cut check: ends on a known sentence, so "the cut sliced through it" and "too much blank paper
 *  after it" are both easy to see. */
internal fun wizardCutFeedTestReceipt(@Suppress("UNUSED_PARAMETER") profile: PrinterProfile): Receipt {
    val body = bodyStyle()
    return receipt(body) {
        space(4)
        text("CUT TEST", style = body.copy(fontSize = TextSize.Large, fontWeight = FontWeight.Bold), align = TextAlign.Center)
        space(6)
        text("The last line below must stay readable after the cut.", style = body, align = TextAlign.Center)
        space(6)
        text("END OF TEST", style = body.copy(fontWeight = FontWeight.Bold), align = TextAlign.Center)
    }
}
