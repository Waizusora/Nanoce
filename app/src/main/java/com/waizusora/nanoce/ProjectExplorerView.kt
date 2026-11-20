package com.waizusora.nanoce

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * SAF-backed hierarchical project browser. All document operations use content URIs.
 */
internal class ProjectExplorerView(
    context: Context,
    private val storage: StorageAccess
) : ScrollView(context) {
    private val rows = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(12, 8, 12, 24)
    }
    private val expanded = mutableSetOf<Uri>()
    private var rootUri: Uri? = null
    var onOpenTextFile: ((Uri, String, String) -> Unit)? = null

    init {
        addView(rows)
        setFillViewport(true)
        setBackgroundColor(context.getColor(R.color.nanoce_background))
    }

    fun showWorkspace(uri: Uri?) {
        rootUri = uri
        expanded.clear()
        render()
    }

    fun refresh() = render()

    private fun render() {
        rows.removeAllViews()
        val root = rootUri
        if (root == null) {
            addLabel("Choose a project folder to browse its files.", context.getColor(R.color.nanoce_muted))
            return
        }
        addLabel("PROJECT", context.getColor(R.color.nanoce_muted), 11f)
        renderDirectory(root, 0, 0)
    }

    private fun renderDirectory(directoryUri: Uri, depth: Int, visited: Int) {
        if (depth > MAX_DEPTH || visited > MAX_ENTRIES) return
        val children = try {
            storage.listChildren(directoryUri)
        } catch (error: Exception) {
            addLabel("Unable to list folder: ${error.message ?: "access error"}", context.getColor(R.color.nanoce_error), 12f, depth)
            return
        }

        children.forEach { entry ->
            if (rows.childCount >= MAX_ENTRIES) {
                addLabel("Entry limit reached. Open a subfolder to browse more.", context.getColor(R.color.nanoce_muted), 12f, depth)
                return
            }
            val isExpanded = entry.isDirectory && entry.uri in expanded
            val prefix = if (entry.isDirectory) {
                if (isExpanded) "▾  " else "▸  "
            } else {
                "    "
            }
            val symbol = if (entry.isDirectory) "📁 " else "📄 "
            val row = TextView(context).apply {
                text = prefix + symbol + entry.name
                textSize = 14f
                setTextColor(if (entry.isDirectory) context.getColor(R.color.nanoce_text) else context.getColor(R.color.nanoce_text))
                setPadding(8 + depth * INDENT, 10, 8, 10)
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                setOnClickListener {
                    if (entry.isDirectory) {
                        if (isExpanded) expanded.remove(entry.uri) else expanded.add(entry.uri)
                        render()
                    } else {
                        previewFile(entry.uri, entry.name, entry.mimeType)
                    }
                }
            }
            rows.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            if (entry.isDirectory && isExpanded) {
                renderDirectory(entry.uri, depth + 1, visited + children.size)
            }
        }
    }

    fun showCreateActions() {
        val root = rootUri ?: run {
            toast("Choose a project folder first")
            return
        }
        AlertDialog.Builder(context)
            .setTitle("Add to project")
            .setItems(arrayOf("New folder", "New text file")) { _, which ->
                when (which) {
                    0 -> promptForName("New folder", "Folder name") { name ->
                        runCatching { storage.createDirectory(root, name) }
                            .onSuccess { refresh(); toast("Folder created") }
                            .onFailure { toast("Create failed: ${it.message}") }
                    }
                    1 -> promptForName("New text file", "File name") { name ->
                        runCatching {
                            storage.createFile(root, name, mimeForName(name))
                        }.onSuccess { uri ->
                            if (uri != null) storage.openOutput(uri)?.use { it.write(ByteArray(0)) }
                            refresh()
                            toast("File created")
                        }.onFailure { toast("Create failed: ${it.message}") }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptForName(title: String, hint: String, onCreate: (String) -> Unit) {
        val input = EditText(context).apply {
            this.hint = hint
            setSingleLine(true)
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) toast("Name cannot be empty") else onCreate(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun previewFile(uri: Uri, name: String, mimeType: String) {
        try {
            val bytes = storage.openInput(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (total <= MAX_PREVIEW_BYTES) {
                    val remaining = MAX_PREVIEW_BYTES + 1 - total
                    val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (count <= 0) break
                    output.write(buffer, 0, count)
                    total += count
                }
                output.toByteArray()
            } ?: throw IllegalStateException("Provider returned no input stream")

            if (bytes.size > MAX_PREVIEW_BYTES) {
                AlertDialog.Builder(context)
                    .setTitle("File too large to edit")
                    .setMessage("This editor currently supports files up to 256 KiB. The original file was not changed.")
                    .setPositiveButton("Close", null)
                    .show()
                return
            }

            val looksTextual = mimeType.startsWith("text/") ||
                name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS
            if (!looksTextual) {
                AlertDialog.Builder(context)
                    .setTitle(name)
                    .setMessage("Binary or unsupported document. Read ${bytes.size} bytes.")
                    .setPositiveButton("Close", null)
                    .show()
                return
            }
            val content = String(bytes, Charsets.UTF_8)
            onOpenTextFile?.invoke(uri, name, content)
                ?: AlertDialog.Builder(context)
                    .setTitle(name)
                    .setMessage(content)
                    .setPositiveButton("Close", null)
                    .show()
        } catch (error: Exception) {
            toast("Unable to open file: ${error.message ?: "read error"}")
        }
    }

    private fun addLabel(
        value: String,
        color: Int,
        size: Float = 13f,
        depth: Int = 0
    ) {
        rows.addView(TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(8 + depth * INDENT, 10, 8, 10)
        })
    }

    private fun toast(message: String) =
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    private fun mimeForName(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "kt", "java", "c", "h", "cpp", "hpp", "rs", "py", "sh", "md", "txt",
            "json", "xml", "html", "css", "js", "toml", "yaml", "yml" -> "text/plain"
            else -> "application/octet-stream"
        }

    companion object {
        private const val INDENT = 20
        private const val MAX_DEPTH = 12
        private const val MAX_ENTRIES = 2000
        private const val MAX_PREVIEW_BYTES = 256 * 1024
        private val TEXT_EXTENSIONS = setOf(
            "kt", "java", "c", "h", "cpp", "hpp", "rs", "py", "sh", "md", "txt",
            "json", "xml", "html", "css", "js", "toml", "yaml", "yml", "gradle"
        )
    }
}
