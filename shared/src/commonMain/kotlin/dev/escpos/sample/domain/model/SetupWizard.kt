package dev.escpos.sample.domain.model

import dev.escpos.core.CutMode
import dev.escpos.core.ESC_ASTERISK_BAND_ROWS
import dev.escpos.core.RasterMode
import dev.escpos.render.DEFAULT_FEED_DOTS_AFTER_CONTENT

/** The two halves of the setup wizard: first prove the printer can be reached, then tune what
 *  comes out of it. */
enum class WizardPhase(val label: String) {
    Connection("Connection"),
    PaperOutput("Paper output"),
}

/**
 * The wizard's pages, in the order they are visited. [ConnectionCheck] only proves the printer can
 * be reached; [Readability] then prints a large graphic to check it comes out smoothly, trying the
 * print methods in priority order (see [ReadabilityCandidate]).
 */
enum class WizardStep(val phase: WizardPhase, val title: String) {
    Transport(WizardPhase.Connection, "How is the printer connected?"),
    Device(WizardPhase.Connection, "Choose the printer"),
    ConnectionCheck(WizardPhase.Connection, "Check the connection"),
    Readability(WizardPhase.PaperOutput, "Print quality and smoothness"),
    Width(WizardPhase.PaperOutput, "Paper width"),
    CutFeed(WizardPhase.PaperOutput, "Cut and feed"),
    Done(WizardPhase.PaperOutput, "All set"),
}

/**
 * What the user can tell the wizard about a test print. The printer never answers back (there is
 * no status reply to rely on), so these taps are the only sensor the wizard has — each one maps to
 * exactly one decision in [SetupTuning].
 */
enum class WizardAnswer(val step: WizardStep, val label: String) {
    ConnReadable(WizardStep.ConnectionCheck, "I can read the printed text"),
    ConnBadOutput(WizardStep.ConnectionCheck, "Paper moved, but it is blank, garbled or black"),
    ConnNothing(WizardStep.ConnectionCheck, "Nothing happened at all"),
    ConnAttention(WizardStep.ConnectionCheck, "It beeped or a light is blinking"),

    ReadClear(WizardStep.Readability, "Smooth and clear — steady feed, no gaps or lines"),
    ReadBad(WizardStep.Readability, "Not good — it stuttered, has gaps or lines, or is garbled"),
    ReadNothing(WizardStep.Readability, "Nothing came out at all"),

    WidthGood(WizardStep.Width, "Both LEFT and RIGHT are fully visible"),
    WidthCutOff(WizardStep.Width, "RIGHT is cut off or missing"),
    WidthTooNarrow(WizardStep.Width, "Printing stops well short of the right edge"),

    CutGood(WizardStep.CutFeed, "Last line is readable and there is little blank paper"),
    CutThroughContent(WizardStep.CutFeed, "The cut sliced through the last line"),
    CutTooMuchPaper(WizardStep.CutFeed, "Too much blank paper before the cut"),
    CutDidNotCut(WizardStep.CutFeed, "It did not cut at all"),
    ;

    companion object {
        fun forStep(step: WizardStep): List<WizardAnswer> = entries.filter { it.step == step }
    }
}

/** The setting values the wizard is allowed to change — a snapshot so each change can be undone. */
data class TuningFields(
    val rasterMode: RasterMode,
    val bandHeight: String,
    val printMode: PrintMode,
    val dotWidth: String,
    val feedDotsAfterContent: String,
    val cut: CutMode,
)

/**
 * Print methods in priority order — the order the wizard tries them while checking that a large
 * print comes out smoothly. The faster graphic method first, the widely compatible one second, and
 * the printer's own text font last (it works even on printers with no graphics support).
 */
enum class ReadabilityCandidate(val label: String, val printMode: PrintMode, val rasterMode: RasterMode?) {
    GsV0Adaptive("Graphic mode, GS v 0 (content-adaptive)", PrintMode.Graphic, RasterMode.GsV0ContentAdaptive),
    EscAsterisk("Graphic mode, ESC * raster", PrintMode.Graphic, RasterMode.EscAsterisk),
    Text("Text mode (the printer's own font)", PrintMode.Text, null),
}

/**
 * What one answer changed: the new setting values, a short description for the summary (null when
 * nothing changed), what to tell the user next, and whether this setting has run out of options —
 * at which point the wizard points the user at the full settings form instead of looping.
 */
class TuningOutcome(
    val fields: TuningFields,
    val change: String?,
    val message: String,
    val exhausted: Boolean = false,
    /** The wizard should move on to the next step now instead of asking for another test print. */
    val proceed: Boolean = false,
)

/**
 * The pure decision logic behind the wizard: given the current settings and what the user saw,
 * what to try next. Kept free of UI and state so the order of attempts — which only real printers
 * can tell us is right — lives in one place and can be tuned without touching the flow.
 */
object SetupTuning {
    const val WIDTH_58MM = 384
    const val WIDTH_80MM = 576
    const val FEED_STEP_DOTS = 32
    private const val MAX_FEED_DOTS = 480

    /** Which wizard method the current settings amount to. Plain `GS v 0`, which the full form
     *  offers but the wizard never picks, counts as the adaptive variant — same command family. */
    fun currentCandidate(fields: TuningFields): ReadabilityCandidate =
        if (fields.printMode == PrintMode.Text) {
            ReadabilityCandidate.Text
        } else if (fields.rasterMode == RasterMode.EscAsterisk) {
            ReadabilityCandidate.EscAsterisk
        } else {
            ReadabilityCandidate.GsV0Adaptive
        }

    private fun withCandidate(current: TuningFields, candidate: ReadabilityCandidate): TuningFields =
        current.copy(
            printMode = candidate.printMode,
            rasterMode = candidate.rasterMode ?: current.rasterMode,
            // The band height only matters to ESC *; the content-adaptive mode ignores it but
            // PrinterProfile still needs a positive number, so it keeps the ESC * value.
            bandHeight = if (candidate.rasterMode == null) current.bandHeight else ESC_ASTERISK_BAND_ROWS.toString(),
        )

    /**
     * Begins the smoothness check on the highest-priority method. [currentFailed] is true when the
     * settings already in use just produced bad output (the connection test came out
     * blank/garbled), so that method is skipped instead of being tried again.
     */
    fun startReadability(current: TuningFields, currentFailed: Boolean): TuningOutcome {
        val now = currentCandidate(current)
        val first = ReadabilityCandidate.entries.first { !(currentFailed && it == now) }
        return TuningOutcome(
            fields = withCandidate(current, first),
            change = if (first != now) "Print method: ${first.label}" else null,
            message = "Starting with ${first.label}. Print the large test and watch the paper — it " +
                "should feed steadily. Then tell me how it looks.",
        )
    }

    /** Moves to the next-priority print method not in [tried] (and different from the current one). */
    fun nextReadability(current: TuningFields, tried: Set<ReadabilityCandidate>): TuningOutcome {
        val now = currentCandidate(current)
        val next = ReadabilityCandidate.entries.firstOrNull { it !in tried && it != now }
            ?: return TuningOutcome(
                fields = current,
                change = null,
                message = "Every print method has been tried. Check the paper roll and cable — over " +
                    "Bluetooth, also try moving closer — then open the full settings form to " +
                    "adjust things by hand.",
                exhausted = true,
            )
        // Text mode is the fallback, not another experiment: both graphic methods have already
        // failed the smoothness test, and text mode prints through the printer's own font, so
        // there is nothing graphic left to check. Switch to it and carry on to the next step.
        if (next == ReadabilityCandidate.Text) {
            return TuningOutcome(
                fields = withCandidate(current, next),
                change = "Print method: ${next.label}",
                message = "Graphic printing did not come out smoothly, so I switched to text mode " +
                    "(the printer's own font) and moved on.",
                proceed = true,
            )
        }
        return TuningOutcome(
            fields = withCandidate(current, next),
            change = "Print method: ${next.label}",
            message = "Switched to ${next.label}. Print the large test again and tell me how it looks.",
        )
    }

    fun adjustWidth(current: TuningFields, answer: WizardAnswer): TuningOutcome {
        val width = current.dotWidth.toIntOrNull() ?: WIDTH_58MM
        return when (answer) {
            WizardAnswer.WidthCutOff ->
                if (width <= WIDTH_58MM) {
                    TuningOutcome(
                        current, null,
                        "Already at the narrowest standard width (58 mm). If the edge is still cut off, " +
                            "enter a smaller dot width in the full settings form.",
                        exhausted = true,
                    )
                } else {
                    TuningOutcome(
                        current.copy(dotWidth = WIDTH_58MM.toString()),
                        "Paper width: 58 mm ($WIDTH_58MM dots)",
                        "Switched to 58 mm paper. Print the test again.",
                    )
                }
            WizardAnswer.WidthTooNarrow ->
                if (width >= WIDTH_80MM) {
                    TuningOutcome(
                        current, null,
                        "Already at the widest standard width (80 mm). If it still stops short, " +
                            "enter a larger dot width in the full settings form.",
                        exhausted = true,
                    )
                } else {
                    TuningOutcome(
                        current.copy(dotWidth = WIDTH_80MM.toString()),
                        "Paper width: 80 mm ($WIDTH_80MM dots)",
                        "Switched to 80 mm paper. Print the test again.",
                    )
                }
            else -> TuningOutcome(current, null, "", exhausted = false)
        }
    }

    fun adjustFeed(current: TuningFields, answer: WizardAnswer): TuningOutcome {
        val feed = current.feedDotsAfterContent.toIntOrNull() ?: DEFAULT_FEED_DOTS_AFTER_CONTENT
        return when (answer) {
            WizardAnswer.CutThroughContent ->
                if (feed >= MAX_FEED_DOTS) {
                    TuningOutcome(
                        current, null,
                        "The feed is already at its maximum ($MAX_FEED_DOTS dots). Open the full settings " +
                            "form if you need to go further.",
                        exhausted = true,
                    )
                } else {
                    val updated = (feed + FEED_STEP_DOTS).coerceAtMost(MAX_FEED_DOTS)
                    TuningOutcome(
                        current.copy(feedDotsAfterContent = updated.toString()),
                        "Feed before cut: $feed → $updated dots",
                        "Feeding more paper before the cut. Print the test again.",
                    )
                }
            WizardAnswer.CutTooMuchPaper ->
                if (feed <= 0) {
                    TuningOutcome(
                        current, null,
                        "The feed is already zero, so there is nothing left to reduce.",
                        exhausted = true,
                    )
                } else {
                    val updated = (feed - FEED_STEP_DOTS).coerceAtLeast(0)
                    TuningOutcome(
                        current.copy(feedDotsAfterContent = updated.toString()),
                        "Feed before cut: $feed → $updated dots",
                        "Feeding less paper before the cut. Print the test again.",
                    )
                }
            else -> TuningOutcome(current, null, "", exhausted = false)
        }
    }

    /** Cycles to a cut mode that has not been tried yet. */
    fun nextCut(current: TuningFields, tried: Set<CutMode>): TuningOutcome {
        val next = CutMode.entries.firstOrNull { it !in tried && it != current.cut }
            ?: return TuningOutcome(
                current, null,
                "No other cut mode left to try. Your printer may not have a cutter — that is fine, " +
                    "you can carry on.",
                exhausted = true,
            )
        return TuningOutcome(
            current.copy(cut = next),
            "Cut mode: ${current.cut.name} → ${next.name}",
            "Switched the cut mode to ${next.name}. Print the test again.",
        )
    }

    /** What to check when the app could not even send the job, or nothing happened on the printer. */
    fun connectionHint(kind: TransportKind): String = when (kind) {
        TransportKind.Tcp ->
            "Check the printer is on and on the same network as this device, and that the IP address " +
                "and port (usually 9100) are right."
        TransportKind.Bluetooth ->
            "Check the printer is on, paired in the system Bluetooth settings, and that Nearby devices " +
                "permission is granted. Only one device can hold the connection at a time."
        TransportKind.Usb ->
            "Check the cable, and allow USB access when the system asks. Unplug and replug if it " +
                "does not ask."
        TransportKind.Serial ->
            "Check the cable and that the selected COM port and baud rate match the printer's settings."
        TransportKind.Spooler ->
            "Check the queue is the right printer and that its driver passes RAW bytes through " +
                "(\"Generic / Text Only\" or a vendor ESC/POS driver)."
    }

    const val ATTENTION_HINT =
        "A beep or blinking light usually means the cover is open or the paper has run out. Close the " +
            "cover, check the roll, then print the test again."
}
