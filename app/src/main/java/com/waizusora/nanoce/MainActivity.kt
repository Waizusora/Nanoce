package com.waizusora.nanoce

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val nativeBridge by lazy { NativeBridge() }
    private lateinit var storageAccess: StorageAccess
    private lateinit var explorer: ProjectExplorerView
    private lateinit var editorWorkspace: EditorWorkspaceView
    private lateinit var workspaceBody: FrameLayout
    private lateinit var welcome: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storageAccess = StorageAccess(this)

        explorer = ProjectExplorerView(this, storageAccess).apply {
            showWorkspace(storageAccess.workspaceUri())
        }
        editorWorkspace = EditorWorkspaceView(this, storageAccess)
        explorer.onOpenTextFile = { uri, name, text ->
            editorWorkspace.openDocument(uri, name, text)
            explorer.visibility = View.GONE
            editorWorkspace.visibility = View.VISIBLE
            welcome.visibility = View.GONE
        }

        val title = TextView(this).apply {
            text = "Nanoce"
            textSize = 23f
            setTextColor(0xFFE6EAF0.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 8, 12, 4)
        }
        val topToolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(iconButton("☰", "Files") { toggleFiles() })
            addView(iconButton("▱", "Open project") { openWorkspacePicker() })
            addView(iconButton("+", "New file or folder") { createProjectItem() })
            addView(iconButton("⟳", "Refresh files") { explorer.refresh() })
        }

        workspaceBody = FrameLayout(this).apply {
            setBackgroundColor(0xFF151922.toInt())
            addView(editorWorkspace, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(explorer, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            explorer.visibility = View.GONE
        }
        welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 24, 28, 24)
            setBackgroundColor(0xFF151922.toInt())
            addView(TextView(this@MainActivity).apply {
                text = "Open or create a project"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(0xFFE6EAF0.toInt())
            })
            addView(TextView(this@MainActivity).apply {
                text = "Choose a project folder to start editing."
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(0xFF9AA4B2.toInt())
                setPadding(0, 10, 0, 0)
            })
        }
        workspaceBody.addView(welcome, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        if (storageAccess.workspaceUri() != null) welcome.visibility = View.GONE

        val bottomToolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(iconButton("▣", "Save") { editorWorkspace.saveActive() })
            addView(iconButton("⇧", "Save as") { launchSaveAs() })
            addView(iconButton("↶", "Undo") { editorWorkspace.undo() })
            addView(iconButton("↷", "Redo") { editorWorkspace.redo() })
            addView(iconButton("ⓘ", "About") { showAbout() })
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF151922.toInt())
            addView(title)
            addView(topToolbar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 48
            ))
            addView(separator())
        }
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF151922.toInt())
            addView(separator())
            addView(bottomToolbar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 52
            ))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF151922.toInt())
            addView(header)
            addView(workspaceBody, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            addView(footer)
        }
        setContentView(content)
    }

    private fun iconButton(symbol: String, label: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = symbol
            contentDescription = label
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(0xFFD6DEE9.toInt())
            setPadding(8, 4, 8, 4)
            minWidth = 52
            minHeight = 48
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            background = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                .getDrawable(0)
                .also { obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).recycle() }
        }

    private fun separator(): View = View(this).apply {
        setBackgroundColor(0xFF303846.toInt())
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1
        )
    }

    private fun toggleFiles() {
        if (explorer.visibility == View.VISIBLE) {
            explorer.visibility = View.GONE
            if (editorWorkspace.visibility != View.VISIBLE) welcome.visibility = View.VISIBLE
        } else {
            welcome.visibility = View.GONE
            explorer.visibility = View.VISIBLE
            explorer.refresh()
        }
    }

    private fun createProjectItem() {
        if (storageAccess.workspaceUri() == null) {
            openWorkspacePicker()
            return
        }
        explorer.showCreateActions()
    }

    private fun openWorkspacePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_WORKSPACE)
    }

    private fun launchSaveAs() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "untitled.txt")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_SAVE_AS)
    }

    private fun showAbout() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 20, 24, 12)
            addView(TextView(this@MainActivity).apply {
                text = "Nanoce"
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(0xFFE6EAF0.toInt())
            })
            addView(TextView(this@MainActivity).apply {
                text = "Nano Code Editor\nnanoce-dev\nNative core: ${nativeBridge.nativeVersion()}"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(0xFF9AA4B2.toInt())
                setPadding(0, 8, 0, 18)
            })
            addView(separator())
            addView(TextView(this@MainActivity).apply {
                text = "Open Source Notices"
                textSize = 16f
                setTextColor(0xFFE6EAF0.toInt())
                setPadding(0, 14, 0, 8)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Nanoce original code is licensed under the MIT License.\n\nTree-sitter is distributed under the MIT License. Grammar sources retain their respective upstream licenses. See the bundled source and license files for complete third-party notices."
                textSize = 12f
                setTextColor(0xFFB8C2D0.toInt())
            })
        }
        AlertDialog.Builder(this)
            .setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onDestroy() {
        editorWorkspace.dispose()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        editorWorkspace.confirmDiscardAndRun {
            super.onBackPressed()
        }
    }

    @Deprecated("Uses platform document pickers for API 23 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        when (requestCode) {
            REQUEST_WORKSPACE -> {
                try {
                    storageAccess.persistWorkspace(uri, data.flags)
                    explorer.showWorkspace(uri)
                    welcome.visibility = View.GONE
                    editorWorkspace.visibility = View.VISIBLE
                    explorer.visibility = View.VISIBLE
                } catch (error: Exception) {
                    showMessage("Unable to access project: ${error.message ?: "unknown error"}")
                }
            }
            REQUEST_SAVE_AS -> {
                val name = data?.data?.lastPathSegment?.substringAfterLast('/') ?: "untitled.txt"
                editorWorkspace.saveAs(uri, name)
            }
        }
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_WORKSPACE = 4101
        private const val REQUEST_SAVE_AS = 4102
    }
}
