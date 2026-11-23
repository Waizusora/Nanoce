package com.waizusora.nanoce

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessDeathSeedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun persistEditorSessionAndMoveAppToBackground() {
        val packageName = context.packageName
        val activityName = MainActivity::class.java.name
        executeShell("am start -W -n $packageName/$activityName")
        executeShell("input keyevent KEYCODE_HOME")
        awaitHomeResumed(packageName)

        // MainActivity.onPause() persists its current session. Seed the fixture
        // only after it has gone to the background so the empty session cannot
        // overwrite the process-death recovery payload.
        val snapshotFile = AtomicFile(File(context.filesDir, "nanoce-editor-session.bin"))
        snapshotFile.delete()
        val output = snapshotFile.startWrite()
        try {
            output.write(SessionSnapshotCodec.encode(processDeathSnapshot()))
            snapshotFile.finishWrite(output)
        } catch (error: Exception) {
            snapshotFile.failWrite(output)
            throw error
        }
    }

    private fun awaitHomeResumed(packageName: String) {
        repeat(50) {
            val activityDump = executeShell("dumpsys activity activities")
            val resumedLine = activityDump.lineSequence().firstOrNull {
                it.contains("mResumedActivity") || it.contains("topResumedActivity")
            }
            if (resumedLine != null && !resumedLine.contains(packageName)) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Home did not become the resumed activity before seeding snapshot")
    }

    private fun executeShell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
    }
}
