package com.omidgame.mench.feature.chat.presentation

import com.google.common.truth.Truth.assertThat
import com.omidgame.mench.feature.chat.domain.ReactionSummary
import org.junit.Test

class ReactorsAndPrefsTest {
    @Test
    fun `buildReactors puts me first and resolves names with fallbacks`() {
        val reactions = listOf(
            ReactionSummary("\u2764\uFE0F", listOf("u2", "me"), reactedByMe = true),
            ReactionSummary("\uD83D\uDE02", listOf("u3"), reactedByMe = false),
        )
        val rows = buildReactors(
            reactions = reactions,
            selfUserId = "me",
            nameOf = { id -> if (id == "u2") "Sara" else null },
            selfName = "You",
            unknownName = "Someone",
        )
        assertThat(rows.map { it.name }).containsExactly("You", "Sara", "Someone").inOrder()
        assertThat(rows.first().isMe).isTrue()
        assertThat(rows.first().emoji).isEqualTo("\u2764\uFE0F")
    }

    @Test
    fun `pushRecent is most recent first, de-duplicated and capped`() {
        assertThat(ChatPrefs.pushRecent(listOf("a", "b", "c"), "b")).containsExactly("b", "a", "c").inOrder()
        assertThat(ChatPrefs.pushRecent(emptyList(), "x")).containsExactly("x")
        assertThat(ChatPrefs.pushRecent(listOf("a", "b", "c"), "d", max = 3)).containsExactly("d", "a", "b").inOrder()
    }
}
