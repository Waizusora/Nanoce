package com.waizusora.nanoce

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

internal data class StorageEntry(
    val name: String,
    val mimeType: String,
    val uri: Uri,
    val size: Long?
) {
    val isDirectory: Boolean
        get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}

/**
 * SAF-backed workspace access. Document URIs intentionally remain URIs:
 * they are not converted to filesystem paths.
 */
internal class StorageAccess(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val preferences = appContext.getSharedPreferences(
        "nanoce_storage", Context.MODE_PRIVATE
    )

    fun persistWorkspace(uri: Uri, resultFlags: Int) {
        val grants = resultFlags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        require(grants != 0) { "The selected folder did not grant read or write access" }
        val previous = workspaceUri()
        if (previous != null && previous != uri) {
            runCatching { resolver.releasePersistableUriPermission(previous, grants) }
        }
        resolver.takePersistableUriPermission(uri, grants)
        preferences.edit().putString(KEY_WORKSPACE_URI, uri.toString()).apply()
    }

    fun workspaceUri(): Uri? =
        preferences.getString(KEY_WORKSPACE_URI, null)?.let(Uri::parse)

    fun listChildren(directoryUri: Uri): List<StorageEntry> {
        val documentId = if (DocumentsContract.isDocumentUri(appContext, directoryUri)) {
            DocumentsContract.getDocumentId(directoryUri)
        } else {
            DocumentsContract.getTreeDocumentId(directoryUri)
        }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            directoryUri, documentId
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val entries = mutableListOf<StorageEntry>()

        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID
            )
            val nameColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            val mimeColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            val sizeColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_SIZE
            )

            while (cursor.moveToNext()) {
                val id = cursor.getString(idColumn)
                val name = cursor.getString(nameColumn) ?: "(unnamed)"
                val mime = cursor.getString(mimeColumn) ?: "application/octet-stream"
                val size = if (cursor.isNull(sizeColumn)) null
                    else cursor.getLong(sizeColumn)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(
                    directoryUri, id
                )
                entries += StorageEntry(name, mime, childUri, size)
            }
        }

        return entries.sortedWith(
            compareBy<StorageEntry> { !it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
    }

    fun createDirectory(parentUri: Uri, name: String): Uri? {
        requireValidName(name)
        return DocumentsContract.createDocument(
            resolver, asDocumentUri(parentUri),
            DocumentsContract.Document.MIME_TYPE_DIR, name
        )
    }

    fun createFile(parentUri: Uri, name: String, mimeType: String): Uri? {
        requireValidName(name)
        require(mimeType.isNotBlank())
        return DocumentsContract.createDocument(resolver, asDocumentUri(parentUri), mimeType, name)
    }

    fun openInput(uri: Uri) = resolver.openInputStream(asDocumentUri(uri))

    fun openOutput(uri: Uri) = resolver.openOutputStream(asDocumentUri(uri), "wt")

    fun delete(uri: Uri): Boolean =
        DocumentsContract.deleteDocument(resolver, asDocumentUri(uri))

    private fun asDocumentUri(uri: Uri): Uri =
        if (DocumentsContract.isDocumentUri(appContext, uri)) uri
        else DocumentsContract.buildDocumentUriUsingTree(
            uri, DocumentsContract.getTreeDocumentId(uri)
        )

    private fun requireValidName(name: String) {
        require(name.isNotBlank() && name != "." && name != "..")
        require('/' !in name && '\\' !in name)
    }

    companion object {
        private const val KEY_WORKSPACE_URI = "workspace_uri"
    }
}
