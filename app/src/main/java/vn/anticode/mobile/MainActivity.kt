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

    // Navigation — start at LOGIN, only go to MAIN after explicit login
    var screen by remember { mutableStateOf(AppScreen.LOGIN) }
    var isLoggedIn by remember { mutableStateOf(false) }

    // Auto-navigate: only go back to login when key is cleared (logout)
    LaunchedEffect(apiKey) {
        if (apiKey.isBlank()) {
            screen = AppScreen.LOGIN
            isLoggedIn = false
        } else if (!isLoggedIn) {
            // Key exists from previous session — verify it's still valid
            api.configure(baseUrl, apiKey)
            val valid = api.testConnection()
            if (valid) {
                isLoggedIn = true
                screen = AppScreen.MAIN
            } else {
                screen = AppScreen.LOGIN
            }
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
                onLogin = { if (apiKey.isNotBlank()) { isLoggedIn = true; screen = AppScreen.MAIN } }
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

                    // Bottom: Chat panel
                    Column(modifier = Modifier.weight(0.8f)) {
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
