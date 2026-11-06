package dev.escpos.sample.presentation

import dev.escpos.core.CutMode
import dev.escpos.sample.domain.model.ReadabilityCandidate
import dev.escpos.sample.domain.model.TuningFields
import dev.escpos.sample.domain.model.WizardStep

/** One setting change the wizard made — what it did, and the values it replaced so it can be
 *  undone. */
data class WizardChange(val description: String, val before: TuningFields)

/**
 * Where the setup wizard is, plus what it has tried so far. Lives inside [SampleAppUiState] so the
 * wizard edits the very same fields the full form does — the two views never disagree.
 */
data class WizardState(
    val open: Boolean = false,
    val step: WizardStep = WizardStep.Transport,

    /** Test prints sent on the current step. The answer buttons only appear once this is above
     *  zero — there is nothing to judge before something has printed. */
    val testsPrintedInStep: Int = 0,

    /** What the wizard wants to tell the user right now: a failure hint, or what it just changed. */
    val message: String? = null,

    /** The current setting has run out of options; the wizard offers the full form instead. */
    val exhausted: Boolean = false,

    val triedReadability: Set<ReadabilityCandidate> = emptySet(),
    val triedCuts: Set<CutMode> = emptySet(),

    /** Newest last. Shown in the final summary; the last entry is what Undo reverts. */
    val changes: List<WizardChange> = emptyList(),
)
