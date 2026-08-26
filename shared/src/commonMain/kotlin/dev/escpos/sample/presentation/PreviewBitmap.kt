package dev.escpos.sample.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Unpacks [rows] — [dev.escpos.render.ReceiptRenderer.renderPreviewRows]'s packed 1bpp rows,
 * MSB-first, a set bit meaning a black dot (see [dev.escpos.core.threshold]'s KDoc) — into an
 * [ImageBitmap] for [SampleAppUiState.previewBitmap]. The domain layer's `RenderPreviewUseCase`
 * hands back raw rows rather than an `ImageBitmap` itself (no UI-framework dependency there), so
 * this presentation-layer mapper is the one place that conversion happens.
 *
 * There is no portable "write a raw ARGB pixel array into an `ImageBitmap`" API in common Compose
 * (Android/Skia back it differently), so this draws instead: white background, then one black
 * [androidx.compose.ui.graphics.drawscope.DrawScope.drawRect] per horizontal run of set bits — the
 * same [CanvasDrawScope] technique [dev.escpos.render.ReceiptRenderer] itself uses to build a
 * bitmap in common code, and far fewer draw calls than one rect per dot for typical text/QR
 * content.
 */
fun rowsToBitmap(dotWidth: Int, rows: List<ByteArray>): ImageBitmap {
    val height = maxOf(rows.size, 1)
    val bitmap = ImageBitmap(dotWidth, height, ImageBitmapConfig.Argb8888)
    CanvasDrawScope().draw(
        density = Density(1f, 1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(bitmap),
        size = Size(dotWidth.toFloat(), height.toFloat()),
    ) {
        drawRect(Color.White, size = size)
        rows.forEachIndexed { y, row ->
            var x = 0
            while (x < dotWidth) {
                if (bitAt(row, x) == 1) {
                    val runStart = x
                    while (x < dotWidth && bitAt(row, x) == 1) x++
                    drawRect(
                        Color.Black,
                        topLeft = Offset(runStart.toFloat(), y.toFloat()),
                        size = Size((x - runStart).toFloat(), 1f),
                    )
                } else {
                    x++
                }
            }
        }
    }
    return bitmap
}

/** The dot at [x] within [row] — MSB-first, matching [dev.escpos.core.threshold]'s packing. */
private fun bitAt(row: ByteArray, x: Int): Int = (row[x / 8].toInt() shr (7 - x % 8)) and 1
