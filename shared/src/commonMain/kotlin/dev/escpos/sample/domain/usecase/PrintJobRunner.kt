package dev.escpos.sample.domain.usecase

import dev.escpos.core.ESC_ASTERISK_BAND_ROWS
import dev.escpos.sample.domain.model.TransportSelection
import dev.escpos.sample.domain.repository.PrinterRepository
import dev.escpos.session.PacingPolicy
import dev.escpos.session.PrintResult
import dev.escpos.session.PrintSession
import dev.escpos.session.PrintStats
import dev.escpos.session.RetryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The outcome of sending one print job — the presentation layer turns either branch into both
 *  UI status text and a print-log entry. */
sealed interface SendResult {
    data class Success(val message: String) : SendResult
    data class Failure(val message: String) : SendResult
}

/**
 * The part of sending a job that doesn't care *what* the job is: build the transport, connect (if
 * needed), write every chunk of the job through a fresh [PrintSession], and report the outcome.
 * Shared by [SendReceiptUseCase] (a job rendered from a receipt) and
 * [ReplayReferenceJobUseCase] (a job that isn't built from a receipt at all).
 *
 * [PrintSession.print] is mode-agnostic — it just writes whatever bytes the sequence yields — so
 * the caller's only decision is which pipeline builds the sequence, via [run]'s `buildJob`.
 */
internal class PrintJobRunner(private val repository: PrinterRepository) {

    /**
     * @param buildJob builds the job *after* the transport exists — a missing device fails fast,
     *   before any rendering work — and inside the same `try`, so a render failure (a missing
     *   font, a layout error) is reported as a failed print instead of escaping.
     * @param chunkHeightDots the dot-row height each chunk represents, for pacing — a job that
     *   isn't made of bands (a replayed capture) leaves it at the default.
     * @param modeLabel appended in brackets to the result, so the log shows what was actually sent
     *   (a stale raster mode silently sending `ESC *` instead of `GS v 0` otherwise looks
     *   identical to a real run).
     * @param describeExtra an extra summary to append, evaluated after the job has been consumed.
     */
    suspend fun run(
        selection: TransportSelection,
        label: String,
        chunkHeightDots: Int = DEFAULT_CHUNK_HEIGHT_DOTS,
        modeLabel: String? = null,
        describeExtra: () -> String? = { null },
        buildJob: suspend () -> Sequence<ByteArray>,
    ): SendResult {
        val transport = repository.buildTransport(selection)
            ?: return SendResult.Failure("Select a device (or enter a TCP host/port, or a serial baud rate) first")

        return try {
            val job = buildJob()
            val session = PrintSession(
                transport = transport,
                // PacingPolicy.None, not TimedPacing: with chunkHeightDots = the band height (24
                // for ESC * mode), TimedPacing sleeps after *every single band* — for a normal
                // receipt that's dozens of stop/resume cycles, which showed up on real hardware
                // as visible stepping/vibration instead of a continuous paper roll. Every
                // transport's own backpressure (USB bulk-transfer blocking, TCP socket buffering,
                // and genuinely rate-limited Bluetooth SPP writes) already throttles writes to
                // what the print head can consume, so this session never needs to know which
                // transport it's talking to or tune anything per printer.
                pacing = PacingPolicy.None,
                chunkHeightDots = chunkHeightDots,
                retryPolicy = RetryPolicy(),
            )
            // Real socket/RFCOMM/USB I/O — must not run on the caller's dispatcher. Presentation
            // launches jobs from a rememberCoroutineScope(), whose default dispatcher is Main, and
            // Android's StrictMode kills a network syscall made there with
            // NetworkOnMainThreadException.
            var stats: PrintStats? = null
            val result = withContext(Dispatchers.IO) { session.print(job) { stats = it } }

            val suffix = buildString {
                modeLabel?.let { append(" [$it]") }
                stats?.let { append(' ').append(it.describe()) }
                describeExtra()?.let { append(' ').append(it) }
            }
            when (result) {
                PrintResult.Success -> SendResult.Success("$label sent successfully.$suffix")
                is PrintResult.Failure -> SendResult.Failure(
                    "Failed at chunk ${result.chunkIndex} after ${result.attempts} attempt(s): " +
                        describeError(result.cause) + suffix,
                )
            }
        } catch (e: Exception) {
            SendResult.Failure("Print failed: ${describeError(e)}")
        } finally {
            withContext(Dispatchers.IO) { runCatching { transport.close() } }
        }
    }

    private companion object {
        /** Matches [PrintSession]'s own default chunk height (`ESC *`'s fixed strip height). */
        const val DEFAULT_CHUNK_HEIGHT_DOTS = ESC_ASTERISK_BAND_ROWS
    }
}

/** [Throwable.message] plus the underlying [Throwable.cause]'s message where one exists and says
 *  something new — e.g. `escpos-transport-tcp`'s `TransportIOException` wraps every connect/write
 *  failure in one generic message ("failed to connect to host:port"); the OS-level reason that's
 *  actually diagnostic ("Connection refused", "No route to host") only lives in
 *  [Throwable.cause]. Showing `e.message` alone silently discards it. Shared by every use case in
 *  this package. */
internal fun describeError(e: Throwable): String {
    val own = e.message ?: e.toString()
    val causeMessage = e.cause?.message
    return if (causeMessage != null && causeMessage != own) "$own ($causeMessage)" else own
}

/**
 * One-line, same-shape-every-time summary of [PrintStats] — appended to a job's result detail so
 * it can sit next to a decoded Bluetooth HCI capture of another app's own job (payload bytes,
 * write count, total time) without needing a capture of *this* app's job just to get the same
 * numbers.
 */
private fun PrintStats.describe(): String {
    val kbTenths = kotlin.math.round(totalBytes / 102.4).toLong() // *10 for one decimal, /1024 for KB
    val avgChunkBytes = if (chunkCount > 0) totalBytes / chunkCount else 0
    return "payload=${kbTenths / 10}.${kbTenths % 10}KB writes=$chunkCount avgChunk=${avgChunkBytes}B " +
        "total=${totalElapsed.inWholeMilliseconds}ms maxGap=${maxChunkGap.inWholeMilliseconds}ms"
}
