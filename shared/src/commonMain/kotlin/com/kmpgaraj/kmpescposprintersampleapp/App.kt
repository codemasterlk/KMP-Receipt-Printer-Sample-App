package com.kmpgaraj.kmpescposprintersampleapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 576 dots for 80mm paper, 384 for 58mm — see [dev.escpos.core.PrinterProfile]'s KDoc. */
private enum class PaperWidth(val label: String, val dots: Int) {
    Mm80("80 mm (576 dots)", 576),
    Mm58("58 mm (384 dots)", 384),
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        val scope = rememberCoroutineScope()

        // Built once in composition (Android needs a Context for this), then
        // constructed off the UI thread below — see PlatformTextMeasurer.kt.
        val textMeasurerFactory = rememberEscPosTextMeasurerFactory()
        var textMeasurer by remember { mutableStateOf<TextMeasurer?>(null) }
        LaunchedEffect(Unit) {
            textMeasurer = withContext(Dispatchers.Default) { textMeasurerFactory() }
        }

        var host by remember { mutableStateOf("") }
        var paperWidth by remember { mutableStateOf(PaperWidth.Mm80) }
        var isPrinting by remember { mutableStateOf(false) }
        val log = remember { mutableStateListOf<String>() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .padding(16.dp),
        ) {
            Text("TerminalPrinterKMP sample", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Renders a small receipt and sends it to a networked ESC/POS printer over TCP (port 9100).",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Printer IP address") },
                placeholder = { Text("192.168.1.50") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            Text("Paper width", style = MaterialTheme.typography.labelLarge)
            Row {
                PaperWidth.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp),
                    ) {
                        RadioButton(
                            selected = paperWidth == option,
                            onClick = { paperWidth = option },
                        )
                        Text(option.label)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val measurer = textMeasurer
                    if (measurer == null) return@Button
                    if (host.isBlank()) {
                        log.add("Enter a printer IP address first.")
                        return@Button
                    }
                    isPrinting = true
                    scope.launch {
                        try {
                            printSampleReceipt(
                                host = host.trim(),
                                dotWidth = paperWidth.dots,
                                textMeasurer = measurer,
                                onLog = { line -> log.add(line) },
                            )
                        } catch (t: Throwable) {
                            log.add("Failed: ${t.message ?: t::class.simpleName}")
                        } finally {
                            isPrinting = false
                        }
                    }
                },
                enabled = textMeasurer != null && !isPrinting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isPrinting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (textMeasurer == null) "Loading fonts…" else "Print sample receipt")
            }

            Spacer(Modifier.height(16.dp))
            Text("Log", style = MaterialTheme.typography.labelLarge)
            HorizontalDivider()

            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(log) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
