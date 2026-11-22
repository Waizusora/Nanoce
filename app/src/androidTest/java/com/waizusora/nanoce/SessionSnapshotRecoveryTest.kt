package com.waizusora.nanoce

import android.content.Context
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionSnapshotRecoveryTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun atomicFileRestoresLastCommittedSnapshotAfterInterruptedWrite() {
        val directory = File(context.cacheDir, "snapshot-recovery").apply { mkdirs() }
        val atomicFile = AtomicFile(File(directory, "session.bin"))
        val committed = "committed-snapshot".toByteArray(Charsets.UTF_8)

        try {
            atomicFile.delete()
            val initialWrite = atomicFile.startWrite()
            initialWrite.write(committed)
            atomicFile.finishWrite(initialWrite)

            val interruptedWrite = atomicFile.startWrite()
            interruptedWrite.write("partial-snapshot".toByteArray(Charsets.UTF_8))
            atomicFile.failWrite(interruptedWrite)

            atomicFile.openRead().use { input ->
                assertArrayEquals(committed, input.readBytes())
            }
        } finally {
            atomicFile.delete()
            directory.delete()
        }
    }

    @Test
    fun encodedSessionSurvivesAtomicFileCloseAndReopen() {
        val directory = File(context.cacheDir, "snapshot-round-trip").apply { mkdirs() }
        val atomicFile = AtomicFile(File(directory, "session.bin"))
        val expected = SessionSnapshot(
            activeUri = "content://provider/document/active",
            selectionStart = 2,
            selectionEnd = 5,
            documents = listOf(
                SessionSnapshotDocument(
                    "content://provider/document/active",
                    "main.rs",
                    "fn main() {}",
                    "fn main()"
                ),
                SessionSnapshotDocument(
                    "content://provider/document/second",
                    "notes.txt",
                    "unsaved text",
                    "saved text"
                )
            )
        )

        try {
            atomicFile.delete()
            val output = atomicFile.startWrite()
            output.write(SessionSnapshotCodec.encode(expected))
            atomicFile.finishWrite(output)

            val restored = atomicFile.openRead().use { input ->
                SessionSnapshotCodec.decode(input.readBytes())
            }
            assertEquals(expected, restored)
        } finally {
            atomicFile.delete()
            directory.delete()
        }
    }
}
