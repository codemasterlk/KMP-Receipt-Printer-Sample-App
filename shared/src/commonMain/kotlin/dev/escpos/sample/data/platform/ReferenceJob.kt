package dev.escpos.sample.data.platform

import dev.escpos.sample.generated.resources.Res

/**
 * A diagnostic-only fixture, not a demo feature: the *exact* bytes of a real print job sent to a
 * CK-710 thermal printer over Bluetooth SPP by a third-party POS app that prints smoothly on that
 * hardware — captured from a Bluetooth HCI snoop log and bundled verbatim
 * (`composeResources/files/reference_job_capture.bin`, 39,810 bytes).
 *
 * Exists to answer one question a same-app A/B test cannot: every other comparison varies *this
 * app's own* bytes and sends them through *this app's own* connection. If the identical bytes a
 * printer already handles smoothly from another app *still* stutter when sent through this app's
 * transport plumbing, the difference isn't in the bytes at all — it's in how the socket is held or
 * writes are scheduled.
 *
 * Parses the capture back into the pieces the original app wrote it in — one `ESC @` (init), then
 * one piece per real `GS v 0` raster command (each exactly as wide/tall as its own header
 * declares), then everything left over as one final piece (the capture's tail past the last raster
 * command, whatever exact byte form it takes — not worth a full command grammar just to replay it
 * faithfully) — so a session can feed one transport `write` per piece, the same granularity the
 * original app used, rather than one giant write that would exercise this app's pacing/chunking
 * completely differently than it was built for.
 */
suspend fun loadReferenceJobPieces(): List<ByteArray> {
    val bytes = Res.readBytes("files/reference_job_capture.bin")
    val pieces = mutableListOf<ByteArray>()
    var pos = 0
    while (pos < bytes.size) {
        val remaining = bytes.size - pos
        when {
            remaining >= 2 && bytes[pos] == 0x1B.toByte() && bytes[pos + 1] == 0x40.toByte() -> {
                pieces += bytes.copyOfRange(pos, pos + 2)
                pos += 2
            }
            remaining >= 8 &&
                bytes[pos] == 0x1D.toByte() && bytes[pos + 1] == 0x76.toByte() && bytes[pos + 2] == 0x30.toByte() -> {
                fun u(i: Int) = bytes[pos + i].toInt() and 0xFF
                val bytesPerRow = u(4) + (u(5) shl 8)
                val rows = u(6) + (u(7) shl 8)
                val len = minOf(8 + bytesPerRow * rows, remaining)
                pieces += bytes.copyOfRange(pos, pos + len)
                pos += len
            }
            else -> {
                pieces += bytes.copyOfRange(pos, bytes.size)
                pos = bytes.size
            }
        }
    }
    return pieces
}
