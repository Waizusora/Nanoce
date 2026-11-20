package com.waizusora.nanoce

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

private data class EditorDocument(
    val uri: Uri,
    val name: String,
    var text: String,
    var savedText: String
) {
    val isDirty: Boolean get() = text != savedText
}

private class CursorEditText(
    context: Context,
    private val onCursorChanged: (() -> Unit)?
) : EditText(context) {
    private val activeLinePaint = android.graphics.Paint().apply {
        color = 0x1842C8F5
        style = android.graphics.Paint.Style.FILL
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        invalidate()
        onCursorChanged?.invoke()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val textLayout = layout
        if (textLayout != null && text.isNotEmpty()) {
            val offset = selectionStart.coerceIn(0, text.length)
            val line = textLayout.getLineForOffset(offset)
            val top = textLayout.getLineTop(line).toFloat() + totalPaddingTop
            val bottom = textLayout.getLineBottom(line).toFloat() + totalPaddingTop
            canvas.drawRect(
                scrollX.toFloat(),
                top,
                (scrollX + width).toFloat(),
                bottom,
                activeLinePaint
            )
        }
        super.onDraw(canvas)
    }
}

/** A small multi-document editor backed by Android SAF streams. */
internal class EditorWorkspaceView(
    context: Context,
    private val storage: StorageAccess
) : LinearLayout(context) {
    private val bracketHighlightSpans = mutableListOf<android.text.style.BackgroundColorSpan>()

    private val tabs = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val tabScroller = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(tabs)
    }
    private val editor = CursorEditText(context) {
        updateStatus()
        updateBracketHighlights()
    }.apply {
        textSize = 14f
        typeface = android.graphics.Typeface.MONOSPACE
        setTextColor(context.getColor(R.color.nanoce_text))
        setHintTextColor(context.getColor(R.color.nanoce_muted))
        setPadding(16, 14, 16, 24)
        gravity = Gravity.TOP or Gravity.START
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setHorizontallyScrolling(true)
        setBackgroundColor(context.getColor(R.color.nanoce_background))
    }
    private val lineNumbers = TextView(context).apply {
        textSize = 14f
        typeface = android.graphics.Typeface.MONOSPACE
        gravity = Gravity.TOP or Gravity.END
        setTextColor(context.getColor(R.color.nanoce_muted))
        setPadding(dp(8), dp(14), dp(10), dp(24))
        setBackgroundColor(context.getColor(R.color.nanoce_background))
        setHorizontallyScrolling(true)
        isSingleLine = false
        text = "1"
    }
    private val status = TextView(context).apply {
        textSize = 11f
        setTextColor(context.getColor(R.color.nanoce_muted))
        setPadding(12, 6, 12, 6)
    }
    private val checkJson = Button(context).apply {
        text = "Validate"
        textSize = 11f
        isAllCaps = false
        visibility = View.GONE
        setOnClickListener { validateActiveSource() }
    }
    private val diagnosticView = TextView(context).apply {
        textSize = 12f
        setTextColor(context.getColor(R.color.nanoce_error))
        setPadding(12, 4, 12, 6)
        visibility = View.GONE
        setOnClickListener { navigateToJsonError() }
    }
    private val nativeBridge by lazy { NativeBridge() }
    private var jsonDiagnostic: String? = null
    private var jsonErrorLine: Int? = null
    private var jsonErrorColumn: Int? = null
    private val documents = linkedMapOf<Uri, EditorDocument>()
    private val undoHistory = mutableMapOf<Uri, MutableList<String>>()
    private val redoHistory = mutableMapOf<Uri, MutableList<String>>()
    private var activeUri: Uri? = null
    private var changingText = false
    private val highlightHandler = Handler(Looper.getMainLooper())
    private val highlightRevisions = mutableMapOf<Uri, Long>()
    private var pendingHighlight: Runnable? = null
    private var pendingHighlightUri: Uri? = null
    private var disposed = false
    private val parserSessionManagerDelegate = lazy {
        ParserSessionManager(
            NativeParserSessionBackend(nativeBridge),
            dispatchResult = { result -> highlightHandler.post(result) }
        )
    }
    private val parserSessionManager by parserSessionManagerDelegate

    init {
        orientation = VERTICAL
        setBackgroundColor(context.getColor(R.color.nanoce_background))
        addView(tabScroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(checkJson, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(diagnosticView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        addView(ScrollView(context).apply {
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                addView(lineNumbers, LinearLayout.LayoutParams(
                    dp(48), ViewGroup.LayoutParams.WRAP_CONTENT
                ))
                addView(editor, LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ))
            }, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            isFillViewport = true
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        editor.filters = editor.filters + InputFilter { source, start, end, dest, dstart, dend ->
            val inserted = source.subSequence(start, end).toString()
            if (inserted != "\n" && inserted != "\r\n") {
                null
            } else {
                var lineStart = dstart
                while (lineStart > 0 && dest[lineStart - 1] != '\n') {
                    lineStart--
                }
                val currentLine = dest.subSequence(lineStart, dstart).toString()
                val indentation = currentLine.takeWhile { it == ' ' || it == '\t' }
                val extraIndent = if (currentLine.trimEnd().endsWith("{")) {
                    if (indentation.contains('\t')) "\t" else "    "
                } else {
                    ""
                }
                inserted + indentation + extraIndent
            }
        }
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (changingText) return
                val document = activeDocument() ?: return
                val updatedText = s?.toString().orEmpty()
                if (document.text != updatedText) {
                    val history = undoHistory.getOrPut(document.uri) { mutableListOf() }
                    history.add(document.text)
                    if (history.size > MAX_UNDO_STEPS) history.removeAt(0)
                    redoHistory[document.uri]?.clear()
                    document.text = updatedText
                }
                jsonDiagnostic = null
                jsonErrorLine = null
                jsonErrorColumn = null
                diagnosticView.visibility = View.GONE
                updateLineNumbers()
                updateSyntaxHighlighting()
                updateStatus()
                updateBracketHighlights()
                renderTabs()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        showEmptyState()
    }

    /** Persist open tabs and unsaved buffers through an atomic, versioned snapshot. */
    fun saveSessionSnapshot(): Boolean {
        val snapshot = runCatching {
            SessionSnapshot(
                activeUri = activeUri?.toString(),
                selectionStart = editor.selectionStart.coerceAtLeast(0),
                selectionEnd = editor.selectionEnd.coerceAtLeast(0),
                documents = documents.values.map { document ->
                    SessionSnapshotDocument(
                        document.uri.toString(), document.name, document.text, document.savedText
                    )
                }
            ).let(SessionSnapshotCodec::encode)
        }.getOrElse { return false }

        val file = sessionSnapshotFile()
        var output: java.io.FileOutputStream? = null
        return try {
            val stream = file.startWrite()
            output = stream
            stream.write(snapshot)
            stream.flush()
            file.finishWrite(stream)
            output = null
            true
        } catch (_: Exception) {
            output?.let { file.failWrite(it) }
            false
        }
    }

    /** Restore only a fully validated snapshot; discard corrupt or obsolete payloads. */
    fun restoreSessionSnapshot(): Boolean {
        val file = sessionSnapshotFile()
        if (!file.baseFile.exists()) return false
        val restored = runCatching {
            file.openRead().use { input ->
                val maximum = SessionSnapshotCodec.MAX_TOTAL_BYTES + 1024 * 1024
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= maximum) { "Snapshot exceeds size limit" }
                    bytes.write(buffer, 0, count)
                }
                SessionSnapshotCodec.decode(bytes.toByteArray())
            }
        }.getOrElse {
            file.delete()
            return false
        }

        val restoredDocuments = linkedMapOf<Uri, EditorDocument>()
        restored.documents.forEach { document ->
            val uri = Uri.parse(document.uri)
            restoredDocuments[uri] = EditorDocument(uri, document.name, document.text, document.savedText)
        }
        documents.clear()
        documents.putAll(restoredDocuments)
        undoHistory.clear()
        redoHistory.clear()
        activeUri = restored.activeUri?.let(Uri::parse)
        renderTabs()
        val active = activeUri
        if (active != null) {
            activate(active)
            editor.setSelection(restored.selectionStart, restored.selectionEnd)
        } else {
            showEmptyState()
        }
        return true
    }

    fun clearSessionSnapshot() { sessionSnapshotFile().delete() }

    private fun sessionSnapshotFile(): AtomicFile =
        AtomicFile(java.io.File(context.filesDir, SESSION_SNAPSHOT_NAME))
    /** Release parser resources when the owning Activity is destroyed. */
    fun dispose() {
        if (disposed) return
        disposed = true
        cancelPendingHighlight()
        if (parserSessionManagerDelegate.isInitialized()) parserSessionManager.dispose()
    }

    fun openDocument(uri: Uri, name: String, text: String) {
        val existing = documents[uri]
        if (existing == null) {
            documents[uri] = EditorDocument(uri, name, text, text)
        }
        activate(uri)
    }

    fun saveActive() {
        val document = activeDocument() ?: run {
            toast("No document is open")
            return
        }
        if (!document.isDirty) {
            toast("No changes to save")
            return
        }
        runCatching {
            storage.openOutput(document.uri)?.use { output ->
                output.write(document.text.toByteArray(Charsets.UTF_8))
                output.flush()
            } ?: throw IllegalStateException("Provider returned no output stream")
        }.onSuccess {
            document.savedText = document.text
            updateStatus()
            renderTabs()
            toast("Saved ${document.name}")
        }.onFailure {
            toast("Save failed: ${it.message ?: "write error"}")
        }
    }

    fun saveAs(uri: Uri, name: String) {
        val source = activeDocument() ?: run {
            toast("No document is open")
            return
        }
        val content = source.text
        runCatching {
            storage.openOutput(uri)?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.flush()
            } ?: throw IllegalStateException("Provider returned no output stream")
        }.onSuccess {
            documents[uri] = EditorDocument(uri, name, content, content)
            undoHistory[uri] = mutableListOf()
            redoHistory[uri] = mutableListOf()
            activate(uri)
            toast("Saved as $name")
        }.onFailure {
            toast("Save as failed: ${it.message ?: "write error"}")
        }
    }

    fun undo() {
        val document = activeDocument() ?: return
        val history = undoHistory[document.uri] ?: return
        if (history.isEmpty()) {
            toast("Nothing to undo")
            return
        }
        redoHistory.getOrPut(document.uri) { mutableListOf() }.add(document.text)
        restoreText(document, history.removeAt(history.lastIndex))
    }

    fun redo() {
        val document = activeDocument() ?: return
        val history = redoHistory[document.uri] ?: return
        if (history.isEmpty()) {
            toast("Nothing to redo")
            return
        }
        undoHistory.getOrPut(document.uri) { mutableListOf() }.add(document.text)
        restoreText(document, history.removeAt(history.lastIndex))
    }

    private fun restoreText(document: EditorDocument, value: String) {
        changingText = true
        editor.setText(value)
        editor.setSelection(editor.text.length)
        document.text = value
        changingText = false
        updateLineNumbers()
        updateSyntaxHighlighting()
        updateStatus()
        updateBracketHighlights()
        renderTabs()
    }

    fun hasUnsavedChanges(): Boolean = documents.values.any { it.isDirty }

    fun confirmDiscardAndRun(action: () -> Unit) {
        if (!hasUnsavedChanges()) {
            action()
            return
        }
        AlertDialog.Builder(context)
            .setTitle("Unsaved changes")
            .setMessage("Discard unsaved changes in open documents?")
            .setPositiveButton("Discard") { _, _ ->
                closeAllDocuments()
                action()
            }
            .setNegativeButton("Keep editing", null)
            .show()
    }

    /** Close every tab before switching to a different project workspace. */
    fun confirmCloseAllAndRun(action: () -> Unit) {
        if (!hasUnsavedChanges()) {
            closeAllDocuments()
            action()
            return
        }
        AlertDialog.Builder(context)
            .setTitle("Unsaved changes")
            .setMessage("Discard unsaved changes and close all editor tabs before switching projects?")
            .setPositiveButton("Discard and continue") { _, _ ->
                closeAllDocuments()
                action()
            }
            .setNegativeButton("Keep editing", null)
            .show()
    }

    private fun closeAllDocuments() {
        cancelPendingHighlight()
        if (parserSessionManagerDelegate.isInitialized()) {
            parserSessionManager.closeDocuments(documents.keys.map(Uri::toString))
        }
        documents.clear()
        highlightRevisions.clear()
        undoHistory.clear()
        redoHistory.clear()
        activeUri = null
        renderTabs()
        showEmptyState()
    }

    private fun activate(uri: Uri) {
        val document = documents[uri] ?: return
        activeUri = uri
        undoHistory.getOrPut(uri) { mutableListOf() }
        redoHistory.getOrPut(uri) { mutableListOf() }
        jsonDiagnostic = null
        jsonErrorLine = null
        jsonErrorColumn = null
        diagnosticView.visibility = View.GONE
        checkJson.visibility = if (supportsNativeValidation(document.name)) View.VISIBLE else View.GONE
        changingText = true
        editor.setText(document.text)
        editor.setSelection(editor.text.length)
        updateLineNumbers()
        updateSyntaxHighlighting()
        changingText = false
        editor.visibility = View.VISIBLE
        updateStatus()
        renderTabs()
    }

    private fun activeDocument(): EditorDocument? = activeUri?.let(documents::get)

    private fun renderTabs() {
        tabs.removeAllViews()
        documents.values.forEach { document ->
            val selected = document.uri == activeUri
            val tab = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 2, 4, 2)
                setBackgroundColor(if (selected) context.getColor(R.color.nanoce_tab_selected) else context.getColor(R.color.nanoce_background))
            }
            val label = TextView(context).apply {
                text = document.name + if (document.isDirty) " •" else ""
                textSize = 12f
                setTextColor(if (selected) context.getColor(R.color.nanoce_accent) else context.getColor(R.color.nanoce_text))
                setBackgroundResource(R.drawable.toolbar_action_background)
                setPadding(8, 8, 8, 8)
                setOnClickListener { activate(document.uri) }
                setOnLongClickListener {
                    requestClose(document.uri)
                    true
                }
            }
            val close = TextView(context).apply {
                text = "×"
                textSize = 18f
                setTextColor(context.getColor(R.color.nanoce_muted))
                setPadding(8, 4, 8, 4)
                setOnClickListener { requestClose(document.uri) }
            }
            tab.addView(label)
            tab.addView(close)
            tabs.addView(tab, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun requestClose(uri: Uri) {
        val document = documents[uri] ?: return
        val close = {
            if (pendingHighlightUri == uri) cancelPendingHighlight()
            if (parserSessionManagerDelegate.isInitialized()) parserSessionManager.closeDocument(uri.toString())
            highlightRevisions.remove(uri)
            undoHistory.remove(uri)
            redoHistory.remove(uri)
            documents.remove(uri)
            if (activeUri == uri) {
                activeUri = documents.keys.lastOrNull()
                val next = activeDocument()
                if (next != null) activate(next.uri) else showEmptyState()
            }
            renderTabs()
        }
        if (document.isDirty) {
            AlertDialog.Builder(context)
                .setTitle("Close ${document.name}?")
                .setMessage("This tab has unsaved changes.")
                .setPositiveButton("Discard and close") { _, _ -> close() }
                .setNegativeButton("Cancel", null)
                .show()
        } else close()
    }

    private fun showEmptyState() {
        changingText = true
        editor.setText("")
        changingText = false
        updateLineNumbers()
        updateSyntaxHighlighting()
        editor.hint = "Open a text file from the project explorer"
        status.text = "No document open"
    }


    /** Highlights the bracket adjacent to the caret and its matching partner. */
    private fun updateBracketHighlights() {
        val editable = editor.text
        bracketHighlightSpans.forEach { editable.removeSpan(it) }
        bracketHighlightSpans.clear()
        if (editable.isEmpty()) return

        val caret = editor.selectionStart.coerceIn(0, editable.length)
        val candidate = when {
            caret < editable.length && editable[caret] in "(){}[]" -> caret
            caret > 0 && editable[caret - 1] in "(){}[]" -> caret - 1
            else -> return
        }
        val opening = "({["
        val closing = ")}]"
        val character = editable[candidate]
        val partner = when {
            character in opening -> {
                val expected = closing[opening.indexOf(character)]
                var depth = 0
                var match = -1
                for (index in candidate until editable.length) {
                    when (editable[index]) {
                        character -> depth++
                        expected -> {
                            depth--
                            if (depth == 0) {
                                match = index
                                break
                            }
                        }
                    }
                }
                match
            }
            else -> {
                val expected = opening[closing.indexOf(character)]
                var depth = 0
                var match = -1
                for (index in candidate downTo 0) {
                    when (editable[index]) {
                        character -> depth++
                        expected -> {
                            depth--
                            if (depth == 0) {
                                match = index
                                break
                            }
                        }
                    }
                }
                match
            }
        }
        if (partner < 0) return

        listOf(candidate, partner).forEach { index ->
            val span = android.text.style.BackgroundColorSpan(0x6642C8F5)
            editable.setSpan(span, index, index + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            bracketHighlightSpans.add(span)
        }
    }

    private fun updateSyntaxHighlighting() {
        if (disposed) return
        val language = activeDocument()?.let { EditorLanguage.fromFileName(it.name) }
            ?: EditorLanguage.PLAIN
        val parserLanguage = when (language) {
            EditorLanguage.JSON -> "json"
            EditorLanguage.CPP -> "cpp"
            EditorLanguage.RUST -> "rust"
            EditorLanguage.KOTLIN -> "kotlin"
            EditorLanguage.PYTHON -> "python"
            else -> null
        }
        val uri = activeUri
        if (parserLanguage == null || uri == null) {
            cancelPendingHighlight()
            SyntaxHighlighter.apply(editor.text, language)
            return
        }
        val source = editor.text.toString()
        val revision = (highlightRevisions[uri] ?: 0L) + 1L
        highlightRevisions[uri] = revision
        cancelPendingHighlight()
        val task = Runnable {
            if (disposed || activeUri != uri || highlightRevisions[uri] != revision) return@Runnable
            parserSessionManager.request(uri.toString(), parserLanguage, source) { encoded ->
                if (disposed || activeUri != uri || highlightRevisions[uri] != revision ||
                    editor.text.toString() != source) return@request
                val applied = encoded?.let {
                    SyntaxHighlighter.applyTreeSitterResult(editor.text, it)
                } ?: false
                if (!applied) SyntaxHighlighter.apply(editor.text, language)
            }
        }
        pendingHighlight = task
        pendingHighlightUri = uri
        highlightHandler.postDelayed(task, 120L)
    }

    private fun cancelPendingHighlight() {
        pendingHighlight?.let(highlightHandler::removeCallbacks)
        pendingHighlight = null
        pendingHighlightUri = null
    }

    private fun supportsNativeValidation(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in
            setOf("json", "c", "h", "cc", "hh", "cpp", "hpp", "cxx", "hxx", "rs")

    private fun validateActiveSource() {
        val document = activeDocument() ?: return
        val extension = document.name.substringAfterLast('.', "").lowercase()
        val language = when (extension) {
            "json" -> "json"
            "rs" -> "rust"
            "c", "h" -> "c"
            "cc", "hh", "cpp", "hpp", "cxx", "hxx" -> "cpp"
            "kt", "kts" -> "kotlin"
            "py", "pyw" -> "python"
            else -> { toast("No native parser for this file type"); return }
        }
        val languageLabel = when (language) {
            "json" -> "JSON"
            "c" -> "C"
            "rust" -> "Rust"
            "kotlin" -> "Kotlin"
            "python" -> "Python"
            else -> "C++"
        }
        runCatching { nativeBridge.nativeCodeDiagnostics(editor.text.toString(), language) }
            .onSuccess { result ->
                jsonErrorLine = null
                jsonErrorColumn = null
                jsonDiagnostic = when {
                    result == "OK" -> "$languageLabel syntax valid"
                    result.startsWith("ERROR:") -> {
                        val location = result.removePrefix("ERROR:").split(":")
                        val line = location.getOrNull(0)?.toIntOrNull() ?: 1
                        val column = location.getOrNull(1)?.toIntOrNull() ?: 1
                        jsonErrorLine = line
                        jsonErrorColumn = column
                        "$languageLabel syntax error at Ln $line, Col $column — tap to navigate"
                    }
                    result == "INPUT_TOO_LARGE" -> "$languageLabel source is too large to validate"
                    result == "UNSUPPORTED_LANGUAGE" -> "No parser available for $languageLabel"
                    else -> "$languageLabel parser unavailable"
                }
                diagnosticView.text = jsonDiagnostic
                diagnosticView.visibility = View.VISIBLE
                diagnosticView.isClickable = jsonErrorLine != null
                diagnosticView.setTextColor(if (jsonErrorLine != null) context.getColor(R.color.nanoce_error) else context.getColor(R.color.nanoce_success))
                updateStatus()
                toast(jsonDiagnostic!!)
            }.onFailure { error ->
                jsonDiagnostic = "$languageLabel validation failed"
                diagnosticView.text = jsonDiagnostic
                diagnosticView.visibility = View.VISIBLE
                diagnosticView.isClickable = false
                updateStatus()
                toast("$languageLabel validation failed: ${error.message ?: "native parser error"}")
            }
    }

    private fun navigateToJsonError() {
        val line = jsonErrorLine ?: return
        val column = jsonErrorColumn ?: return
        val lines = editor.text.toString().split('\n')
        if (line !in 1..lines.size) return
        var offset = 0
        for (index in 0 until line - 1) {
            offset += lines[index].length + 1
        }
        offset += (column - 1).coerceIn(0, lines[line - 1].length)
        editor.requestFocus()
        editor.setSelection(offset.coerceIn(0, editor.text.length))
    }

    private fun updateLineNumbers() {
        val lineCount = editor.text.count { it == '\n' } + 1
        lineNumbers.text = (1..lineCount).joinToString("\n")
        val widestNumber = lineNumbers.paint.measureText(lineCount.toString()).toInt()
        val requiredWidth = widestNumber + lineNumbers.paddingLeft + lineNumbers.paddingRight
        lineNumbers.layoutParams = lineNumbers.layoutParams.apply {
            width = maxOf(dp(48), requiredWidth)
        }
        lineNumbers.requestLayout()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun updateStatus() {
        val document = activeDocument()
        if (document == null) {
            status.text = "No document open"
            return
        }
        val selection = editor.selectionStart.coerceAtLeast(0)
        val safeOffset = selection.coerceAtMost(editor.text.length)
        val beforeCursor = editor.text.subSequence(0, safeOffset)
        val line = beforeCursor.count { it == '\n' } + 1
        val lastNewline = beforeCursor.lastIndexOf("\n")
        val column = safeOffset - lastNewline
        val saveState = if (document.isDirty) "Modified — not saved" else "Saved"
        val language = EditorLanguage.fromFileName(document.name).label
        val diagnostic = jsonDiagnostic?.let { "  •  $it" }.orEmpty()
        status.text = "${language}  •  Ln ${line}, Col ${column}  •  ${saveState}${diagnostic}"
    }

    private fun toast(message: String) =
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val MAX_UNDO_STEPS = 200
        private const val SESSION_SNAPSHOT_MAGIC = 0x4E414E43
        private const val SESSION_SNAPSHOT_NAME = "nanoce-editor-session.bin"
    }
}
