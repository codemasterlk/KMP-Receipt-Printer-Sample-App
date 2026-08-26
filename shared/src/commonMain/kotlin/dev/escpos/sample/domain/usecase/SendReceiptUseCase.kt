package dev.escpos.sample.domain.usecase

import dev.escpos.core.PrinterProfile
import dev.escpos.core.Receipt
import dev.escpos.sample.domain.model.TransportSelection
import dev.escpos.sample.domain.repository.PrinterRepository
import dev.escpos.session.PacingPolicy
import dev.escpos.session.PrintResult
import dev.escpos.session.PrintSession
import dev.escpos.session.RetryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [SendReceiptUseCase]'s result — the presentation layer turns either branch into both UI status
 *  text and a print-log entry. */
sealed interface SendResult {
    data class Success(val message: String) : SendResult
    data class Failure(val message: String) : SendResult
}

/**
 * Sends an already-built [Receipt] — [dev.escpos.sample.domain.receipt.customInvoiceReceipt]'s
 * customer receipt or the library's bundled diagnostic pattern, the caller decides which — over
 * whichever transport/device [selection] identifies. Connects (if needed), renders to ESC/POS
 * bytes, and drives a [PrintSession] with retry; see [PrintSession]'s own KDoc for the
 * pacing/retry/reconnect behavior this wraps.
 *
 * [label] is purely cosmetic — it only shows up in the success/failure message text ("Receipt
 * sent successfully." vs. "Diagnostic receipt sent successfully.").
 */
class SendReceiptUseCase(private val repository: PrinterRepository) {
    suspend operator fun invoke(
        receipt: Receipt,
        profile: PrinterProfile,
        feedDotsAfterContent: String,
        selection: TransportSelection,
        label: String,
    ): SendResult {
        val transport = repository.buildTransport(selection)
            ?: return SendResult.Failure("Select a device (or enter a TCP host/port, or a serial baud rate) first")

        return try {
            val job = repository.createRenderer(profile, feedDotsAfterContent).render(receipt)
            val session = PrintSession(
                transport = transport,
                // PacingPolicy.None, not TimedPacing: with chunkHeightDots = profile.bandHeight
                // (24 for ESC * mode), TimedPacing sleeps after *every single band* — for a
                // normal-length receipt that's dozens of stop/resume cycles, which is exactly
                // what showed up on real hardware as visible stepping/vibration instead of a
                // continuous paper roll. Modern thermal printers carry enough onboard buffer, and
                // the transport's own backpressure (Bluetooth RFCOMM credit flow, USB
                // bulk-transfer blocking, TCP socket buffering) already throttles writes to what
                // the print head can actually consume.
                pacing = PacingPolicy.None,
                chunkHeightDots = profile.bandHeight,
                retryPolicy = RetryPolicy(),
            )
            // Real socket/RFCOMM/USB I/O — must not run on the caller's dispatcher. Presentation
            // dispatches SampleAppEvent.PrintReceipt/PrintDiagnostic via a rememberCoroutineScope(),
            // whose default dispatcher is Main, and Android's StrictMode kills a network syscall
            // made there with NetworkOnMainThreadException.
            when (val result = withContext(Dispatchers.IO) { session.print(job) }) {
                PrintResult.Success -> SendResult.Success("$label sent successfully.")
                is PrintResult.Failure -> SendResult.Failure(
                    "Failed at chunk ${result.chunkIndex} after ${result.attempts} attempt(s): " +
                        describeError(result.cause),
                )
            }
        } catch (e: Exception) {
            SendResult.Failure("Print failed: ${describeError(e)}")
        } finally {
            withContext(Dispatchers.IO) { runCatching { transport.close() } }
        }
    }
}

/** [Throwable.message] plus the underlying [Throwable.cause]'s message where one exists and says
 *  something new — e.g. `escpos-transport-tcp`'s `TransportIOException` wraps every connect/write
 *  failure in one generic message ("failed to connect to host:port"); the OS-level reason that's
 *  actually diagnostic ("Connection refused", "No route to host") only lives in
 *  [Throwable.cause]. Showing `e.message` alone silently discards it. Shared by
 *  [SendReceiptUseCase] and [CheckPrinterStatusUseCase]. */
internal fun describeError(e: Throwable): String {
    val own = e.message ?: e.toString()
    val causeMessage = e.cause?.message
    return if (causeMessage != null && causeMessage != own) "$own ($causeMessage)" else own
}
