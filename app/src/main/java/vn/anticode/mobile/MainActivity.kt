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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.anticode.mobile.ai.AnticodeApi
import vn.anticode.mobile.ai.ChatMessage
import vn.anticode.mobile.data.FileManager
import vn.anticode.mobile.data.SettingsStore
import vn.anticode.mobile.ui.chat.ChatPanel
import vn.anticode.mobile.ui.files.FileExplorer
import vn.anticode.mobile.ui.settings.SettingsScreen
import vn.anticode.mobile.ui.theme.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

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
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
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

    // Configure API + auto-login
    LaunchedEffect(apiKey, baseUrl) {
        if (apiKey.isBlank()) {
            screen = AppScreen.LOGIN
            isLoggedIn = false
        } else {
            api.configure(baseUrl, apiKey)
            if (!isLoggedIn) {
                val valid = api.testConnection()
                if (valid) { isLoggedIn = true; screen = AppScreen.MAIN }
                else { screen = AppScreen.LOGIN }
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
    val editorTheme by SettingsStore.getEditorTheme(context).collectAsState(initial = "dark")

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

    // Image state
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImageUrl by remember { mutableStateOf<String?>(null) }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingImageUri = uri
            // Upload image in background
            scope.launch {
                val url = uploadImage(context, uri, baseUrl, apiKey)
                if (url != null) {
                    pendingImageUrl = url
                } else {
                    snackbarHostState.showSnackbar("Failed to upload image", duration = SnackbarDuration.Short)
                    pendingImageUri = null
                }
            }
        }
    }

    // --- Functions ---
    fun openFileAction(file: File) {
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
            scope.launch { snackbarHostState.showSnackbar("No file open!", duration = SnackbarDuration.Short) }
            return
        }
        openFile?.let {
            fileContent = code
            val ok = FileManager.writeFile(it, fileContent)
            fileDirty = false
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (ok) "Applied to ${it.name}" else "Failed to apply",
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
            scope.launch { snackbarHostState.showSnackbar("Created ${file.name}", duration = SnackbarDuration.Short) }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Failed to create $fileName", duration = SnackbarDuration.Short) }
        }
    }

    fun processAIActions(response: String) {
        val createRegex = Regex("<<<CREATE_FILE:(.+?)>>>\\s*```\\w*\\n([\\s\\S]*?)```")
        createRegex.findAll(response).forEach { match ->
            createFileFromChat(match.groupValues[1].trim(), match.groupValues[2].trimEnd())
        }
    }

    fun sendMessage(text: String) {
        // Build user message with image if attached
        val imageUrl = pendingImageUrl
        val userMsg = ChatMessage("user", text, imageUrl = imageUrl)
        chatMessages = chatMessages + userMsg

        // Clear pending image
        pendingImageUri = null
        pendingImageUrl = null

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
                append("3. ANSWER QUESTIONS: You can explain code, debug, suggest improvements.\n\n")
                append("=== RULES ===\n")
                append("- NEVER say 'I have created/edited the file' without providing code blocks.\n")
                append("- NEVER lie about doing something you haven't done.\n")
                append("- ALWAYS provide actual code in code blocks for any file changes.\n")
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
                val dirFiles = FileManager.listFiles(currentDir)
                if (dirFiles.isNotEmpty()) {
                    append("\n\n=== CURRENT DIR: ${currentDir.absolutePath} ===\n")
                    append(dirFiles.joinToString("\n") {
                        (if (it.isDirectory) "[DIR] " else "") + it.name
                    })
                }
                if (imageUrl != null) {
                    append("\n\n=== USER ATTACHED AN IMAGE ===\n")
                    append("The user has attached a screenshot/image. Analyze it and help fix any issues shown.\n")
                    append("Image URL: $imageUrl")
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
                wordWrap = false,
                editorTheme = editorTheme,
                onApiKeyChange = { scope.launch { SettingsStore.setApiKey(context, it) } },
                onBaseUrlChange = { scope.launch { SettingsStore.setBaseUrl(context, it) } },
                onModelChange = { scope.launch { SettingsStore.setSelectedModel(context, it) } },
                onEditorFontSizeChange = { scope.launch { SettingsStore.setEditorFontSize(context, it) } },
                onChatFontSizeChange = { scope.launch { SettingsStore.setChatFontSize(context, it) } },
                onShowLineNumbersChange = { scope.launch { SettingsStore.setShowLineNumbers(context, it) } },
                onWordWrapChange = { },
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
                                        if (openFile?.absolutePath == file.absolutePath) {
                                            openFile = null; fileContent = ""; fileDirty = false
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

                        // Editor
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

                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(editorBg)) {
                            if (openFile != null) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    if (showLineNumbers) {
                                        val lineCount = fileContent.count { it == '\n' } + 1
                                        Column(
                                            modifier = Modifier.fillMaxHeight()
                                                .background(editorBg.copy(alpha = 0.8f))
                                                .padding(horizontal = 4.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            for (i in 1..lineCount.coerceAtMost(500)) {
                                                Text("$i", color = lineNumColor, fontSize = editorFontSize.sp,
                                                    fontFamily = FontFamily.Monospace, lineHeight = (editorFontSize * 1.5f).sp)
                                            }
                                        }
                                        VerticalDivider(color = Border.copy(alpha = 0.3f), thickness = 0.5.dp)
                                    }
                                    BasicTextField(
                                        value = fileContent,
                                        onValueChange = { fileContent = it; fileDirty = true },
                                        modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                                        textStyle = TextStyle(color = editorFg, fontSize = editorFontSize.sp,
                                            fontFamily = FontFamily.Monospace, lineHeight = (editorFontSize * 1.5f).sp),
                                        cursorBrush = SolidColor(Primary)
                                    )
                                }
                            } else {
                                Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.Code, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text("Open a file to start", color = TextMuted, fontSize = 14.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Tap folder icon to browse files", color = TextMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Border, thickness = 0.5.dp)

                    // Bottom: Chat panel (no tabs, just chat)
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
                                onApplyCode = { code -> applyCodeToFile(code) },
                                hasOpenFile = openFile != null,
                                onPickImage = {
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                pendingImageUri = pendingImageUri,
                                onClearImage = { pendingImageUri = null; pendingImageUrl = null },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Image Upload Helper ---
suspend fun uploadImage(context: android.content.Context, uri: Uri, baseUrl: String, apiKey: String): String? = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        val bytes = inputStream.readBytes()
        inputStream.close()

        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", "screenshot.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/upload/image")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val json = JSONObject(response.body?.string() ?: "{}")
            json.optString("url", null)
        } else null
    } catch (_: Exception) { null }
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
    var showKey by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(Background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.widthIn(max = 360.dp).padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Anticode", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Primary)
                Spacer(Modifier.height(4.dp))
                Text("AI Code Editor", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    enabled = apiKey.isNotBlank()
                ) {
                    Text("Connect", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

fun getDefaultDir(): File {
    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (!dir.exists()) dir.mkdirs()
    return dir
}
