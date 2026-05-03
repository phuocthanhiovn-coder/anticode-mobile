package vn.anticode.mobile.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.anticode.mobile.ai.ChatMessage
import vn.anticode.mobile.ui.theme.*

@Composable
fun ChatPanel(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    currentStreamContent: String,
    modelName: String,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onApplyCode: ((String) -> Unit)? = null,
    hasOpenFile: Boolean = false,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll
    val totalItems = messages.size + if (isStreaming && currentStreamContent.isNotEmpty()) 1 else 0
    LaunchedEffect(totalItems, currentStreamContent.length) {
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(modifier = modifier.background(Background)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.SmartToy, null, tint = Primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(modelName, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            if (isStreaming) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = Secondary)
            }
        }

        HorizontalDivider(color = Border, thickness = 0.5.dp)

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages) { msg ->
                MessageBubble(msg, onApplyCode = onApplyCode, hasOpenFile = hasOpenFile)
            }

            if (isStreaming && currentStreamContent.isNotEmpty()) {
                item { MessageBubble(ChatMessage("assistant", currentStreamContent), onApplyCode = null, hasOpenFile = false) }
            }

            if (messages.isEmpty() && !isStreaming) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.SmartToy, null, tint = TextMuted, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Ask AI anything...", color = TextMuted, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("AI can read & edit your open file", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Border, thickness = 0.5.dp)

        // Input
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface).padding(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Message...", color = TextMuted, fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary, unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    cursorColor = Primary
                ),
                shape = RoundedCornerShape(16.dp),
                maxLines = 3,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
            )

            Spacer(Modifier.width(4.dp))

            if (isStreaming) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Filled.StopCircle, "Stop", tint = Error, modifier = Modifier.size(26.dp))
                }
            } else {
                IconButton(
                    onClick = {
                        val text = inputText.trim()
                        if (text.isNotBlank()) { onSend(text); inputText = "" }
                    },
                    enabled = inputText.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send, "Send",
                        tint = if (inputText.isNotBlank()) Primary else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ============ Message Bubble with Markdown ============

@Composable
fun MessageBubble(
    message: ChatMessage,
    onApplyCode: ((String) -> Unit)? = null,
    hasOpenFile: Boolean = false
) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // User message — simple bubble
            SelectionContainer {
                Text(
                    text = message.content,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp))
                        .background(Primary.copy(alpha = 0.2f))
                        .padding(10.dp)
                )
            }
        } else {
            // AI message — parse markdown with code blocks
            val parts = parseMarkdown(message.content)
            Column(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .clip(RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp))
                    .background(SurfaceVariant)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                parts.forEach { part ->
                    when (part) {
                        is MarkdownPart.Text -> {
                            SelectionContainer {
                                RichText(part.content)
                            }
                        }
                        is MarkdownPart.CodeBlock -> {
                            CodeBlockView(
                                code = part.code,
                                language = part.language,
                                onApply = if (hasOpenFile && onApplyCode != null) {
                                    { onApplyCode(part.code) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============ Code Block with Copy/Apply ============

@Composable
fun CodeBlockView(
    code: String,
    language: String,
    onApply: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CodeBackground)
    ) {
        // Header: language + buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                language.ifEmpty { "code" },
                color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))

            // Copy button
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                    copied = true
                },
                modifier = Modifier.height(24.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Icon(
                    if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    null, Modifier.size(12.dp),
                    tint = if (copied) Secondary else TextMuted
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    if (copied) "Copied" else "Copy",
                    fontSize = 10.sp,
                    color = if (copied) Secondary else TextMuted
                )
            }

            // Apply button (only if file is open)
            if (onApply != null) {
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = onApply,
                    modifier = Modifier.height(24.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Filled.Check, null, Modifier.size(12.dp), tint = Primary)
                    Spacer(Modifier.width(3.dp))
                    Text("Apply", fontSize = 10.sp, color = Primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Code content
        SelectionContainer {
            Text(
                text = code.trimEnd(),
                color = TextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp)
            )
        }
    }
}

// ============ Rich Text (bold, italic, inline code) ============

@Composable
fun RichText(content: String) {
    val annotated = buildAnnotatedString {
        var i = 0
        while (i < content.length) {
            when {
                // Bold: **text**
                content.startsWith("**", i) -> {
                    val end = content.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(content.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(content[i])
                        i++
                    }
                }
                // Inline code: `code`
                content[i] == '`' && !content.startsWith("```", i) -> {
                    val end = content.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = CodeBackground,
                            fontSize = 12.sp
                        )) {
                            append(content.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(content[i])
                        i++
                    }
                }
                // Italic: *text*
                content[i] == '*' && (i + 1 < content.length && content[i + 1] != '*') -> {
                    val end = content.indexOf('*', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(content.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(content[i])
                        i++
                    }
                }
                else -> {
                    append(content[i])
                    i++
                }
            }
        }
    }

    Text(
        text = annotated,
        color = TextPrimary,
        fontSize = 13.sp,
        lineHeight = 19.sp
    )
}

// ============ Markdown Parser ============

sealed class MarkdownPart {
    data class Text(val content: String) : MarkdownPart()
    data class CodeBlock(val language: String, val code: String) : MarkdownPart()
}

fun parseMarkdown(content: String): List<MarkdownPart> {
    val parts = mutableListOf<MarkdownPart>()
    val codeBlockRegex = Regex("```(\\w*)\\s*\\n([\\s\\S]*?)```")

    var lastIndex = 0
    codeBlockRegex.findAll(content).forEach { match ->
        // Text before code block
        if (match.range.first > lastIndex) {
            val text = content.substring(lastIndex, match.range.first).trim()
            if (text.isNotEmpty()) parts.add(MarkdownPart.Text(text))
        }
        // Code block
        parts.add(MarkdownPart.CodeBlock(
            language = match.groupValues[1],
            code = match.groupValues[2]
        ))
        lastIndex = match.range.last + 1
    }
    // Remaining text after last code block
    if (lastIndex < content.length) {
        val text = content.substring(lastIndex).trim()
        if (text.isNotEmpty()) parts.add(MarkdownPart.Text(text))
    }

    // If no code blocks found, return as single text
    if (parts.isEmpty()) parts.add(MarkdownPart.Text(content))

    return parts
}
