package vn.anticode.mobile.ui.files

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.anticode.mobile.data.FileItem
import vn.anticode.mobile.data.FileManager
import vn.anticode.mobile.ui.theme.*
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileExplorer(
    currentDir: File,
    onNavigate: (File) -> Unit,
    onFileSelect: (File) -> Unit,
    onCreateFile: ((File) -> Unit)? = null,
    onDeleteFile: ((File) -> Unit)? = null,
    onRenameFile: ((File, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var files by remember(currentDir) { mutableStateOf(FileManager.listFiles(currentDir)) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<File?>(null) }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var contextMenuFile by remember { mutableStateOf<File?>(null) }

    fun refreshFiles() {
        files = FileManager.listFiles(currentDir)
    }

    Column(modifier = modifier.background(Background)) {
        // Header with back button + folder name + create button
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentDir.parentFile != null) {
                IconButton(onClick = { currentDir.parentFile?.let { onNavigate(it) } }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
            Icon(Icons.Filled.Folder, null, tint = Warning, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                currentDir.name.ifEmpty { "Storage" },
                color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            // Create new file/folder button
            IconButton(onClick = { showCreateDialog = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Add, "New", tint = Primary, modifier = Modifier.size(18.dp))
            }
        }

        HorizontalDivider(color = Border, thickness = 0.5.dp)

        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Empty folder", color = TextMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showCreateDialog = true }) {
                        Text("+ Create file", color = Primary, fontSize = 12.sp)
                    }
                }
            }
        } else {
            LazyColumn {
                items(files, key = { it.file.absolutePath }) { item ->
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (item.isDirectory) onNavigate(item.file) else onFileSelect(item.file)
                                    },
                                    onLongClick = {
                                        contextMenuFile = item.file
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (item.isDirectory) Icons.Filled.Folder else fileIcon(item.extension),
                                contentDescription = null,
                                tint = if (item.isDirectory) Warning else fileColor(item.extension),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!item.isDirectory && item.size > 0) {
                                    Text(formatBytes(item.size), color = TextMuted, fontSize = 10.sp)
                                }
                            }
                            if (item.isDirectory) {
                                Icon(Icons.Filled.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                            }
                        }

                        // Context menu dropdown
                        DropdownMenu(
                            expanded = contextMenuFile == item.file,
                            onDismissRequest = { contextMenuFile = null },
                            offset = DpOffset(100.dp, 0.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename", fontSize = 13.sp) },
                                onClick = {
                                    contextMenuFile = null
                                    showRenameDialog = item.file
                                },
                                leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(16.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", fontSize = 13.sp, color = Error) },
                                onClick = {
                                    contextMenuFile = null
                                    showDeleteDialog = item.file
                                },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, Modifier.size(16.dp), tint = Error) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ===== Dialogs =====

    // Create File/Folder Dialog
    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        var isFolder by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Surface,
            title = { Text(if (isFolder) "New Folder" else "New File", color = TextPrimary, fontSize = 16.sp) },
            text = {
                Column {
                    // Toggle: File / Folder
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = !isFolder,
                            onClick = { isFolder = false },
                            label = { Text("File", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary.copy(alpha = 0.2f),
                                selectedLabelColor = Primary
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = isFolder,
                            onClick = { isFolder = true },
                            label = { Text("Folder", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Warning.copy(alpha = 0.2f),
                                selectedLabelColor = Warning
                            )
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text(if (isFolder) "folder_name" else "filename.kt", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary, unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            cursorColor = Primary
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            val name = if (isFolder) "$newName/" else newName
                            val created = FileManager.createFile(currentDir, name)
                            if (created != null) {
                                onCreateFile?.invoke(created)
                                refreshFiles()
                            }
                            showCreateDialog = false
                        }
                    }
                ) { Text("Create", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    // Delete Confirmation Dialog
    showDeleteDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = Surface,
            title = { Text("Delete ${file.name}?", color = TextPrimary, fontSize = 16.sp) },
            text = {
                Text(
                    if (file.isDirectory) "This will delete the folder and all its contents." else "This file will be permanently deleted.",
                    color = TextSecondary, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    FileManager.deleteFile(file)
                    onDeleteFile?.invoke(file)
                    refreshFiles()
                    showDeleteDialog = null
                }) { Text("Delete", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    // Rename Dialog
    showRenameDialog?.let { file ->
        var newName by remember { mutableStateOf(file.name) }

        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            containerColor = Surface,
            title = { Text("Rename", color = TextPrimary, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        cursorColor = Primary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank() && newName != file.name) {
                        val newFile = File(file.parentFile, newName)
                        if (file.renameTo(newFile)) {
                            onRenameFile?.invoke(file, newName)
                            refreshFiles()
                        }
                    }
                    showRenameDialog = null
                }) { Text("Rename", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

private fun fileIcon(ext: String) = when (ext.lowercase()) {
    "kt", "java", "py", "js", "ts", "go", "rs", "c", "cpp", "cs", "rb", "php", "swift", "dart" -> Icons.Filled.Code
    "json", "xml", "yaml", "yml", "toml" -> Icons.Filled.DataObject
    "md", "txt", "log" -> Icons.Filled.Description
    "html", "css", "scss" -> Icons.Filled.Language
    "png", "jpg", "jpeg", "gif", "svg", "webp" -> Icons.Filled.Image
    "gradle", "kts" -> Icons.Filled.Build
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun fileColor(ext: String): Color = when (ext.lowercase()) {
    "kt" -> PrimaryVariant
    "java" -> Warning
    "py" -> Secondary
    "js", "ts" -> Warning
    "json" -> Secondary
    "html" -> Accent
    "css", "scss" -> Primary
    "md" -> TextSecondary
    else -> TextSecondary
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1048576 -> "${bytes / 1024}KB"
    else -> "${"%.1f".format(bytes / 1048576.0)}MB"
}
