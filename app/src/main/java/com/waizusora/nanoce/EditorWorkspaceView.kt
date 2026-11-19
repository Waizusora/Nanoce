package com.waizusora.nanoce

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
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
    private val onCursorChanged: () -> Unit
) : EditText(context) {
    private val activeLinePaint = android.graphics.Paint().apply {
        color = 0x182F81F7
        style = android.graphics.Paint.Style.FILL
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        invalidate()
        onCursorChanged()
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
        setTextColor(0xFFE6EAF0.toInt())
        setHintTextColor(0xFF7F8A99.toInt())
        setPadding(16, 14, 16, 24)
        gravity = Gravity.TOP or Gravity.START
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setHorizontallyScrolling(true)
        setBackgroundColor(0xFF151922.toInt())
    }
    private val lineNumbers = TextView(context).apply {
        textSize = 14f
        typeface = android.graphics.Typeface.MONOSPACE
        gravity = Gravity.TOP or Gravity.END
        setTextColor(0xFF657184.toInt())
        setPadding(8, 14, 10, 24)
        setBackgroundColor(0xFF11161E.toInt())
        isSingleLine = false
        text = "1"
    }
    private val status = TextView(context).apply {
        textSize = 11f
        setTextColor(0xFF9AA4B2.toInt())
        setPadding(12, 6, 12, 6)
    }
    private val checkJson = Button(context).apply {
        text = "Check JSON"
        textSize = 11f
        isAllCaps = false
        setOnClickListener { validateActiveJson() }
    }
    private val nativeBridge by lazy { NativeBridge() }
    private var jsonDiagnostic: String? = null
    private val documents = linkedMapOf<Uri, EditorDocument>()
    private var activeUri: Uri? = null
    private var changingText = false

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF151922.toInt())
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
        addView(ScrollView(context).apply {
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                addView(lineNumbers, LinearLayout.LayoutParams(
                    48, ViewGroup.LayoutParams.WRAP_CONTENT
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
                document.text = s?.toString().orEmpty()
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
                documents.clear()
                activeUri = null
                renderTabs()
                showEmptyState()
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
        documents.clear()
        activeUri = null
        renderTabs()
        showEmptyState()
    }

    private fun activate(uri: Uri) {
        val document = documents[uri] ?: return
        activeUri = uri
        jsonDiagnostic = null
        checkJson.visibility = if (document.name.substringAfterLast('.', "").lowercase() == "json") View.VISIBLE else View.GONE
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
                setBackgroundColor(if (selected) 0xFF263548.toInt() else 0xFF1B222D.toInt())
            }
            val label = Button(context).apply {
                text = document.name + if (document.isDirty) " •" else ""
                textSize = 11f
                isAllCaps = false
                setTextColor(if (selected) 0xFF80D8FF.toInt() else 0xFFCBD5E1.toInt())
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener { activate(document.uri) }
                setOnLongClickListener {
                    requestClose(document.uri)
                    true
                }
            }
            val close = TextView(context).apply {
                text = "×"
                textSize = 18f
                setTextColor(0xFF9AA4B2.toInt())
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

    private val bracketHighlightSpans = mutableListOf<android.text.style.BackgroundColorSpan>()

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
            val span = android.text.style.BackgroundColorSpan(0xFF665C32.toInt())
            editable.setSpan(span, index, index + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            bracketHighlightSpans.add(span)
        }
    }

    private fun updateSyntaxHighlighting() {
        val language = activeDocument()?.let { EditorLanguage.fromFileName(it.name) }
            ?: EditorLanguage.PLAIN
        SyntaxHighlighter.apply(editor.text, language)
    }

    private fun validateActiveJson() {
        val document = activeDocument() ?: return
        if (document.name.substringAfterLast('.', "").lowercase() != "json") {
            toast("JSON validation is available for .json files")
            return
        }
        runCatching {
            nativeBridge.nativeValidateJson(editor.text.toString())
        }.onSuccess { valid ->
            jsonDiagnostic = if (valid) "JSON valid" else "JSON syntax error"
            updateStatus()
            toast(jsonDiagnostic!!)
        }.onFailure { error ->
            jsonDiagnostic = "JSON check failed"
            updateStatus()
            toast("JSON check failed: ${error.message ?: "native parser error"}")
        }
    }

    private fun updateLineNumbers() {
        val lineCount = editor.text.count { it == '\n' } + 1
        lineNumbers.text = (1..lineCount).joinToString("\n")
    }

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
}
