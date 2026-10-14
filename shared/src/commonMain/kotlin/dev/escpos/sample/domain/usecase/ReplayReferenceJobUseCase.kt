package dev.escpos.sample.domain.usecase

import dev.escpos.sample.domain.model.TransportSelection
import dev.escpos.sample.domain.repository.PrinterRepository

/**
 * Sends a real, previously captured print job's exact bytes through this app's own transport,
 * bypassing the renderer entirely (see [PrinterRepository.loadReferenceJob]). A diagnostic, not a
 * demo feature: it separates "the bytes are the problem" from "the connection is the problem" —
 * if bytes a printer already handles smoothly from another app *still* misbehave through this
 * app's connection, the difference isn't in the bytes at all.
 *
 * Goes through [PrintJobRunner], so it gets the same stats, connect/close lifecycle, and error
 * handling as every other job; `chunkHeightDots` stays at its default — this job's pieces aren't
 * band-sized, so that figure has nothing meaningful to describe here.
 */
class ReplayReferenceJobUseCase(private val repository: PrinterRepository) {
    private val runner = PrintJobRunner(repository)

    suspend operator fun invoke(selection: TransportSelection): SendResult =
        runner.run(selection = selection, label = LABEL) { repository.loadReferenceJob().asSequence() }

    companion object {
        /** The label presentation logs this job under. */
        const val LABEL = "Reference job replay (diagnostic)"
    }
}
