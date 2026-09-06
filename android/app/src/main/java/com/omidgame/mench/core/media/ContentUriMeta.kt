package com.omidgame.mench.core.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

data class ContentUriMeta(val mimeType: String, val displayName: String)

/**
 * Resolves the mime type and display name for a Uri returned by a system
 * file/photo picker. Pure Android platform lookup — belongs in the UI/
 * platform layer, not pushed into the ViewModel or domain layer, since
 * ContentResolver is an Android framework concept the domain layer has no
 * business knowing about.
 */
fun resolveContentUriMeta(context: Context, uri: Uri): ContentUriMeta {
    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

    var displayName = uri.lastPathSegment ?: "file"
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)?.let { displayName = it }
        }
    }

    return ContentUriMeta(mimeType, displayName)
}
