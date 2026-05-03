package vn.anticode.mobile.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.anticode.mobile.data.TerminalManager
import vn.anticode.mobile.data.TerminalResult
import vn.anticode.mobile.data.VpsSpecs
import vn.anticode.mobile.ui.theme.*

data class TerminalEntry(
    val command: String,
    val result: TerminalResult? = null,
    val isRunning: Boolean = false
)

@Composable
fun TerminalPanel(
    entries: List<TerminalEntry>,
    onEntriesChange: (List<TerminalEntry>) -> Unit,
    modifier: Modifier = Modifier,
    onTerminalOutput: ((String, String) -> Unit)? = null
) {
    var inputText by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var specs by remember { mutableStateOf<VpsSpecs?>(null) }
    var specsLoading by remember { mutableStateOf(true) }
    var specsError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Load VPS specs on first open
    LaunchedEffect(Unit) {
        if (specs == null) {
            specsLoading = true
            specsError = null
            val result = TerminalManager.getSpecs()
            if (result.cores == "?") {
                specsError = "Cannot connect to VPS"
            } else {
                specs = result
            }
            specsLoading = false
        }
    }

    // Auto-scroll when entries change
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    Column(modifier = modifier.background(CodeBackground)) {
        // Header with VPS info + connection status
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Cloud, null, tint = if (specs != null) Secondary else Warning, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (specs != null) "VPS Terminal" else if (specsLoading) "Connecting..." else "VPS Offline",
                color = if (specs != null) Secondary else Warning,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            if (specs != null && !specsLoading) {
                Text("${specs!!.cores} CPU • ${specs!!.ram} RAM", color = TextMuted, fontSize = 9.sp)
            } else if (specsLoading) {
                CircularProgressIndicator(Modifier.size(8.dp), strokeWidth = 1.dp, color = TextMuted)
            }
            Spacer(Modifier.width(4.dp))
            if (entries.isNotEmpty()) {
                IconButton(onClick = { onEntriesChange(emptyList()) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.DeleteSweep, "Clear", tint = TextMuted, modifier = Modifier.size(14.dp))
                }
            }
        }

        HorizontalDivider(color = Border, thickness = 0.5.dp)

        // Output
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Welcome + specs card
            if (entries.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Cloud, null, tint = Secondary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Cloud Terminal", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))

                        if (specsError != null) {
                            Text("⚠ $specsError", color = Warning, fontSize = 10.sp)
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = {
                                scope.launch {
                                    specsLoading = true
                                    specsError = null
                                    val result = TerminalManager.getSpecs()
                                    if (result.cores == "?") specsError = "Cannot connect to VPS"
                                    else specs = result
                                    specsLoading = false
                                }
                            }) {
                                Text("Retry", color = Secondary, fontSize = 11.sp)
                            }
                        } else if (!TerminalManager.isConfigured()) {
                            Text("⚠ Set API Key in Settings first", color = Warning, fontSize = 10.sp)
                        } else {
                            Text("Connected to Anticode VPS", color = TextMuted, fontSize = 10.sp)
                        }

                        if (specs != null && !specsLoading) {
                            Spacer(Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    SpecRow("🖥️ OS", specs!!.os)
                                    SpecRow("⚙️ CPU", "${specs!!.cpu} (${specs!!.cores} cores)")
                                    SpecRow("🧠 RAM", specs!!.ram)
                                    SpecRow("💾 Disk", specs!!.disk)
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Try: ls, python3 --version, node --version, pip install ...",
                            color = TextMuted, fontSize = 9.sp)
                    }
                }
            }

            items(entries) { entry ->
                // Command
                SelectionContainer {
                    Text(
                        "$ ${entry.command}",
                        color = Secondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (entry.isRunning) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = Secondary)
                        Spacer(Modifier.width(6.dp))
                        Text("Running on VPS...", color = TextMuted, fontSize = 10.sp)
                    }
                }

                entry.result?.let { result ->
                    SelectionContainer {
                        Text(
                            result.output,
                            color = if (result.isError) Error else TextPrimary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Border, thickness = 0.5.dp)

        // Input
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$", color = Secondary, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("command...", color = TextMuted, fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Secondary, unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    cursorColor = Secondary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            )

            Spacer(Modifier.width(4.dp))

            IconButton(
                onClick = {
                    val cmd = inputText.trim()
                    if (cmd.isNotBlank() && !isRunning) {
                        inputText = ""
                        isRunning = true
                        val newEntries = entries + TerminalEntry(cmd, isRunning = true)
                        onEntriesChange(newEntries)
                        val idx = newEntries.size - 1
                        scope.launch {
                            val result = TerminalManager.execute(cmd)
                            onEntriesChange(
                                newEntries.mapIndexed { i, e ->
                                    if (i == idx) e.copy(result = result, isRunning = false) else e
                                }
                            )
                            isRunning = false
                            onTerminalOutput?.invoke(cmd, result.output)
                        }
                    }
                },
                enabled = inputText.isNotBlank() && !isRunning
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send, "Run",
                    tint = if (inputText.isNotBlank() && !isRunning) Secondary else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SpecRow(icon: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(icon, fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
        Text(value, color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}
