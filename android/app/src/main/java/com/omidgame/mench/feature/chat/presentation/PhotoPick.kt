package com.omidgame.mench.feature.chat.presentation

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import com.omidgame.mench.feature.chat.domain.PickedPhoto

/** Phase 3 — shared photo picking for the album, letters and stories. */

/** The MIME type is looked up from the system; images only. Falls back to JPEG for a provider that does not say. */
internal fun Context.toPickedPhoto(uri: Uri): PickedPhoto =
    PickedPhoto(
        uri = uri.toString(),
        mimeType = contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg",
        filename = uri.lastPathSegment?.substringAfterLast('/'),
    )

/** Returns a function that opens the system image picker; [onPicked] receives the chosen photo. */
@Composable
fun rememberPhotoPicker(context: Context, onPicked: (PickedPhoto) -> Unit): () -> Unit {
    val latest = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) latest.value(context.toPickedPhoto(uri))
    }
    return { launcher.launch("image/*") }
}
