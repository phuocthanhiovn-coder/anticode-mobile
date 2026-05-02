package vn.anticode.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import vn.anticode.mobile.ai.AnticodeApi
import vn.anticode.mobile.ai.ChatMessage
import vn.anticode.mobile.data.FileManager
import vn.anticode.mobile.data.SettingsStore
import vn.anticode.mobile.ui.chat.ChatPanel
import vn.anticode.mobile.ui.files.FileExplorer
import vn.anticode.mobile.ui.settings.SettingsScreen
import vn.anticode.mobile.ui.theme.*
import java.io.File

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermission()
        setContent {
            AnticodeTheme {
                AnticodeMainApp()
            }
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ uses MANAGE_EXTERNAL_STORAGE — needs special intent
            // For now, use app-specific directory
        } else {
            val perms = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val needed = perms.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                permissionLauncher.launch(needed.toTypedArray())
            }
        }
    }
}

// Navigation screens
enum class AppScreen { LOGIN, MAIN, SETTINGS }
enum class BottomTab { CHAT, TERMINAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnticodeMainApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { AnticodeApi() }

    // Persisted settings
    val apiKey by SettingsStore.getApiKey(context).collectAsState(initial = "")
    val baseUrl by SettingsStore.getBaseUrl(context).collectAsState(initial = "https://anticode.vn")
    val selectedModel by SettingsStore.getSelectedModel(context).collectAsState(initial = "claude-sonnet-4-6")
    var availableModels by remember {
        mutableStateOf(listOf("claude-sonnet-4-6", "claude-opus-4-6", "gpt-5.5", "gpt-5.4-mini", "deepseek-3.2"))
    }

    // Navigation
    var screen by remember { mutableStateOf(if (apiKey.isBlank()) AppScreen.LOGIN else AppScreen.MAIN) }

    // Auto-navigate: if API key becomes blank → login, if filled → main
    LaunchedEffect(apiKey) {
        if (apiKey.isBlank()) {
            screen = AppScreen.LOGIN
        } else if (screen == AppScreen.LOGIN) {
            screen = AppScreen.MAIN
        }
    }

    // Configure API
    LaunchedEffect(apiKey, baseUrl) {
        if (apiKey.isNotBlank()) {
            api.configure(baseUrl, apiKey)
            try {
                val loaded = api.getModels()
                if (loaded.isNotEmpty()) availableModels = loaded.map { it.id }
            } catch (_: Exception) { }
        }
    }

    // UI state
    var bottomTab by remember { mutableStateOf(BottomTab.CHAT) }
    var showFiles by remember { mutableStateOf(false) }
    var currentDir by remember { mutableStateOf(getDefaultDir()) }
    var openFile by remember { mutableStateOf<File?>(null) }
    var fileContent by remember { mutableStateOf("") }
    var fileDirty by remember { mutableStateOf(false) }

    // Chat state
    var chatMessages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isStreaming by remember { mutableStateOf(false) }
    var streamContent by remember { mutableStateOf("") }
    var streamJob by remember { mutableStateOf<Job?>(null) }

    // Terminal state
    var terminalLines by remember { mutableStateOf(listOf("$ anticode terminal v1.0", "Type a command...")) }
    var terminalInput by remember { mutableStateOf("") }

    // --- Functions ---
    fun openFileAction(file: File) {
        openFile = file
        fileContent = FileManager.readFile(file)
        fileDirty = false
        showFiles = false
    }

    fun saveFile() {
        openFile?.let {
            FileManager.writeFile(it, fileContent)
            fileDirty = false
        }
    }

    fun sendMessage(text: String) {
        val userMsg = ChatMessage("user", text)
        chatMessages = chatMessages + userMsg

        val job = scope.launch {
            isStreaming = true
            streamContent = ""

            val systemPrompt = buildString {
                append("You are Anticode AI, an expert coding assistant. Reply in the user's language.")
                if (openFile != null) {
                    append("\n\nOpen file: ${openFile!!.name}")
                    append("\n```${FileManager.getLanguage(openFile!!.extension)}\n")
                    append(fileContent.take(6000))
                    append("\n```")
                }
            }

            try {
                api.streamChat(
                    messages = chatMessages.takeLast(10),
                    model = selectedModel,
                    systemPrompt = systemPrompt
                ).catch { e ->
                    streamContent += "\n[Error: ${e.message}]"
                }.collect { chunk ->
                    streamContent += chunk
                }

                if (streamContent.isNotBlank()) {
                    chatMessages = chatMessages + ChatMessage("assistant", streamContent)
                }
            } catch (e: Exception) {
                chatMessages = chatMessages + ChatMessage("assistant", "[Error: ${e.message}]")
            } finally {
                isStreaming = false
                streamContent = ""
            }
        }
        streamJob = job
    }

    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        if (streamContent.isNotBlank()) {
            chatMessages = chatMessages + ChatMessage("assistant", streamContent + "\n[Stopped]")
        }
        isStreaming = false
        streamContent = ""
    }

    // --- Render ---
    when (screen) {
        AppScreen.LOGIN -> {
            LoginScreen(
                apiKey = apiKey,
                baseUrl = baseUrl,
                onApiKeyChange = { scope.launch { SettingsStore.setApiKey(context, it) } },
                onBaseUrlChange = { scope.launch { SettingsStore.setBaseUrl(context, it) } },
                onLogin = { if (apiKey.isNotBlank()) screen = AppScreen.MAIN }
            )
        }

        AppScreen.SETTINGS -> {
            SettingsScreen(
                apiKey = apiKey,
                baseUrl = baseUrl,
                selectedModel = selectedModel,
                models = availableModels,
                onApiKeyChange = { scope.launch { SettingsStore.setApiKey(context, it) } },
                onBaseUrlChange = { scope.launch { SettingsStore.setBaseUrl(context, it) } },
                onModelChange = { scope.launch { SettingsStore.setSelectedModel(context, it) } },
                onBack = { screen = AppScreen.MAIN }
            )
        }

        AppScreen.MAIN -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Anticode", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Primary)
                                if (openFile != null) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(openFile!!.name, fontSize = 13.sp, color = TextSecondary)
                                    if (fileDirty) {
                                        Text(" *", fontSize = 13.sp, color = Warning)
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { showFiles = !showFiles }) {
                                Icon(
                                    if (showFiles) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                    contentDescription = "Files",
                                    tint = Warning
                                )
                            }
                        },
                        actions = {
                            if (openFile != null && fileDirty) {
                                IconButton(onClick = { saveFile() }) {
                                    Icon(Icons.Filled.Save, "Save", tint = Secondary)
                                }
                            }
                            IconButton(onClick = { screen = AppScreen.SETTINGS }) {
                                Icon(Icons.Filled.Settings, "Settings", tint = TextSecondary)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
                    )
                },
                containerColor = Background
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // Top: Files + Editor
                    Row(modifier = Modifier.weight(1f)) {
                        AnimatedVisibility(visible = showFiles) {
                            Row {
                                FileExplorer(
                                    currentDir = currentDir,
                                    onNavigate = { currentDir = it },
                                    onFileSelect = { openFileAction(it) },
                                    modifier = Modifier.width(220.dp).fillMaxHeight()
                                )
                                VerticalDivider(color = Border, thickness = 0.5.dp)
                            }
                        }

                        // Editor
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight().background(CodeBackground)
                        ) {
                            if (openFile != null) {
                                BasicTextField(
                                    value = fileContent,
                                    onValueChange = {
                                        fileContent = it
                                        fileDirty = true
                                    },
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                    textStyle = TextStyle(
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 20.sp
                                    ),
                                    cursorBrush = SolidColor(Primary)
                                )
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Filled.Code, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text("Open a file to start", color = TextMuted, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Border, thickness = 0.5.dp)

                    // Bottom: Tabs + Content
                    Column(modifier = Modifier.weight(0.8f)) {
                        Row(modifier = Modifier.fillMaxWidth().background(Surface)) {
                            BottomTabButton(Icons.AutoMirrored.Filled.Chat, "Chat", bottomTab == BottomTab.CHAT) {
                                bottomTab = BottomTab.CHAT
                            }
                            BottomTabButton(Icons.Filled.Terminal, "Terminal", bottomTab == BottomTab.TERMINAL) {
                                bottomTab = BottomTab.TERMINAL
                            }
                        }

                        when (bottomTab) {
                            BottomTab.CHAT -> {
                                if (!api.isConfigured()) {
                                    Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Filled.Key, null, tint = Warning, modifier = Modifier.size(32.dp))
                                            Spacer(Modifier.height(8.dp))
                                            Text("Set API Key in Settings", color = TextSecondary, fontSize = 13.sp)
                                            Spacer(Modifier.height(8.dp))
                                            TextButton(onClick = { screen = AppScreen.SETTINGS }) {
                                                Text("Open Settings", color = Primary)
                                            }
                                        }
                                    }
                                } else {
                                    ChatPanel(
                                        messages = chatMessages,
                                        isStreaming = isStreaming,
                                        currentStreamContent = streamContent,
                                        modelName = selectedModel,
                                        onSend = { sendMessage(it) },
                                        onStop = { stopStreaming() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            BottomTab.TERMINAL -> {
                                TerminalView(
                                    lines = terminalLines,
                                    input = terminalInput,
                                    onInputChange = { terminalInput = it },
                                    onSubmit = {
                                        terminalLines = terminalLines + "$ $terminalInput"
                                        terminalLines = terminalLines + "Command execution available in Phase 2"
                                        terminalInput = ""
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Login Screen ---
@Composable
fun LoginScreen(
    apiKey: String,
    baseUrl: String,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Background).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Code, null, tint = Primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Anticode", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Primary)
        Text("AI Code Editor", fontSize = 14.sp, color = TextSecondary)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text("API Base URL") },
            leadingIcon = { Icon(Icons.Filled.Language, null, Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = loginFieldColors()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            leadingIcon = { Icon(Icons.Filled.Key, null, Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = loginFieldColors()
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onLogin,
            enabled = apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Icon(Icons.Filled.Login, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Connect", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = Border,
    focusedLabelColor = Primary,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLeadingIconColor = Primary,
    unfocusedLeadingIconColor = TextSecondary,
    cursorColor = Primary
)

// --- Terminal View ---
@Composable
fun TerminalView(
    lines: List<String>,
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(Background).padding(8.dp)) {
        // Output
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(lines.size) { i ->
                Text(
                    lines[i],
                    color = if (lines[i].startsWith("$")) Secondary else TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
        }
        // Input
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$ ", color = Secondary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Secondary),
                singleLine = true
            )
            IconButton(onClick = onSubmit, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.PlayArrow, "Run", tint = Secondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}




// --- Bottom Tab Button ---
@Composable
fun RowScope.BottomTabButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.textButtonColors(contentColor = if (selected) Primary else TextMuted)
    ) {
        Icon(icon, label, Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// --- Helpers ---
fun getDefaultDir(): File {
    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return if (downloads.exists()) downloads else Environment.getExternalStorageDirectory()
}
