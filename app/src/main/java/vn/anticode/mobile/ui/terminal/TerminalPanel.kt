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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.anticode.mobile.data.TerminalManager
import vn.anticode.mobile.data.TerminalResult
import vn.anticode.mobile.ui.theme.*
import java.io.File

data class TerminalEntry(
    val command: String,
    val result: TerminalResult? = null,
    val isRunning: Boolean = false
)

@Composable
fun TerminalPanel(
    workingDir: File,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(listOf<TerminalEntry>()) }
    var isRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Auto-scroll
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    Column(modifier = modifier.background(CodeBackground)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Terminal, null, tint = Secondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Terminal", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(workingDir.absolutePath, color = TextMuted, fontSize = 9.sp, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            // Clear button
            IconButton(onClick = { entries = emptyList() }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.DeleteSweep, "Clear", tint = TextMuted, modifier = Modifier.size(14.dp))
            }
        }

        HorizontalDivider(color = Border, thickness = 0.5.dp)

        // Output
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (entries.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Terminal, null, tint = TextMuted, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Shell Terminal", color = TextMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Run commands: ls, cat, pwd, mkdir, echo...", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }

            items(entries) { entry ->
                // Command line
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = Primary)
                        Spacer(Modifier.width(6.dp))
                        Text("Running...", color = TextMuted, fontSize = 10.sp)
                    }
                }

                // Output
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
                        val entry = TerminalEntry(cmd, isRunning = true)
                        entries = entries + entry
                        scope.launch {
                            val result = TerminalManager.execute(cmd, workingDir)
                            entries = entries.map {
                                if (it.command == cmd && it.isRunning) it.copy(result = result, isRunning = false) else it
                            }
                            isRunning = false
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
