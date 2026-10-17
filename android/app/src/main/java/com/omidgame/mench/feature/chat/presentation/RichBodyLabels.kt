package com.omidgame.mench.feature.chat.presentation

import com.omidgame.mench.R

/** String resource for the one-word label of a rich message (chat list, reply quote, pinned banner). */
fun richKindLabel(kind: RichBody.Kind): Int = when (kind) {
    RichBody.Kind.STICKER -> R.string.preview_sticker
    RichBody.Kind.GIF -> R.string.preview_gif
    RichBody.Kind.POLL -> R.string.preview_poll
    RichBody.Kind.LOCATION -> R.string.preview_location
    RichBody.Kind.CONTACT -> R.string.preview_contact
    RichBody.Kind.SURPRISE -> R.string.preview_surprise
}
