package dev.escpos.sample.domain.usecase

import dev.escpos.core.DEFAULT_TEXT_MODE_FEED_DOTS_AFTER_CONTENT
import dev.escpos.core.textPreviewLines
import dev.escpos.core.textPrintJob
import dev.escpos.sample.domain.model.PrintMode
import dev.escpos.sample.domain.model.ProfileSettings
import dev.escpos.sample.domain.model.ReceiptOptions
import dev.escpos.sample.domain.receipt.customInvoiceReceipt
import dev.escpos.sample.domain.repository.PrinterRepository

/**
 * [RenderPreviewUseCase]'s result — raw domain data, never a UI type: turning it into something
 * on screen (an `ImageBitmap`, a monospace text block) is a presentation-layer concern, and the
 * domain layer has no UI-framework dependency to do that itself.
 */
sealed interface PreviewResult {
    /** [PrintMode.Graphic]: the printer's dot width plus the packed 1bpp rows
     *  [dev.escpos.render.ReceiptRenderer.renderPreviewRows] produced (see
     *  `dev.escpos.sample.presentation.rowsToBitmap` for turning them into pixels). */
    data class Graphic(val dotWidth: Int, val rows: List<ByteArray>) : PreviewResult

    /** [PrintMode.Text]: no bitmap at all — the plain strings `textPreviewLines` produced, plus
     *  the real job's byte size (what would actually go on the wire, not a line-count guess). */
    data class Text(val lines: List<String>, val jobBytes: Long) : PreviewResult

    /** The message presentation shows as-is. */
    data class Failure(val message: String) : PreviewResult
}

/**
 * Renders [dev.escpos.sample.domain.receipt.customInvoiceReceipt] for the given profile, item
 * count, font, and [ReceiptOptions] — the preview panel's live "what would this actually print"
 * view, always in sync with what [SendReceiptUseCase] would actually send, since both build the
 * receipt the exact same way.
 */
class RenderPreviewUseCase(private val repository: PrinterRepository) {
    suspend operator fun invoke(
        profileSettings: ProfileSettings,
        feedDotsAfterContent: String,
        itemCount: Int,
        contentFontId: String,
        options: ReceiptOptions,
    ): PreviewResult {
        val profile = repository.buildProfile(profileSettings)
            ?: return PreviewResult.Failure("Fix dot width / band height first — both must be whole numbers")

        return try {
            val receipt = customInvoiceReceipt(
                profile = profile,
                itemCount = itemCount,
                fontFamilyId = contentFontId,
                options = options,
            )
            when (options.mode) {
                PrintMode.Graphic -> PreviewResult.Graphic(
                    dotWidth = profile.dotWidth,
                    rows = repository.createRenderer(profile, feedDotsAfterContent).renderPreviewRows(receipt),
                )
                PrintMode.Text -> PreviewResult.Text(
                    lines = textPreviewLines(receipt, profile.columns),
                    jobBytes = textPrintJob(
                        receipt,
                        profile,
                        feedDotsAfterContent.toIntOrNull() ?: DEFAULT_TEXT_MODE_FEED_DOTS_AFTER_CONTENT,
                    ).sumOf { it.size.toLong() },
                )
            }
        } catch (e: Exception) {
            PreviewResult.Failure("Preview failed: ${e.message ?: e.toString()}")
        }
    }
}
