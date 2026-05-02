package vn.anticode.mobile.data

import java.io.File

data class FileItem(
    val file: File,
    val name: String = file.name,
    val isDirectory: Boolean = file.isDirectory,
    val extension: String = file.extension,
    val size: Long = if (file.isFile) file.length() else 0
)

object FileManager {
    fun listFiles(directory: File): List<FileItem> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()

        return directory.listFiles()
            ?.filter { !it.name.startsWith(".") } // Hide dotfiles
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?.map { FileItem(it) }
            ?: emptyList()
    }

    fun readFile(file: File): String {
        return try {
            file.readText()
        } catch (e: Exception) {
            "// Error reading file: ${e.message}"
        }
    }

    fun writeFile(file: File, content: String): Boolean {
        return try {
            file.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun createFile(parent: File, name: String): File? {
        return try {
            val newFile = File(parent, name)
            if (name.endsWith("/")) {
                newFile.mkdirs()
            } else {
                newFile.parentFile?.mkdirs()
                newFile.createNewFile()
            }
            newFile
        } catch (e: Exception) {
            null
        }
    }

    fun deleteFile(file: File): Boolean {
        return try {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } catch (e: Exception) {
            false
        }
    }

    fun getLanguage(extension: String): String {
        return when (extension.lowercase()) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "js", "mjs", "cjs" -> "javascript"
            "ts", "tsx" -> "typescript"
            "py" -> "python"
            "rb" -> "ruby"
            "go" -> "go"
            "rs" -> "rust"
            "c", "h" -> "c"
            "cpp", "cc", "hpp" -> "cpp"
            "cs" -> "csharp"
            "swift" -> "swift"
            "php" -> "php"
            "html", "htm" -> "html"
            "css", "scss", "sass" -> "css"
            "json" -> "json"
            "xml" -> "xml"
            "yaml", "yml" -> "yaml"
            "md", "markdown" -> "markdown"
            "sql" -> "sql"
            "sh", "bash", "zsh" -> "shell"
            "dart" -> "dart"
            "lua" -> "lua"
            "r" -> "r"
            "gradle" -> "groovy"
            else -> "plaintext"
        }
    }
}
