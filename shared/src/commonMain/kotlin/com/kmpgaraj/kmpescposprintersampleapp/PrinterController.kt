package com.kmpgaraj.kmpescposprintersampleapp

import androidx.compose.ui.text.TextMeasurer
import dev.escpos.core.PrinterProfile
import dev.escpos.render.ReceiptRenderer
import dev.escpos.render.RenderConfig
import dev.escpos.session.PrintSession
import dev.escpos.transport.tcp.TcpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders [buildSampleReceipt] and sends it to a printer over TCP
 * (escpos-transport-tcp always uses port 9100).
 *
 * There is only ever one printer profile ("generic") — see the README's
 * discussion of constraint C-6 — so the one physical fact callers must supply
 * is [dotWidth]: 576 dots for 80mm paper, 384 for 58mm. The four advanced
 * overrides (raster mode, band height, cut mode, drawer pin) are left at
 * their defaults here; pass them to [PrinterProfile] if a specific printer
 * needs one of the handful of known deviations.
 *
 * @param host printer IP address on the local network, e.g. "192.168.1.50"
 * @param textMeasurer built via [rememberEscPosTextMeasurerFactory]
 * @param onLog called with a short progress line after each step, on the
 *   calling context — safe to update Compose state from directly.
 */
suspend fun printSampleReceipt(
    host: String,
    dotWidth: Int,
    textMeasurer: TextMeasurer,
    onLog: (String) -> Unit,
) {
    onLog("Building receipt…")
    val receipt = buildSampleReceipt()

    val profile = PrinterProfile(name = "Sample printer", dotWidth = dotWidth)

    onLog("Rendering to ESC/POS bytes ($dotWidth dots wide)…")
    val job = withContext(Dispatchers.Default) {
        ReceiptRenderer(RenderConfig.of(profile), textMeasurer).render(receipt)
    }

    onLog("Connecting to $host:9100…")
    val result = withContext(Dispatchers.IO) {
        val transport = TcpTransport(host = host)
        PrintSession(transport, chunkHeightDots = profile.bandHeight).print(job)
    }

    onLog("Done: $result")
}
