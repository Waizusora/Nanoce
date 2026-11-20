package com.waizusora.nanoce

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal data class SessionSnapshotDocument(
    val uri: String,
    val name: String,
    val text: String,
    val savedText: String
)

internal data class SessionSnapshot(
    val activeUri: String?,
    val selectionStart: Int,
    val selectionEnd: Int,
    val documents: List<SessionSnapshotDocument>
)

/** Versioned, bounded codec for Activity editor-session snapshots. */
internal object SessionSnapshotCodec {
    private const val MAGIC = 0x4E414E43
    private const val VERSION = 2
    const val MAX_DOCUMENTS = 64
    const val MAX_DOCUMENT_BYTES = 4 * 1024 * 1024
    const val MAX_TOTAL_BYTES = 16 * 1024 * 1024
    private const val MAX_URI_CHARS = 4096
    private const val MAX_NAME_CHARS = 1024

    fun encode(snapshot: SessionSnapshot): ByteArray {
        require(snapshot.documents.size <= MAX_DOCUMENTS)
        require(snapshot.selectionStart >= 0 && snapshot.selectionEnd >= 0)
        val seen = mutableSetOf<String>()
        var totalBytes = 0L
        snapshot.documents.forEach { document ->
            require(document.uri.isNotBlank() && document.name.isNotBlank())
            require(document.uri.length <= MAX_URI_CHARS && document.name.length <= MAX_NAME_CHARS)
            require(seen.add(document.uri)) { "Duplicate document URI" }
            val textSize = document.text.toByteArray(Charsets.UTF_8).size
            val savedSize = document.savedText.toByteArray(Charsets.UTF_8).size
            require(textSize <= MAX_DOCUMENT_BYTES && savedSize <= MAX_DOCUMENT_BYTES)
            totalBytes += textSize.toLong() + savedSize
            require(totalBytes <= MAX_TOTAL_BYTES)
        }
        require(snapshot.activeUri == null || snapshot.activeUri in seen)
        val active = snapshot.activeUri?.let { uri -> snapshot.documents.first { it.uri == uri } }
        val activeLength = active?.text?.length ?: 0
        require(snapshot.selectionStart <= activeLength && snapshot.selectionEnd <= activeLength)

        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeInt(snapshot.documents.size)
            output.writeUTF(snapshot.activeUri.orEmpty())
            output.writeInt(snapshot.selectionStart)
            output.writeInt(snapshot.selectionEnd)
            snapshot.documents.forEach { document ->
                output.writeUTF(document.uri)
                output.writeUTF(document.name)
                val text = document.text.toByteArray(Charsets.UTF_8)
                val saved = document.savedText.toByteArray(Charsets.UTF_8)
                output.writeInt(text.size)
                output.write(text)
                output.writeInt(saved.size)
                output.write(saved)
            }
        }
        return bytes.toByteArray()
    }

    private fun decodeUtf8(bytes: ByteArray): String =
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    fun decode(bytes: ByteArray): SessionSnapshot {
        require(bytes.size <= MAX_TOTAL_BYTES + 1024 * 1024) { "Snapshot exceeds size limit" }
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Invalid snapshot magic" }
            require(input.readInt() == VERSION) { "Unsupported snapshot version" }
            val count = input.readInt()
            require(count in 0..MAX_DOCUMENTS) { "Invalid document count" }
            val activeUri = input.readUTF().takeIf(String::isNotEmpty)
            val selectionStart = input.readInt()
            val selectionEnd = input.readInt()
            require(selectionStart >= 0 && selectionEnd >= 0) { "Invalid selection" }
            val documents = ArrayList<SessionSnapshotDocument>(count)
            val seen = mutableSetOf<String>()
            var totalBytes = 0L
            repeat(count) {
                val uri = input.readUTF()
                val name = input.readUTF()
                require(uri.isNotBlank() && name.isNotBlank()) { "Invalid document identity" }
                require(uri.length <= MAX_URI_CHARS && name.length <= MAX_NAME_CHARS) {
                    "Document identity exceeds size limit"
                }
                require(seen.add(uri)) { "Duplicate document URI" }
                val textSize = input.readInt()
                require(textSize in 0..MAX_DOCUMENT_BYTES) { "Invalid document buffer size" }
                totalBytes += textSize
                require(totalBytes <= MAX_TOTAL_BYTES)
                val textBytes = ByteArray(textSize)
                input.readFully(textBytes)
                val savedSize = input.readInt()
                require(savedSize in 0..MAX_DOCUMENT_BYTES) { "Invalid saved buffer size" }
                totalBytes += savedSize
                require(totalBytes <= MAX_TOTAL_BYTES)
                val savedBytes = ByteArray(savedSize)
                input.readFully(savedBytes)
                documents += SessionSnapshotDocument(
                    uri, name, decodeUtf8(textBytes), decodeUtf8(savedBytes)
                )
            }
            require(input.read() == -1) { "Unexpected trailing snapshot data" }
            require(activeUri == null || activeUri in seen) { "Active document is missing" }
            val activeLength = activeUri?.let { uri -> documents.first { it.uri == uri }.text.length } ?: 0
            require(selectionStart <= activeLength && selectionEnd <= activeLength) {
                "Selection is outside active document"
            }
            return SessionSnapshot(activeUri, selectionStart, selectionEnd, documents)
        }
    }
}
