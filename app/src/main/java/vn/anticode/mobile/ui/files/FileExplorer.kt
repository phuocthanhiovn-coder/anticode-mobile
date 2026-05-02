package vn.anticode.mobile.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.anticode.mobile.data.FileItem
import vn.anticode.mobile.data.FileManager
import vn.anticode.mobile.ui.theme.*
import java.io.File

@Composable
fun FileExplorer(
    currentDir: File,
    onNavigate: (File) -> Unit,
    onFileSelect: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val files = remember(currentDir) { FileManager.listFiles(currentDir) }

    Column(modifier = modifier.background(Background)) {
        // Header
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
        }

        HorizontalDivider(color = Border, thickness = 0.5.dp)

        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Empty folder", color = TextMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn {
                items(files, key = { it.file.absolutePath }) { item ->
                    FileRow(item) {
                        if (item.isDirectory) onNavigate(item.file) else onFileSelect(item.file)
                    }
                }
            }
        }
    }
}

@Composable
fun FileRow(item: FileItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
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
