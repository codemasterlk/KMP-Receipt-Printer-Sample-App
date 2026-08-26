package dev.escpos.sample.domain.usecase

import dev.escpos.sample.domain.model.ProfileSettings
import dev.escpos.sample.domain.receipt.customInvoiceReceipt
import dev.escpos.sample.domain.repository.PrinterRepository

/** [RenderPreviewUseCase]'s result. [Success] carries the printer's dot width plus the packed
 *  1bpp rows [dev.escpos.render.ReceiptRenderer.renderPreviewRows] produced — raw domain data, not
 *  an `ImageBitmap`: turning that into an on-screen bitmap is a presentation-layer concern (see
 *  `dev.escpos.sample.presentation.rowsToBitmap`), and the domain layer has no UI-framework
 *  dependency to do that itself. [Failure] carries the message presentation shows as-is. */
sealed interface PreviewResult {
    data class Success(val dotWidth: Int, val rows: List<ByteArray>) : PreviewResult
    data class Failure(val message: String) : PreviewResult
}

/**
 * Renders [dev.escpos.sample.domain.receipt.customInvoiceReceipt] for the given profile, item
 * count, and font, and returns the result as raw thresholded rows — the preview panel's live
 * "what would this actually print" view, always in sync with what [SendReceiptUseCase] would
 * actually send, since both build the receipt the exact same way.
 */
class RenderPreviewUseCase(private val repository: PrinterRepository) {
    suspend operator fun invoke(
        profileSettings: ProfileSettings,
        feedDotsAfterContent: String,
        itemCount: Int,
        contentFontId: String,
    ): PreviewResult {
        val profile = repository.buildProfile(profileSettings)
            ?: return PreviewResult.Failure("Fix dot width / band height first — both must be whole numbers")

        return try {
            val receipt = customInvoiceReceipt(profile = profile, itemCount = itemCount, fontFamilyId = contentFontId)
            val rows = repository.createRenderer(profile, feedDotsAfterContent).renderPreviewRows(receipt)
            PreviewResult.Success(dotWidth = profile.dotWidth, rows = rows)
        } catch (e: Exception) {
            PreviewResult.Failure("Preview failed: ${e.message ?: e.toString()}")
        }
    }
}
