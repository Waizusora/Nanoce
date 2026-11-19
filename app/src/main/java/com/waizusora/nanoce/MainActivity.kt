package com.waizusora.nanoce

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val nativeBridge by lazy { NativeBridge() }
    private lateinit var storageAccess: StorageAccess
    private lateinit var workspaceStatus: TextView
    private lateinit var explorer: ProjectExplorerView
    private lateinit var editorWorkspace: EditorWorkspaceView
    private lateinit var workspaceBody: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storageAccess = StorageAccess(this)

        val title = TextView(this).apply {
            text = "Nanoce"
            textSize = 26f
            setTextColor(0xFFE6EAF0.toInt())
        }
        val subtitle = TextView(this).apply {
            text = "Nano Code Editor"
            textSize = 13f
            setTextColor(0xFF9AA4B2.toInt())
        }
        val version = TextView(this).apply {
            text = "Native core: ${nativeBridge.nativeVersion()}"
            textSize = 11f
            setTextColor(0xFF7F8A99.toInt())
        }
        workspaceStatus = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFB8C2D0.toInt())
            text = workspaceDescription(storageAccess.workspaceUri())
        }
        explorer = ProjectExplorerView(this, storageAccess).apply {
            showWorkspace(storageAccess.workspaceUri())
        }
        editorWorkspace = EditorWorkspaceView(this, storageAccess)
        explorer.onOpenTextFile = { uri, name, text ->
            editorWorkspace.openDocument(uri, name, text)
            showEditor()
        }

        val chooseWorkspace = Button(this).apply {
            text = "Open project"
            setOnClickListener { openWorkspacePicker() }
        }
        val createItem = Button(this).apply {
            text = "New"
            setOnClickListener { explorer.showCreateActions() }
        }
        val refresh = Button(this).apply {
            text = "Refresh"
            setOnClickListener { explorer.refresh() }
        }
        val files = Button(this).apply {
            text = "Files"
            setOnClickListener { showExplorer() }
        }
        val save = Button(this).apply {
            text = "Save"
            setOnClickListener { editorWorkspace.saveActive() }
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(chooseWorkspace)
            addView(createItem)
            addView(refresh)
            addView(files)
            addView(save)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 8)
            setBackgroundColor(0xFF151922.toInt())
            addView(title)
            addView(subtitle)
            addView(version)
            addView(actions, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 })
            addView(workspaceStatus, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 })
        }

        workspaceBody = FrameLayout(this).apply {
            addView(explorer, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(editorWorkspace, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            editorWorkspace.visibility = View.GONE
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF151922.toInt())
            addView(header)
            addView(workspaceBody, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ))
        }
        setContentView(content)
    }

    private fun showEditor() {
        explorer.visibility = View.GONE
        editorWorkspace.visibility = View.VISIBLE
    }

    private fun showExplorer() {
        editorWorkspace.confirmDiscardAndRun {
            editorWorkspace.visibility = View.GONE
            explorer.visibility = View.VISIBLE
        }
    }

    private fun openWorkspacePicker() {
        editorWorkspace.confirmDiscardAndRun {
            launchWorkspacePicker()
        }
    }

    private fun launchWorkspacePicker() {
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

    @Deprecated("Uses the platform document-tree picker for API 23 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_WORKSPACE || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            storageAccess.persistWorkspace(uri, data.flags)
            workspaceStatus.text = workspaceDescription(uri)
            explorer.showWorkspace(uri)
        } catch (error: Exception) {
            workspaceStatus.text = "Unable to access folder: ${error.message ?: "unknown error"}"
        }
    }

    private fun workspaceDescription(uri: Uri?): String =
        if (uri == null) "No project folder selected"
        else "Workspace: ${uri.lastPathSegment ?: uri}"

    companion object {
        private const val REQUEST_WORKSPACE = 4101
    }
}
