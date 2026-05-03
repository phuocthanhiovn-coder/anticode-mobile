package vn.anticode.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.anticode.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    apiKey: String,
    baseUrl: String,
    selectedModel: String,
    models: List<String>,
    editorFontSize: Float,
    chatFontSize: Float,
    showLineNumbers: Boolean,
    wordWrap: Boolean,
    editorTheme: String,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onEditorFontSizeChange: (Float) -> Unit,
    onChatFontSizeChange: (Float) -> Unit,
    onShowLineNumbersChange: (Boolean) -> Unit,
    onWordWrapChange: (Boolean) -> Unit,
    onEditorThemeChange: (String) -> Unit,
    onBack: () -> Unit
) {
    var showKey by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Primary, unfocusedBorderColor = Border,
        focusedLabelColor = Primary, unfocusedLabelColor = TextSecondary,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
        focusedLeadingIconColor = Primary, unfocusedLeadingIconColor = TextSecondary,
        cursorColor = Primary
    )

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        TopAppBar(
            title = { Text("Settings", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Surface,
                titleContentColor = TextPrimary,
                navigationIconContentColor = TextPrimary
            )
        )

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section: API
            SectionLabel("API Connection")

            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("Base URL") },
                leadingIcon = { Icon(Icons.Filled.Language, null, Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API Key") },
                leadingIcon = { Icon(Icons.Filled.Key, null, Modifier.size(18.dp)) },
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            "Toggle", Modifier.size(18.dp)
                        )
                    }
                },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
            )

            HorizontalDivider(color = Border)

            // Section: Model
            SectionLabel("Default Model")

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    leadingIcon = { Icon(Icons.Filled.SmartToy, null, Modifier.size(18.dp)) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(SurfaceVariant)
                ) {
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model, color = TextPrimary, fontSize = 13.sp) },
                            onClick = { onModelChange(model); expanded = false },
                            leadingIcon = {
                                if (model == selectedModel) {
                                    Icon(Icons.Filled.Check, null, tint = Secondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = Border)

            // Section: Appearance
            SectionLabel("Appearance")

            // Editor font size
            FontSizeSlider(
                label = "Editor Font Size",
                icon = Icons.Filled.Code,
                value = editorFontSize,
                onValueChange = onEditorFontSizeChange
            )

            // Chat font size
            FontSizeSlider(
                label = "Chat Font Size",
                icon = Icons.Filled.Chat,
                value = chatFontSize,
                onValueChange = onChatFontSizeChange
            )

            HorizontalDivider(color = Border)

            // Section: Editor
            SectionLabel("Editor Options")

            // Line Numbers toggle
            SettingsToggle(
                icon = Icons.Filled.FormatListNumbered,
                label = "Line Numbers",
                description = "Show line numbers in editor",
                checked = showLineNumbers,
                onCheckedChange = onShowLineNumbersChange
            )

            // Word Wrap toggle
            SettingsToggle(
                icon = Icons.AutoMirrored.Filled.WrapText,
                label = "Word Wrap",
                description = "Wrap long lines in editor",
                checked = wordWrap,
                onCheckedChange = onWordWrapChange
            )

            HorizontalDivider(color = Border)

            // Section: Theme
            SectionLabel("Editor Theme")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeCard("Dark", editorTheme == "dark",
                    bg = Color(0xFF0D1117), fg = Color(0xFFE6EDF3),
                    accent = Color(0xFF7B61FF), onClick = { onEditorThemeChange("dark") },
                    modifier = Modifier.weight(1f))
                ThemeCard("Monokai", editorTheme == "monokai",
                    bg = Color(0xFF272822), fg = Color(0xFFF8F8F2),
                    accent = Color(0xFFA6E22E), onClick = { onEditorThemeChange("monokai") },
                    modifier = Modifier.weight(1f))
                ThemeCard("Ocean", editorTheme == "ocean",
                    bg = Color(0xFF1B2B34), fg = Color(0xFFD8DEE9),
                    accent = Color(0xFF6699CC), onClick = { onEditorThemeChange("ocean") },
                    modifier = Modifier.weight(1f))
            }

            HorizontalDivider(color = Border)

            // Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (apiKey.isNotBlank()) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        null,
                        tint = if (apiKey.isNotBlank()) Success else Warning,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (apiKey.isNotBlank()) "Connected to $baseUrl" else "API Key required",
                        color = if (apiKey.isNotBlank()) Success else Warning,
                        fontSize = 12.sp
                    )
                }
            }

            // Logout
            if (apiKey.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onApiKeyChange("") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Logout")
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun SettingsToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(description, color = TextMuted, fontSize = 10.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Primary,
                    checkedTrackColor = Primary.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Border
                )
            )
        }
    }
}

@Composable
fun ThemeCard(
    name: String,
    selected: Boolean,
    bg: Color,
    fg: Color,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (selected) Modifier.border(2.dp, Primary, RoundedCornerShape(10.dp)) else Modifier.border(1.dp, Border, RoundedCornerShape(10.dp)))
            .clickable(onClick = onClick)
            .background(bg)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Preview
        Text("fn()", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text("code", color = fg, fontSize = 8.sp)
        Spacer(Modifier.height(4.dp))
        Text(name, color = if (selected) Primary else fg.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FontSizeSlider(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text("${value.toInt()}sp", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 10f..24f,
                steps = 13,
                colors = SliderDefaults.colors(
                    thumbColor = Primary,
                    activeTrackColor = Primary,
                    inactiveTrackColor = Border
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = Primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
}
