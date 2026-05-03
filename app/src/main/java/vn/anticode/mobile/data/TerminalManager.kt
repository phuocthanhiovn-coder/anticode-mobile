package vn.anticode.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class TerminalResult(
    val output: String,
    val exitCode: Int,
    val isError: Boolean = exitCode != 0
)

object TerminalManager {

    /**
     * Execute a shell command and return the combined output.
     * Works on Android without root for basic commands.
     */
    suspend fun execute(
        command: String,
        workingDir: File? = null
    ): TerminalResult = withContext(Dispatchers.IO) {
        try {
            val parts = parseCommand(command)
            val pb = ProcessBuilder(parts)
            pb.redirectErrorStream(true)
            if (workingDir != null && workingDir.exists()) {
                pb.directory(workingDir)
            }
            // Set basic environment
            pb.environment().apply {
                put("HOME", workingDir?.absolutePath ?: "/data/local/tmp")
                put("TERM", "xterm-256color")
            }

            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            TerminalResult(
                output = output.ifEmpty { if (exitCode == 0) "✓ Command completed" else "✗ Command failed" },
                exitCode = exitCode
            )
        } catch (e: Exception) {
            TerminalResult(
                output = "Error: ${e.message}",
                exitCode = -1,
                isError = true
            )
        }
    }

    /**
     * Parse command string into parts, handling basic quoting.
     */
    private fun parseCommand(command: String): List<String> {
        // Use sh -c for complex commands (pipes, redirects, etc.)
        return if (command.contains("|") || command.contains(">") || command.contains("&&") || command.contains(";")) {
            listOf("sh", "-c", command)
        } else {
            val parts = mutableListOf<String>()
            var current = StringBuilder()
            var inQuote = false
            var quoteChar = ' '

            for (c in command) {
                when {
                    !inQuote && (c == '"' || c == '\'') -> { inQuote = true; quoteChar = c }
                    inQuote && c == quoteChar -> { inQuote = false }
                    !inQuote && c == ' ' -> {
                        if (current.isNotEmpty()) { parts.add(current.toString()); current = StringBuilder() }
                    }
                    else -> current.append(c)
                }
            }
            if (current.isNotEmpty()) parts.add(current.toString())
            parts
        }
    }

    /**
     * Get list of available commands on this device
     */
    suspend fun getAvailableCommands(): List<String> = withContext(Dispatchers.IO) {
        val cmds = mutableListOf<String>()
        // Common paths
        listOf("/system/bin", "/system/xbin", "/data/local/tmp").forEach { dir ->
            val d = File(dir)
            if (d.exists() && d.isDirectory) {
                d.listFiles()?.forEach { f ->
                    if (f.canExecute()) cmds.add(f.name)
                }
            }
        }
        cmds.distinct().sorted()
    }
}
