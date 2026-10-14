package dev.escpos.sample.domain.usecase

import dev.escpos.core.CutMode
import dev.escpos.core.EscPos
import dev.escpos.render.DEFAULT_FEED_DOTS_AFTER_CONTENT
import dev.escpos.sample.domain.model.TransportSelection
import dev.escpos.sample.domain.repository.PrinterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Sends the bare minimum a job can be — [EscPos.INIT], one [EscPos.feed], one [EscPos.cut] — with
 * no receipt content, no raster bands, no line-spacing commands at all. A diagnostic tool, not a
 * customer-facing action: isolates the trailing feed+cut sequence from every other variable a real
 * receipt's content could introduce (raster data volume, slice/band count, line-spacing).
 *
 * Confirmed on real Bluetooth hardware that a bare 3-command job alone didn't cut, even though a
 * real text-mode job — `INIT`, several lines of actual content, then the *same* trailing feed+cut
 * bytes — cut fine over the same link to the same printer. The one thing a real job naturally has
 * that this bare one doesn't is elapsed wall-clock time between `connect()` and the trailing cut:
 * composing several lines of content takes real CPU time this job skips straight past. So this
 * bypasses [PrintJobRunner]/`PrintSession` deliberately, calling the transport directly, to insert
 * [SETTLE_AFTER_CONNECT_MILLIS] right after `connect()` and before anything is written, and
 * [SETTLE_AFTER_WRITE_MILLIS] after the last write and before `close()` (cutter-blade actuation
 * time, which neither write pacing nor close's own estimate models — both only account for
 * paper-feed speed). Both values are experiment-only, not derived from anything measured; the
 * first was confirmed on a real CK-710 to fix its cut.
 */
class FeedAndCutUseCase(private val repository: PrinterRepository) {
    suspend operator fun invoke(
        selection: TransportSelection,
        feedDotsAfterContent: String,
        cut: CutMode,
    ): SendResult {
        val transport = repository.buildTransport(selection)
            ?: return SendResult.Failure("Select a device (or enter a TCP host/port, or a serial baud rate) first")
        val dots = feedDotsAfterContent.toIntOrNull() ?: DEFAULT_FEED_DOTS_AFTER_CONTENT

        return try {
            withContext(Dispatchers.IO) {
                transport.connect()
                delay(SETTLE_AFTER_CONNECT_MILLIS)
                transport.write(EscPos.INIT)
                transport.write(EscPos.feed(dots))
                transport.write(EscPos.cut(cut))
                transport.flush()
                delay(SETTLE_AFTER_WRITE_MILLIS)
            }
            SendResult.Success("$LABEL sent successfully.")
        } catch (e: Exception) {
            SendResult.Failure("$LABEL failed: ${describeError(e)}")
        } finally {
            withContext(Dispatchers.IO) { runCatching { transport.close() } }
        }
    }

    companion object {
        /** The label presentation logs this job under. */
        const val LABEL = "Feed + cut (test)"

        private const val SETTLE_AFTER_CONNECT_MILLIS = 2_000L
        private const val SETTLE_AFTER_WRITE_MILLIS = 3_000L
    }
}
