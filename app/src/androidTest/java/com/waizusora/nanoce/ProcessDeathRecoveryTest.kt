package com.waizusora.nanoce

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessDeathRecoveryTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun forceStoppedProcessRestoresPersistedEditorSessionOnNextLaunch() {
        val snapshotFile = AtomicFile(File(context.filesDir, "nanoce-editor-session.bin"))
        val activeUri = "content://provider/document/process-recovery"
        val snapshot = SessionSnapshot(
            activeUri = activeUri,
            selectionStart = 3,
            selectionEnd = 7,
            documents = listOf(
                SessionSnapshotDocument(
                    uri = activeUri,
                    name = "process.rs",
                    text = "fn main() { let recovered = true; }",
                    savedText = "fn main() {}"
                ),
                SessionSnapshotDocument(
                    uri = "content://provider/document/process-notes",
                    name = "recovery-notes.txt",
                    text = "persisted across process death",
                    savedText = "persisted across process death"
                )
            )
        )

        snapshotFile.delete()
        val output = snapshotFile.startWrite()
        output.write(SessionSnapshotCodec.encode(snapshot))
        snapshotFile.finishWrite(output)

        val packageName = context.packageName
        val activityName = MainActivity::class.java.name
        try {
            executeShell("am start -W -n $packageName/$activityName")
            executeShell("input keyevent KEYCODE_HOME")
            executeShell("am force-stop $packageName")

            val processId = executeShell("pidof $packageName").trim()
            assertTrue("Expected force-stop to terminate the app process", processId.isEmpty())

            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ).use { scenario ->
                scenario.onActivity { activity ->
                    val editor = findEditTexts(activity.window.decorView).singleOrNull()
                    assertNotNull("Expected restored editor view", editor)
                    assertEquals(snapshot.documents.first().text, editor!!.text.toString())
                    assertEquals(snapshot.selectionStart, editor.selectionStart)
                    assertEquals(snapshot.selectionEnd, editor.selectionEnd)
                    assertTrue(
                        findTextViews(activity.window.decorView).any {
                            it.text == "recovery-notes.txt"
                        }
                    )
                }
            }
        } finally {
            snapshotFile.delete()
        }
    }

    private fun executeShell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
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
