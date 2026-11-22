package com.waizusora.nanoce

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessDeathSeedTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun persistEditorSessionAndMoveAppToBackground() {
        val snapshotFile = AtomicFile(File(context.filesDir, "nanoce-editor-session.bin"))
        snapshotFile.delete()
        val output = snapshotFile.startWrite()
        output.write(SessionSnapshotCodec.encode(processDeathSnapshot()))
        snapshotFile.finishWrite(output)

        val packageName = context.packageName
        val activityName = MainActivity::class.java.name
        executeShell("am start -W -n $packageName/$activityName")
        executeShell("input keyevent KEYCODE_HOME")
    }

    private fun executeShell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
    }
}
