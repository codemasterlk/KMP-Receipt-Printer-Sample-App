package dev.escpos.sample.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.escpos.sample.domain.model.PrintMode
import dev.escpos.sample.domain.model.TransportKind
import dev.escpos.sample.domain.model.WizardAnswer
import dev.escpos.sample.domain.model.WizardStep
import dev.escpos.sample.domain.model.availableTransportKinds

/**
 * The guided printer setup, shown instead of the form/preview layout while [WizardState.open].
 *
 * Two phases. **Connection** (pick how it is connected, pick the device, send a first test) proves
 * the printer can be reached. **Paper output** then tunes what comes out: first a large graphic
 * print to check it comes out smoothly (trying GS v 0 content-adaptive, then ESC *, then text mode,
 * in that priority order), then paper width, and cut and feed — each by printing a test the user can
 * judge by eye and answering what they see. The printer never reports anything back, so those answers are the only feedback; each
 * one changes exactly one setting (see `SetupTuning`) and asks for another test print.
 *
 * Everything here edits the same state as the full settings form, which stays available — the
 * wizard is a guided way into it, not a replacement.
 */
@Composable
internal fun SetupWizard(
    uiState: SampleAppUiState,
    viewModel: SampleAppViewModel,
    onRefreshBluetooth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wizard = uiState.wizard
    val step = wizard.step
    Box(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WizardHeader(step, onCancel = { viewModel.onEvent(SampleAppEvent.WizardCancel) })

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(step.title, style = MaterialTheme.typography.titleLarge)
                    when (step) {
                        WizardStep.Transport -> TransportStep(uiState, viewModel)
                        WizardStep.Device -> DeviceStep(uiState, viewModel, onRefreshBluetooth)
                        WizardStep.ConnectionCheck -> TestStep(
                            uiState, viewModel,
                            instructions = "We will print a short test — it uses very little paper. " +
                                "Then tell me what the printer did.",
                        )
                        WizardStep.Readability -> TestStep(
                            uiState, viewModel,
                            instructions = "This prints a large pattern, about 10 cm tall. Watch the paper " +
                                "while it prints — it should feed steadily without pausing or " +
                                "stuttering — then check the pattern has no gaps, missing lines " +
                                "or garbled areas.",
                        )
                        WizardStep.Width -> TestStep(
                            uiState, viewModel,
                            instructions = "Look for the words LEFT and RIGHT at the two edges of the " +
                                "printout, with a dashed line above and below.",
                        )
                        WizardStep.CutFeed -> TestStep(
                            uiState, viewModel,
                            instructions = "After printing, the printer cuts. Check the last line, " +
                                "END OF TEST, on the piece that was cut off.",
                        )
                        WizardStep.Done -> DoneStep(uiState)
                    }
                }
            }

            wizard.message?.let { message ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            WizardNavigation(uiState, viewModel)
        }
    }
}

@Composable
private fun WizardHeader(step: WizardStep, onCancel: () -> Unit) {
    val index = WizardStep.entries.indexOf(step)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Printer setup wizard", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${step.phase.label} · step ${index + 1} of ${WizardStep.entries.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        LinearProgressIndicator(
            progress = { (index + 1).toFloat() / WizardStep.entries.size },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TransportStep(uiState: SampleAppUiState, viewModel: SampleAppViewModel) {
    val transports = remember { availableTransportKinds() }
    Text(
        "Pick the way this device talks to the printer.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    transports.forEach { kind ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.onEvent(SampleAppEvent.TransportKindChanged(kind)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = uiState.transportKind == kind,
                onClick = { viewModel.onEvent(SampleAppEvent.TransportKindChanged(kind)) },
            )
            Column {
                Text(kind.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    transportDescription(kind),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun transportDescription(kind: TransportKind): String = when (kind) {
    TransportKind.Tcp -> "A network printer reached by IP address."
    TransportKind.Bluetooth -> "A printer already paired in the system Bluetooth settings."
    TransportKind.Usb -> "A printer plugged straight into this device."
    TransportKind.Serial -> "A printer on a COM / serial port."
    TransportKind.Spooler -> "A printer installed in the operating system, sent raw bytes."
}

@Composable
private fun DeviceStep(uiState: SampleAppUiState, viewModel: SampleAppViewModel, onRefreshBluetooth: () -> Unit) {
    // Bluetooth needs a runtime permission before it can list anything — asking here, with the
    // list on screen, is when the prompt makes sense to the user.
    LaunchedEffect(uiState.transportKind) {
        if (uiState.transportKind == TransportKind.Bluetooth) onRefreshBluetooth()
    }
    DeviceSection(uiState, viewModel, onRefreshBluetooth)
    if (!uiState.hasDeviceSelected) {
        Text(
            "Choose a printer to continue.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One test-and-answer page: print a test, look at the paper, tap what you see. The answers only
 * appear after something has printed — there is nothing to judge before that.
 */
@Composable
private fun TestStep(uiState: SampleAppUiState, viewModel: SampleAppViewModel, instructions: String) {
    val wizard = uiState.wizard
    Text(instructions, style = MaterialTheme.typography.bodyMedium)

    Button(
        onClick = { viewModel.onEvent(SampleAppEvent.WizardPrintTest) },
        enabled = !uiState.printing,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            when {
                uiState.printing -> "Printing…"
                wizard.testsPrintedInStep > 0 -> "Print again"
                else -> "Print test"
            },
        )
    }

    if (wizard.testsPrintedInStep > 0) {
        HorizontalDivider()
        Text("What do you see?", style = MaterialTheme.typography.titleSmall)
        WizardAnswer.forStep(wizard.step).forEach { answer ->
            OutlinedButton(
                onClick = { viewModel.onEvent(SampleAppEvent.WizardAnswered(answer)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(answer.label)
            }
        }
    }

    if (wizard.exhausted) {
        OutlinedButton(
            onClick = { viewModel.onEvent(SampleAppEvent.WizardOpenFullForm) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open the full settings form")
        }
    }
    if (wizard.changes.isNotEmpty()) {
        TextButton(onClick = { viewModel.onEvent(SampleAppEvent.WizardUndo) }) {
            Text("Undo last change (${wizard.changes.last().description})")
        }
    }
}

@Composable
private fun DoneStep(uiState: SampleAppUiState) {
    Text(
        "Your printer is set up. These settings are saved, and you can still change any of them " +
            "in the full form.",
        style = MaterialTheme.typography.bodyMedium,
    )
    HorizontalDivider()
    SummaryRow("Connection", "${uiState.transportKind.label} — ${deviceSummary(uiState)}")
    SummaryRow("Paper width", "${uiState.dotWidth} dots")
    SummaryRow("Print method", if (uiState.printMode == PrintMode.Text) "Text mode" else "Graphic · ${uiState.rasterMode.name}")
    SummaryRow("Cut", uiState.cut.name)
    SummaryRow("Feed before cut", "${uiState.feedDotsAfterContent} dots")
    if (uiState.wizard.changes.isNotEmpty()) {
        HorizontalDivider()
        Text("Adjusted during setup", style = MaterialTheme.typography.titleSmall)
        uiState.wizard.changes.forEach { change ->
            Text("• ${change.description}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun deviceSummary(s: SampleAppUiState): String = when (s.transportKind) {
    TransportKind.Tcp -> "${s.host}:${s.port}"
    TransportKind.Bluetooth -> s.selectedBluetoothDevice?.let { it.name ?: it.address } ?: "none selected"
    TransportKind.Usb -> s.selectedUsbDevice?.let { it.productName ?: it.deviceName } ?: "none selected"
    TransportKind.Serial -> s.selectedSerialPort?.let { "${it.systemPortName} @ ${s.serialBaudRate}" } ?: "none selected"
    TransportKind.Spooler -> s.selectedSpoolerPrinter?.name ?: "none selected"
}

@Composable
private fun WizardNavigation(uiState: SampleAppUiState, viewModel: SampleAppViewModel) {
    val step = uiState.wizard.step
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step == WizardStep.Transport) {
            Box {}
        } else {
            OutlinedButton(onClick = { viewModel.onEvent(SampleAppEvent.WizardBack) }) { Text("Back") }
        }
        when (step) {
            WizardStep.Transport -> Button(onClick = { viewModel.onEvent(SampleAppEvent.WizardNext) }) { Text("Next") }
            WizardStep.Device -> Button(
                onClick = { viewModel.onEvent(SampleAppEvent.WizardNext) },
                enabled = uiState.hasDeviceSelected,
            ) { Text("Next") }
            WizardStep.ConnectionCheck, WizardStep.Readability, WizardStep.Width, WizardStep.CutFeed ->
                TextButton(onClick = { viewModel.onEvent(SampleAppEvent.WizardNext) }) { Text("Skip this check") }
            WizardStep.Done -> Button(onClick = { viewModel.onEvent(SampleAppEvent.WizardFinish) }) { Text("Finish") }
        }
    }
}
