package dev.escpos.sample.domain.usecase

import dev.escpos.core.DEFAULT_TEXT_MODE_FEED_DOTS_AFTER_CONTENT
import dev.escpos.core.PrinterProfile
import dev.escpos.core.Receipt
import dev.escpos.core.textPrintJob
import dev.escpos.sample.domain.model.PrintMode
import dev.escpos.sample.domain.model.TransportSelection
import dev.escpos.sample.domain.repository.PrinterRepository

/**
 * Sends an already-built [Receipt] — [dev.escpos.sample.domain.receipt.customInvoiceReceipt]'s
 * customer receipt or one of the library's diagnostic patterns, the caller decides which — over
 * whichever transport/device [TransportSelection] identifies. Which pipeline turns the receipt
 * into bytes depends on [PrintMode]: [PrintMode.Graphic] renders it to a raster image through
 * `ReceiptRenderer`; [PrintMode.Text] formats it with the printer's own font via native text
 * commands. Everything after that — connect, retry, stats, close — is [PrintJobRunner]'s, and
 * identical for both.
 *
 * [label] is purely cosmetic — it only shows up in the success/failure message text ("Receipt
 * sent successfully." vs. "Diagnostic receipt sent successfully.").
 */
class SendReceiptUseCase(private val repository: PrinterRepository) {
    private val runner = PrintJobRunner(repository)

    suspend operator fun invoke(
        receipt: Receipt,
        profile: PrinterProfile,
        feedDotsAfterContent: String,
        selection: TransportSelection,
        label: String,
        mode: PrintMode,
    ): SendResult {
        // Counts each chunk as the session actually consumes it — see JobByteBreakdown's KDoc.
        val breakdown = JobByteBreakdown()
        return runner.run(
            selection = selection,
            label = label,
            chunkHeightDots = profile.bandHeight,
            // Surfaced in the result: a GS v 0 test print that silently sent ESC * instead (the
            // raster mode having reverted to its default) otherwise looks identical to a real
            // ESC * run, making "I tested GS v 0 and saw no difference" indistinguishable from
            // "GS v 0 was never actually sent".
            modeLabel = when (mode) {
                PrintMode.Graphic -> "${profile.rasterMode} bandHeight=${profile.bandHeight}"
                PrintMode.Text -> "Text columns=${profile.columns}"
            },
            describeExtra = { breakdown.describe(profile.dotWidth) },
        ) {
            val rawJob = when (mode) {
                PrintMode.Graphic -> repository.createRenderer(profile, feedDotsAfterContent).render(receipt)
                PrintMode.Text -> textPrintJob(
                    receipt,
                    profile,
                    feedDotsAfterContent.toIntOrNull() ?: DEFAULT_TEXT_MODE_FEED_DOTS_AFTER_CONTENT,
                )
            }
            rawJob.map { chunk -> breakdown.record(chunk); chunk }
        }
    }
}
