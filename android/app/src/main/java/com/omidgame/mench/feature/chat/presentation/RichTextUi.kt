package com.omidgame.mench.feature.chat.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/** Lets a tapped #hashtag start an in-chat search; provided by ChatScreen. */
val LocalHashtagSearch = staticCompositionLocalOf<(String) -> Unit> { {} }

private const val TAG_LINK = "link"
private const val TAG_SPOILER = "spoiler"

/**
 * Message text with Phase 2A formatting: bold / italic / strike / code / spoiler, and tappable
 * links, e-mail addresses, phone numbers (opens the dialer), #hashtags (in-chat search) and
 * @mentions (styled). Plain text without any of these renders exactly like a normal Text.
 */
@Composable
fun RichMessageText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    // Settings Phase 2: message text size and line spacing (separate from the global font scale).
    val chatSettings = rememberChatSettings()
    val style = remember(style, chatSettings.messageTextScale, chatSettings.lineSpacing) {
        val baseSize = if (style.fontSize.isSpecified) style.fontSize else 16.sp
        val baseLine = if (style.lineHeight.isSpecified) style.lineHeight else baseSize * 1.25f
        style.copy(fontSize = baseSize * chatSettings.messageTextScale, lineHeight = baseLine * chatSettings.messageTextScale * chatSettings.lineSpacing)
    }
    val parsed = remember(text) { RichText.parse(text) }
    val context = LocalContext.current
    val onHashtag = LocalHashtagSearch.current
    val contentColor = LocalContentColor.current
    val linkColor = MaterialTheme.colorScheme.tertiary
    val codeBackground = contentColor.copy(alpha = 0.14f)
    var revealed by remember(text) { mutableStateOf(false) }

    val annotated = remember(parsed, revealed, contentColor, linkColor) {
        AnnotatedString.Builder(parsed.plain).apply {
            parsed.spans.forEach { span ->
                val spanStyle = when (span.type) {
                    SpanType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    SpanType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    SpanType.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    SpanType.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
                    SpanType.SPOILER ->
                        if (revealed) SpanStyle(background = codeBackground)
                        else SpanStyle(color = Color.Transparent, background = contentColor.copy(alpha = 0.45f))
                    SpanType.URL, SpanType.EMAIL, SpanType.PHONE ->
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                    SpanType.HASHTAG, SpanType.MENTION -> SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)
                }
                addStyle(spanStyle, span.start, span.end)
                when (span.type) {
                    SpanType.URL, SpanType.EMAIL, SpanType.PHONE ->
                        addStringAnnotation(TAG_LINK, span.type.name + "|" + span.data.orEmpty(), span.start, span.end)
                    SpanType.HASHTAG -> addStringAnnotation(TAG_LINK, "HASHTAG|" + span.data.orEmpty(), span.start, span.end)
                    SpanType.SPOILER -> addStringAnnotation(TAG_SPOILER, "1", span.start, span.end)
                    else -> Unit
                }
            }
        }.toAnnotatedString()
    }

    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style.copy(color = if (style.color == Color.Unspecified) contentColor else style.color),
        onClick = { offset ->
            val link = annotated.getStringAnnotations(TAG_LINK, offset, offset).firstOrNull()
            if (link != null) {
                val (kind, data) = link.item.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                when (kind) {
                    "HASHTAG" -> onHashtag("#$data")
                    else -> openLink(context, kind, data)
                }
            } else if (annotated.getStringAnnotations(TAG_SPOILER, offset, offset).isNotEmpty()) {
                revealed = !revealed
            }
        },
    )
}

private fun openLink(context: Context, kind: String, data: String) {
    val intent = when (kind) {
        "URL" -> Intent(Intent.ACTION_VIEW, Uri.parse(data))
        "EMAIL" -> Intent(Intent.ACTION_SENDTO, Uri.parse(data)) // mailto:
        "PHONE" -> Intent(Intent.ACTION_DIAL, Uri.parse(data))   // tel: — opens the dialer, never dials by itself
        else -> return
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
