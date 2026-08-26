package dev.escpos.sample.domain.usecase

import dev.escpos.core.EscPos
import dev.escpos.core.PrinterStatus
import dev.escpos.sample.domain.model.TransportSelection
import dev.escpos.sample.domain.repository.PrinterRepository
import dev.escpos.session.StatusPoller
import dev.escpos.transport.api.StatusQueryTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [CheckPrinterStatusUseCase]'s result. [Unsupported] is distinct from [Failure]: it means the
 *  selected transport was never capable of answering, not that a real check attempt failed. */
sealed interface StatusCheckResult {
    data class Success(val message: String) : StatusCheckResult
    data class Unsupported(val message: String) : StatusCheckResult
    data class Failure(val message: String) : StatusCheckResult
}

/**
 * FR-7.5 — connects to whichever transport/device [selection] identifies and polls it once for
 * paper-out/cover-open, via [StatusPoller] one-shot queries decoded with
 * [PrinterStatus.fromOfflineAndPaperBytes]. Only [StatusQueryTransport]s answer this — TCP and
 * Bluetooth SPP (see each transport's KDoc) — so any other selection comes back
 * [StatusCheckResult.Unsupported] rather than pretending to check.
 */
class CheckPrinterStatusUseCase(private val repository: PrinterRepository) {
    suspend operator fun invoke(selection: TransportSelection): StatusCheckResult {
        val transport = repository.buildTransport(selection)
            ?: return StatusCheckResult.Failure("Select a device (or enter a TCP host/port, or a serial baud rate) first")

        if (transport !is StatusQueryTransport) {
            withContext(Dispatchers.IO) { runCatching { transport.close() } }
            return StatusCheckResult.Unsupported(
                "${selection.transportKind.name} does not support status queries (FR-7.5) — connect and print instead.",
            )
        }

        return try {
            // Real socket/RFCOMM I/O — see SendReceiptUseCase's KDoc for why this must not run
            // on the caller's (Main) dispatcher.
            val (offline, paper) = withContext(Dispatchers.IO) {
                transport.connect()
                val offlineBytes = StatusPoller(transport, EscPos.RealtimeStatus.OFFLINE).queryOnce()
                val paperBytes = StatusPoller(transport, EscPos.RealtimeStatus.PAPER).queryOnce()
                offlineBytes to paperBytes
            }
            if (offline.isEmpty() || paper.isEmpty()) {
                StatusCheckResult.Failure("Printer did not answer the status query in time — it may not implement DLE EOT.")
            } else {
                val status = PrinterStatus.fromOfflineAndPaperBytes(offline[0], paper[0])
                StatusCheckResult.Success(
                    "Cover open: ${status.coverOpen} · Paper near end: ${status.paperNearEnd} · Paper out: ${status.paperOut}",
                )
            }
        } catch (e: Exception) {
            StatusCheckResult.Failure("Status check failed: ${describeError(e)}")
        } finally {
            withContext(Dispatchers.IO) { runCatching { transport.close() } }
        }
    }
}
