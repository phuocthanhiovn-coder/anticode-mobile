package vn.anticode.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import vn.anticode.mobile.data.TerminalManager
import vn.anticode.mobile.ui.chat.ChatPanel
import vn.anticode.mobile.ui.files.FileExplorer
import vn.anticode.mobile.ui.settings.SettingsScreen
import vn.anticode.mobile.ui.terminal.TerminalEntry
import vn.anticode.mobile.ui.terminal.TerminalPanel
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
            // Android 11+ — request MANAGE_EXTERNAL_STORAGE via Settings
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    // Fallback: open general storage settings
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
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
    var screen by remember { mutableStateOf(AppScreen.LOGIN) }
    var isLoggedIn by remember { mutableStateOf(false) }

    // Configure API + Terminal + auto-login when key exists
    LaunchedEffect(apiKey, baseUrl) {
        if (apiKey.isBlank()) {
            screen = AppScreen.LOGIN
            isLoggedIn = false
        } else {
            api.configure(baseUrl, apiKey)
            TerminalManager.configure(baseUrl, apiKey)
            if (!isLoggedIn) {
                val valid = api.testConnection()
                if (valid) {
                    isLoggedIn = true
                    screen = AppScreen.MAIN
                } else {
                    screen = AppScreen.LOGIN
                }
            }
            try {
                val loaded = api.getModels()
                if (loaded.isNotEmpty()) availableModels = loaded.map { it.id }
            } catch (_: Exception) { }
        }
    }

    val editorFontSize by SettingsStore.getEditorFontSize(context).collectAsState(initial = 13f)
    val chatFontSize by SettingsStore.getChatFontSize(context).collectAsState(initial = 13f)
    val showLineNumbers by SettingsStore.getShowLineNumbers(context).collectAsState(initial = true)
    val wordWrap by SettingsStore.getWordWrap(context).collectAsState(initial = false)
    val editorTheme by SettingsStore.getEditorTheme(context).collectAsState(initial = "dark")

    // UI state
    var showFiles by remember { mutableStateOf(false) }
    var currentDir by remember { mutableStateOf(getDefaultDir()) }
    var openFile by remember { mutableStateOf<File?>(null) }
    var fileContent by remember { mutableStateOf("") }
    var fileDirty by remember { mutableStateOf(false) }
    var bottomTab by remember { mutableStateOf("chat") }

    // Terminal state (hoisted so it persists across tab switches)
    var terminalEntries by remember { mutableStateOf(listOf<TerminalEntry>()) }
    var terminalHistory by remember { mutableStateOf(listOf<String>()) }

    // Chat state
    var chatMessages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isStreaming by remember { mutableStateOf(false) }
    var streamContent by remember { mutableStateOf("") }
    var streamJob by remember { mutableStateOf<Job?>(null) }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // --- Functions ---
    fun openFileAction(file: File) {
        // Auto-save previous file if dirty
        if (fileDirty && openFile != null) {
            FileManager.writeFile(openFile!!, fileContent)
        }
        openFile = file
        fileContent = FileManager.readFile(file)
        fileDirty = false
        showFiles = false
    }

    fun saveFile() {
        openFile?.let {
            val ok = FileManager.writeFile(it, fileContent)
            fileDirty = false
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (ok) "Saved ${it.name}" else "Failed to save",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    fun applyCodeToFile(code: String) {
        if (openFile == null) {
            scope.launch {
                snackbarHostState.showSnackbar("❌ No file open! Open a file first.", duration = SnackbarDuration.Short)
            }
            return
        }
        openFile?.let {
            fileContent = code
            val ok = FileManager.writeFile(it, fileContent)
            fileDirty = false
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (ok) "✅ Applied to ${it.name}" else "❌ Failed to apply (check permissions)",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    fun createFileFromChat(fileName: String, content: String) {
        val file = FileManager.createFile(currentDir, fileName)
        if (file != null && file.isFile) {
            FileManager.writeFile(file, content)
            openFileAction(file)
            scope.launch {
                snackbarHostState.showSnackbar("✅ Created ${file.name}", duration = SnackbarDuration.Short)
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("❌ Failed to create $fileName (check permissions)", duration = SnackbarDuration.Short)
            }
        }
    }

    // Process AI response for file actions and terminal commands
    fun processAIActions(response: String) {
        // Create files
        val createRegex = Regex("<<<CREATE_FILE:(.+?)>>>\\s*```\\w*\\n([\\s\\S]*?)```")
        createRegex.findAll(response).forEach { match ->
            val fileName = match.groupValues[1].trim()
            val content = match.groupValues[2].trimEnd()
            createFileFromChat(fileName, content)
        }

        // Run terminal commands
        val cmdRegex = Regex("<<<RUN_CMD:(.+?)>>>")
        cmdRegex.findAll(response).forEach { match ->
            val command = match.groupValues[1].trim()
            scope.launch {
                val result = TerminalManager.execute(command)
                val output = if (result.isError) "❌ $command\n${result.output}" else "✅ $command\n${result.output}"
                chatMessages = chatMessages + ChatMessage("assistant", "```\n$ $command\n${result.output}\n```")
                snackbarHostState.showSnackbar(
                    if (result.isError) "Command failed" else "Command executed",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    fun sendMessage(text: String) {
        val userMsg = ChatMessage("user", text)
        chatMessages = chatMessages + userMsg

        val job = scope.launch {
            isStreaming = true
            streamContent = ""

            val systemPrompt = buildString {
                append("You are Anticode AI, a coding assistant inside a mobile code editor.\n")
                append("Reply in the user's language.\n\n")
                append("=== YOUR CAPABILITIES ===\n")
                append("1. EDIT OPEN FILE: Show the COMPLETE updated file in a ```language code block. ")
                append("The user will click 'Apply' to save it.\n")
                append("2. CREATE NEW FILE: Use this EXACT format:\n")
                append("<<<CREATE_FILE:filename.ext>>>\n")
                append("```language\nfile content here\n```\n")
                append("The system will automatically create the file.\n")
                append("4. RUN TERMINAL COMMANDS: Use this EXACT format:\n")
                append("<<<RUN_CMD:command here>>>\n")
                append("The system will execute the command and show output.\n")
                append("3. ANSWER QUESTIONS: You can explain code, debug, suggest improvements.\n\n")
                append("=== RULES ===\n")
                append("- NEVER say 'I have created/edited the file' without providing code blocks.\n")
                append("- NEVER lie about doing something you haven't done.\n")
                append("- ALWAYS provide actual code in code blocks for any file changes.\n")
                append("- For terminal commands, ALWAYS use <<<RUN_CMD:command>>> format.\n")
                if (openFile != null) {
                    append("\n=== CURRENTLY OPEN FILE: ${openFile!!.name} ===\n")
                    append("Path: ${openFile!!.absolutePath}\n")
                    append("Language: ${FileManager.getLanguage(openFile!!.extension)}\n")
                    append("```${FileManager.getLanguage(openFile!!.extension)}\n")
                    append(fileContent.take(8000))
                    append("\n```")
                } else {
                    append("\n=== NO FILE IS OPEN ===\n")
                    append("The user has no file open. If they want to create a file, use <<<CREATE_FILE:name>>>")
                }
                // List files in current directory
                val dirFiles = FileManager.listFiles(currentDir)
                if (dirFiles.isNotEmpty()) {
                    append("\n\n=== CURRENT DIR: ${currentDir.absolutePath} ===\n")
                    append(dirFiles.joinToString("\n") {
                        (if (it.isDirectory) "📁 " else "📄 ") + it.name
                    })
                }
                // Terminal history for AI awareness
                if (terminalHistory.isNotEmpty()) {
                    append("\n\n=== RECENT TERMINAL OUTPUT (VPS) ===\n")
                    append(terminalHistory.joinToString("\n---\n"))
                    append("\n\nYou can see what commands were run and their output. Analyze errors and suggest fixes.")
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
                    // Process AI actions (auto-create files)
                    processAIActions(streamContent)
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
                onLogin = { if (apiKey.isNotBlank()) { isLoggedIn = true; screen = AppScreen.MAIN } }
            )
        }

        AppScreen.SETTINGS -> {
            SettingsScreen(
                apiKey = apiKey,
                baseUrl = baseUrl,
                selectedModel = selectedModel,
                models = availableModels,
                editorFontSize = editorFontSize,
                chatFontSize = chatFontSize,
                showLineNumbers = showLineNumbers,
                wordWrap = wordWrap,
                editorTheme = editorTheme,
                onApiKeyChange = { scope.launch { SettingsStore.setApiKey(context, it) } },
                onBaseUrlChange = { scope.launch { SettingsStore.setBaseUrl(context, it) } },
                onModelChange = { scope.launch { SettingsStore.setSelectedModel(context, it) } },
                onEditorFontSizeChange = { scope.launch { SettingsStore.setEditorFontSize(context, it) } },
                onChatFontSizeChange = { scope.launch { SettingsStore.setChatFontSize(context, it) } },
                onShowLineNumbersChange = { scope.launch { SettingsStore.setShowLineNumbers(context, it) } },
                onWordWrapChange = { scope.launch { SettingsStore.setWordWrap(context, it) } },
                onEditorThemeChange = { scope.launch { SettingsStore.setEditorTheme(context, it) } },
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
                            // New Chat button
                            IconButton(onClick = {
                                chatMessages = emptyList()
                                streamContent = ""
                                streamJob?.cancel()
                                streamJob = null
                                isStreaming = false
                            }) {
                                Icon(Icons.Filled.AddComment, "New Chat", tint = Primary)
                            }
                            IconButton(onClick = { screen = AppScreen.SETTINGS }) {
                                Icon(Icons.Filled.Settings, "Settings", tint = TextSecondary)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
                    )
                },
                containerColor = Background,
                snackbarHost = { SnackbarHost(snackbarHostState) }
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
                                    onCreateFile = { file ->
                                        if (file.isFile) openFileAction(file)
                                        scope.launch { snackbarHostState.showSnackbar("Created ${file.name}", duration = SnackbarDuration.Short) }
                                    },
                                    onDeleteFile = { file ->
                                        // If deleted file is currently open, close it
                                        if (openFile?.absolutePath == file.absolutePath) {
                                            openFile = null
                                            fileContent = ""
                                            fileDirty = false
                                        }
                                        scope.launch { snackbarHostState.showSnackbar("Deleted ${file.name}", duration = SnackbarDuration.Short) }
                                    },
                                    onRenameFile = { _, newName ->
                                        scope.launch { snackbarHostState.showSnackbar("Renamed to $newName", duration = SnackbarDuration.Short) }
                                    },
                                    modifier = Modifier.width(220.dp).fillMaxHeight()
                                )
                                VerticalDivider(color = Border, thickness = 0.5.dp)
                            }
                        }

                        // Editor with theme
                        val editorBg = when (editorTheme) {
                            "monokai" -> Color(0xFF272822)
                            "ocean" -> Color(0xFF1B2B34)
                            else -> CodeBackground
                        }
                        val editorFg = when (editorTheme) {
                            "monokai" -> Color(0xFFF8F8F2)
                            "ocean" -> Color(0xFFD8DEE9)
                            else -> TextPrimary
                        }
                        val lineNumColor = when (editorTheme) {
                            "monokai" -> Color(0xFF75715E)
                            "ocean" -> Color(0xFF65737E)
                            else -> TextMuted
                        }

                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight().background(editorBg)
                        ) {
                            if (openFile != null) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    // Line numbers column
                                    if (showLineNumbers) {
                                        val lineCount = fileContent.count { it == '\n' } + 1
                                        Column(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .background(editorBg.copy(alpha = 0.8f))
                                                .padding(horizontal = 4.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            for (i in 1..lineCount.coerceAtMost(500)) {
                                                Text(
                                                    "$i",
                                                    color = lineNumColor,
                                                    fontSize = editorFontSize.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    lineHeight = (editorFontSize * 1.5f).sp
                                                )
                                            }
                                        }
                                        VerticalDivider(color = Border.copy(alpha = 0.3f), thickness = 0.5.dp)
                                    }

                                    // Code editor
                                    BasicTextField(
                                        value = fileContent,
                                        onValueChange = {
                                            fileContent = it
                                            fileDirty = true
                                        },
                                        modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                                        textStyle = TextStyle(
                                            color = editorFg,
                                            fontSize = editorFontSize.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = (editorFontSize * 1.5f).sp
                                        ),
                                        cursorBrush = SolidColor(Primary)
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Filled.Code, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text("Open a file to start", color = TextMuted, fontSize = 14.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Use 📁 to browse files", color = TextMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Border, thickness = 0.5.dp)

                    // Bottom: Chat/Terminal panel with tab switcher
                    Column(modifier = Modifier.weight(0.8f)) {
                        // Tab bar
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = bottomTab == "chat",
                                onClick = { bottomTab = "chat" },
                                label = { Text("💬 Chat", fontSize = 11.sp) },
                                modifier = Modifier.height(28.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary.copy(alpha = 0.2f),
                                    selectedLabelColor = Primary
                                )
                            )
                            Spacer(Modifier.width(4.dp))
                            FilterChip(
                                selected = bottomTab == "terminal",
                                onClick = { bottomTab = "terminal" },
                                label = { Text("⌨ Terminal", fontSize = 11.sp) },
                                modifier = Modifier.height(28.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Secondary.copy(alpha = 0.2f),
                                    selectedLabelColor = Secondary
                                )
                            )
                        }

                        HorizontalDivider(color = Border, thickness = 0.5.dp)

                        when (bottomTab) {
                            "chat" -> {
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
                                        onApplyCode = { code -> applyCodeToFile(code) },
                                        hasOpenFile = openFile != null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            "terminal" -> {
                                TerminalPanel(
                                    entries = terminalEntries,
                                    onEntriesChange = { terminalEntries = it },
                                    modifier = Modifier.fillMaxSize(),
                                    onTerminalOutput = { cmd, output ->
                                        terminalHistory = (terminalHistory + "$ $cmd\n$output").takeLast(5)
                                    }
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
    val scope = rememberCoroutineScope()
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showKey by remember { mutableStateOf(false) }

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
            onValueChange = { onApiKeyChange(it); errorMessage = null },
            label = { Text("API Key") },
            leadingIcon = { Icon(Icons.Filled.Key, null, Modifier.size(18.dp)) },
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        "Toggle visibility", Modifier.size(18.dp)
                    )
                }
            },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = loginFieldColors(),
            isError = errorMessage != null
        )

        // Error message
        AnimatedVisibility(visible = errorMessage != null) {
            Text(
                errorMessage ?: "",
                color = Error,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (apiKey.isNotBlank()) {
                    isConnecting = true
                    errorMessage = null
                    val testApi = AnticodeApi(baseUrl.trimEnd('/'), apiKey.trim())
                    scope.launch {
                        val ok = testApi.testConnection()
                        isConnecting = false
                        if (ok) {
                            onLogin()
                        } else {
                            errorMessage = "Không thể kết nối. Kiểm tra lại API Key và Base URL."
                        }
                    }
                }
            },
            enabled = apiKey.isNotBlank() && !isConnecting,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = TextPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text("Đang kết nối...", fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Filled.Login, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Connect", fontWeight = FontWeight.SemiBold)
            }
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



// --- Helpers ---
fun getDefaultDir(): File {
    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return if (downloads.exists()) downloads else Environment.getExternalStorageDirectory()
}
