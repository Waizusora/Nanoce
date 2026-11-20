package com.waizusora.nanoce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionSnapshotCodecTest {
    @Test fun roundTripsMultipleDocumentsAndSelection() {
        val expected = SessionSnapshot(
            activeUri = "content://provider/document/one",
            selectionStart = 4,
            selectionEnd = 9,
            documents = listOf(
                SessionSnapshotDocument("content://provider/document/one", "one.rs", "fn main() {}", "fn main()"),
                SessionSnapshotDocument("content://provider/document/two", "two.json", "{\n  \"ok\": true\n}", "{\n}")
            )
        )
        assertEquals(expected, SessionSnapshotCodec.decode(SessionSnapshotCodec.encode(expected)))
    }

    @Test fun rejectsUnknownVersion() {
        val encoded = SessionSnapshotCodec.encode(
            SessionSnapshot(null, 0, 0, emptyList())
        )
        encoded[7] = 99
        assertThrows(IllegalArgumentException::class.java) {
            SessionSnapshotCodec.decode(encoded)
        }
    }

    @Test fun rejectsTruncatedPayload() {
        val encoded = SessionSnapshotCodec.encode(
            SessionSnapshot("doc", 0, 0, listOf(SessionSnapshotDocument("doc", "a.txt", "hello", "")))
        )
        assertThrows(Exception::class.java) {
            SessionSnapshotCodec.decode(encoded.copyOf(encoded.size - 2))
        }
    }

    @Test fun rejectsTrailingBytes() {
        val encoded = SessionSnapshotCodec.encode(SessionSnapshot(null, 0, 0, emptyList())) + byteArrayOf(1)
        assertThrows(IllegalArgumentException::class.java) {
            SessionSnapshotCodec.decode(encoded)
        }
    }

    @Test fun rejectsDuplicateDocumentUris() {
        val duplicate = SessionSnapshot(
            null, 0, 0, listOf(
                SessionSnapshotDocument("same", "a.txt", "", ""),
                SessionSnapshotDocument("same", "b.txt", "", "")
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            SessionSnapshotCodec.encode(duplicate)
        }
    }

    @Test fun rejectsSelectionOutsideActiveDocument() {
        val invalid = SessionSnapshot(
            "doc", 8, 8,
            listOf(SessionSnapshotDocument("doc", "a.txt", "short", "short"))
        )
        assertThrows(IllegalArgumentException::class.java) {
            SessionSnapshotCodec.encode(invalid)
        }
    }
}
