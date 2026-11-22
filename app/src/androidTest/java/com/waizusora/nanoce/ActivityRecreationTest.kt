package com.waizusora.nanoce

import android.content.Context
import android.util.AtomicFile
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityRecreationTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun recreationRestoresOpenTabsUnsavedBufferAndSelection() {
        val snapshotFile = AtomicFile(File(context.filesDir, "nanoce-editor-session.bin"))
        val activeUri = "content://provider/document/main"
        val snapshot = SessionSnapshot(
            activeUri = activeUri,
            selectionStart = 4,
            selectionEnd = 8,
            documents = listOf(
                SessionSnapshotDocument(
                    uri = activeUri,
                    name = "main.rs",
                    text = "fn main() { let value = 42; }",
                    savedText = "fn main() {}"
                ),
                SessionSnapshotDocument(
                    uri = "content://provider/document/notes",
                    name = "notes.txt",
                    text = "workspace notes",
                    savedText = "workspace notes"
                )
            )
        )

        snapshotFile.delete()
        val output = snapshotFile.startWrite()
        output.write(SessionSnapshotCodec.encode(snapshot))
        snapshotFile.finishWrite(output)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEditorState(activity, snapshot)
                assertTrue(findTextViews(activity.window.decorView).any { it.text == "notes.txt" })
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                assertEditorState(activity, snapshot)
                assertTrue(findTextViews(activity.window.decorView).any { it.text == "notes.txt" })
            }
        }
    }

    private fun assertEditorState(activity: MainActivity, snapshot: SessionSnapshot) {
        val editor = findEditTexts(activity.window.decorView).singleOrNull()
        assertNotNull("Expected exactly one editor view", editor)
        assertEquals(snapshot.documents.first().text, editor!!.text.toString())
        assertEquals(snapshot.selectionStart, editor.selectionStart)
        assertEquals(snapshot.selectionEnd, editor.selectionEnd)
    }

    private fun findEditTexts(view: View): List<EditText> {
        if (view is EditText) return listOf(view)
        if (view !is ViewGroup) return emptyList()
        return (0 until view.childCount).flatMap { findEditTexts(view.getChildAt(it)) }
    }

    private fun findTextViews(view: View): List<TextView> {
        if (view is TextView) return listOf(view)
        if (view !is ViewGroup) return emptyList()
        return (0 until view.childCount).flatMap { findTextViews(view.getChildAt(it)) }
    }
}
