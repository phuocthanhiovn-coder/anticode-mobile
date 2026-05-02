package vn.anticode.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
            items(messages) { msg -> MessageBubble(msg) }

            if (isStreaming && currentStreamContent.isNotEmpty()) {
                item { MessageBubble(ChatMessage("assistant", currentStreamContent)) }
            }

            if (messages.isEmpty() && !isStreaming) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Ask AI anything...", color = TextMuted, fontSize = 13.sp)
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

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        SelectionContainer {
            Text(
                text = message.content,
                color = if (isUser) TextPrimary else TextPrimary,
                fontSize = 13.sp,
                fontFamily = if (!isUser) FontFamily.Default else FontFamily.Default,
                lineHeight = 19.sp,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp, topEnd = 14.dp,
                            bottomStart = if (isUser) 14.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 14.dp
                        )
                    )
                    .background(if (isUser) Primary.copy(alpha = 0.2f) else SurfaceVariant)
                    .padding(10.dp)
            )
        }
    }
}
