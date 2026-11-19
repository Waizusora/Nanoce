package com.waizusora.nanoce

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.text.Editable
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
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onCursorChanged()
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
    private val editor = CursorEditText(context) { updateStatus() }.apply {
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
    private val documents = linkedMapOf<Uri, EditorDocument>()
    private var activeUri: Uri? = null
    private var changingText = false

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF151922.toInt())
        addView(tabScroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (changingText) return
                val document = activeDocument() ?: return
                document.text = s?.toString().orEmpty()
                updateLineNumbers()
                updateStatus()
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
        changingText = true
        editor.setText(document.text)
        editor.setSelection(editor.text.length)
        updateLineNumbers()
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
        editor.hint = "Open a text file from the project explorer"
        status.text = "No document open"
    }

    private fun updateLineNumbers() {
        val lineCount = editor.text.count { it == '\\n' } + 1
        lineNumbers.text = (1..lineCount).joinToString("\\n")
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
        status.text = "${document.name}  •  Ln ${line}, Col ${column}  •  ${saveState}"
    }

    private fun toast(message: String) =
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
