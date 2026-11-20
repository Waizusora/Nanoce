package com.waizusora.nanoce

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.WindowInsets
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
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
    private var createAfterWorkspacePicker = false

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
            setTextColor(getColor(R.color.nanoce_text))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 8, 12, 4)
        }
        val topToolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addToolbarAction(this, R.drawable.ic_toolbar_files, "Files") { toggleFiles() }
            addToolbarAction(this, R.drawable.ic_toolbar_open, "Open") { openWorkspacePicker() }
            addToolbarAction(this, R.drawable.ic_toolbar_new, "New") { createProjectItem() }
            addToolbarAction(this, R.drawable.ic_toolbar_refresh, "Refresh") { explorer.refresh() }
        }

        workspaceBody = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.nanoce_background))
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
            setBackgroundColor(getColor(R.color.nanoce_background))
            addView(TextView(this@MainActivity).apply {
                text = "Open or create a project"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.nanoce_text))
            })
            addView(TextView(this@MainActivity).apply {
                text = "Choose a project folder to start editing."
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.nanoce_muted))
                setPadding(0, 10, 0, 0)
            })
        }
        workspaceBody.addView(welcome, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val bottomToolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addToolbarAction(this, R.drawable.ic_toolbar_save, "Save") { editorWorkspace.saveActive() }
            addToolbarAction(this, R.drawable.ic_toolbar_save_as, "Save As") { launchSaveAs() }
            addToolbarAction(this, R.drawable.ic_toolbar_undo, "Undo") { editorWorkspace.undo() }
            addToolbarAction(this, R.drawable.ic_toolbar_redo, "Redo") { editorWorkspace.redo() }
            addToolbarAction(this, R.drawable.ic_toolbar_about, "About") { showAbout() }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.nanoce_background))
            addView(title, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
            ))
            addView(topToolbar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60)
            ))
            addView(separator())
        }
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.nanoce_background))
            addView(separator())
            addView(bottomToolbar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(70)
            ))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.nanoce_background))
            addView(header)
            addView(workspaceBody, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            addView(footer)
        }
        content.setOnApplyWindowInsetsListener { view, insets ->
            val (topInset, bottomInset) =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val systemBars = insets.getInsets(
                        WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                    )
                    systemBars.top to systemBars.bottom
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetTop to insets.systemWindowInsetBottom
                }
            view.setPadding(view.paddingLeft, topInset, view.paddingRight, bottomInset)
            insets
        }
        setContentView(content)
        window.statusBarColor = getColor(R.color.nanoce_background)
        window.navigationBarColor = getColor(R.color.nanoce_background)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        window.decorView.systemUiVisibility = 0
        content.requestApplyInsets()
        if (savedInstanceState?.getBoolean(STATE_EDITOR_SESSION) == true &&
            editorWorkspace.restoreSessionSnapshot()
        ) {
            welcome.visibility = View.GONE
            explorer.visibility = View.GONE
            editorWorkspace.visibility = View.VISIBLE
        }
    }

    private fun addToolbarAction(
        toolbar: LinearLayout,
        iconRes: Int,
        label: String,
        action: () -> Unit
    ) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            contentDescription = label
            isClickable = true
            isFocusable = true
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            setPadding(dp(2), dp(3), dp(2), dp(3))
            setBackgroundResource(R.drawable.toolbar_action_background)
            addView(ImageView(this@MainActivity).apply {
                setImageResource(iconRes)
                imageTintList = android.content.res.ColorStateList.valueOf(
                    getColor(R.color.nanoce_text)
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = null
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(getColor(R.color.nanoce_muted))
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) })
            setOnClickListener { action() }
        }
        toolbar.addView(item, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
        ))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun separator(): View = View(this).apply {
        setBackgroundColor(getColor(R.color.nanoce_divider))
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
            createAfterWorkspacePicker = true
            openWorkspacePicker()
            return
        }
        explorer.showCreateActions()
    }

    private fun openWorkspacePicker() {
        editorWorkspace.confirmCloseAllAndRun { launchWorkspacePicker() }
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
                setTextColor(getColor(R.color.nanoce_text))
            })
            addView(TextView(this@MainActivity).apply {
                text = "Nano Code Editor\nnanoce-dev\nNative core: ${nativeBridge.nativeVersion()}"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.nanoce_muted))
                setPadding(0, 8, 0, 18)
            })
            addView(separator())
            addView(TextView(this@MainActivity).apply {
                text = "Open Source Notices"
                textSize = 16f
                setTextColor(getColor(R.color.nanoce_text))
                setPadding(0, 14, 0, 8)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Nanoce original code: MIT License.\n\nTree-sitter runtime 0.25.10: MIT License.\nTree-sitter JSON 0.24.8: MIT License.\nTree-sitter C 0.24.2: MIT License.\nTree-sitter C++ 0.23.4: MIT License.\nTree-sitter Rust 0.24.2: MIT License.\nTree-sitter Python 0.25.0: MIT License.\nTree-sitter Kotlin (fwcd, pinned upstream revision): MIT License.\n\nThird-party components remain subject to their upstream license terms. Full license texts are available from their respective upstream repositories."
                textSize = 12f
                setTextColor(getColor(R.color.nanoce_text))
            })
        }
        AlertDialog.Builder(this)
            .setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_EDITOR_SESSION, editorWorkspace.saveSessionSnapshot())
    }

    override fun onDestroy() {
        if (isFinishing) editorWorkspace.clearSessionSnapshot()
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
        if (resultCode != RESULT_OK) {
            if (requestCode == REQUEST_WORKSPACE) createAfterWorkspacePicker = false
            return
        }
        val uri: Uri = data?.data ?: return
        when (requestCode) {
            REQUEST_WORKSPACE -> {
                try {
                    storageAccess.persistWorkspace(uri, data.flags)
                    explorer.showWorkspace(uri)
                    welcome.visibility = View.GONE
                    editorWorkspace.visibility = View.VISIBLE
                    explorer.visibility = View.VISIBLE
                    if (createAfterWorkspacePicker) {
                        createAfterWorkspacePicker = false
                        explorer.showCreateActions()
                    }
                } catch (error: Exception) {
                    createAfterWorkspacePicker = false
                    showMessage("Unable to access project: ${error.message ?: "unknown error"}")
                }
            }
            REQUEST_SAVE_AS -> {
                val name = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?: "untitled.txt"
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
        private const val STATE_EDITOR_SESSION = "nanoce.editor.session.saved"
    }
}
