package dev.escpos.sample.domain.usecase

import dev.escpos.core.ESC_ASTERISK_BAND_ROWS

/**
 * Classifies every chunk of a job by its own ESC/POS command header as [record] sees it — raster
 * band (`ESC *` or `GS v 0`) vs. everything else (control commands in graphic mode; the receipt's
 * actual native text/line/table bytes in text mode) — and tallies byte counts, command counts, and
 * per-raster-block row heights. Answers, from *this* app's own job, the same questions a decoded
 * Bluetooth HCI capture answers about someone else's: how much of the payload is raster image data
 * vs. native text, how many raster commands, and how tall each one's block is.
 *
 * Lives here, not inside `PrintSession` (which stays deliberately mode-agnostic) or `escpos-core`
 * (which has no reason to know about per-job aggregate reporting). Fed lazily as the job is
 * actually consumed, so it never materialises the whole job in memory (FR-3.4).
 */
internal class JobByteBreakdown {
    private var rasterBytes = 0L
    private var rasterCommandCount = 0
    private var gsV0CommandCount = 0
    private var escAsteriskCommandCount = 0
    private var minBlockRows = Int.MAX_VALUE
    private var maxBlockRows = 0
    private var otherBytes = 0L // control commands (graphic mode) or native text bytes (text mode)

    fun record(chunk: ByteArray) {
        fun u(i: Int) = chunk[i].toInt() and 0xFF
        val n = chunk.size
        when {
            n >= 8 && u(0) == 0x1D && u(1) == 0x76 && u(2) == 0x30 -> { // GS v 0
                rasterBytes += n
                rasterCommandCount++
                gsV0CommandCount++
                val rows = u(6) + (u(7) shl 8)
                if (rows < minBlockRows) minBlockRows = rows
                if (rows > maxBlockRows) maxBlockRows = rows
            }
            n >= 5 && u(0) == 0x1B && u(1) == 0x2A -> { // ESC * mode 33 — always 24 rows (protocol-fixed)
                rasterBytes += n
                rasterCommandCount++
                escAsteriskCommandCount++
                if (ESC_ASTERISK_BAND_ROWS < minBlockRows) minBlockRows = ESC_ASTERISK_BAND_ROWS
                if (ESC_ASTERISK_BAND_ROWS > maxBlockRows) maxBlockRows = ESC_ASTERISK_BAND_ROWS
            }
            else -> otherBytes += n
        }
    }

    /** [dotWidth] recovers an approximate total raster row count from the raster byte total alone
     *  — no second render pass, no new API on `ReceiptRenderer` needed just for a log line. */
    fun describe(dotWidth: Int): String {
        val bytesPerRow = (dotWidth + 7) / 8
        val approxRasterRows = if (bytesPerRow > 0) rasterBytes / bytesPerRow else 0
        val blockRowsRange = if (rasterCommandCount > 0) "$minBlockRows-$maxBlockRows" else "n/a"
        return "raster=${rasterBytes}B(~${approxRasterRows}rows, ${dotWidth}x$approxRasterRows) " +
            "rasterCmds=$rasterCommandCount(gsv0=$gsV0CommandCount escAst=$escAsteriskCommandCount) " +
            "blockRows=$blockRowsRange otherBytes=${otherBytes}B"
    }
}
