package com.omidgame.mench.feature.settings.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatSettingsModelTest {
    @Test fun `defaults reproduce the old behaviour`() {
        val d = ChatSettingsSnapshot()
        assertThat(d.messageTextScale).isEqualTo(1f)
        assertThat(d.enterSends).isFalse()
        assertThat(d.linkPreviews).isTrue()
        assertThat(d.keepArchived).isFalse()
        assertThat(d.swipeReply).isEqualTo(SwipeReplyDirection.ANY)
        assertThat(d.quickReactions).hasSize(8) // the old hard-coded list, until the user customises
    }

    @Test fun `text scale and spacing are clamped`() {
        assertThat(ChatSettingsRules.clampTextScale(0.1f)).isEqualTo(0.85f)
        assertThat(ChatSettingsRules.clampTextScale(9f)).isEqualTo(1.5f)
        assertThat(ChatSettingsRules.clampLineSpacing(0f)).isEqualTo(1f)
        assertThat(ChatSettingsRules.clampLineSpacing(3f)).isEqualTo(1.6f)
    }

    @Test fun `quick reactions are limited deduplicated and never empty`() {
        assertThat(ChatSettingsRules.sanitizeQuickReactions(listOf("a", "a", " ", "b", "c", "d", "e", "f", "g")))
            .containsExactly("a", "b", "c", "d", "e", "f").inOrder()
        assertThat(ChatSettingsRules.sanitizeQuickReactions(emptyList())).isEqualTo(ChatSettingsRules.DEFAULT_QUICK_REACTIONS)
        assertThat(ChatSettingsRules.decodeReactions(null)).isEqualTo(ChatSettingsRules.DEFAULT_QUICK_REACTIONS)
    }

    @Test fun `a custom list is capped at six but the default list is kept whole`() {
        assertThat(ChatSettingsRules.sanitizeQuickReactions(ChatSettingsRules.DEFAULT_QUICK_REACTIONS)).hasSize(6)
        assertThat(ChatSettingsRules.decodeReactions("")).hasSize(8)
    }

    @Test fun `encode and decode round trip`() {
        val list = listOf("❤️", "🔥", "😂")
        assertThat(ChatSettingsRules.decodeReactions(ChatSettingsRules.encodeReactions(list))).isEqualTo(list)
    }

    @Test fun `toggle adds removes caps at six and keeps at least one`() {
        val six = listOf("a", "b", "c", "d", "e", "f")
        assertThat(ChatSettingsRules.toggleReaction(six, "🔥")).isEqualTo(six) // full: ignored
        assertThat(ChatSettingsRules.toggleReaction(six, "a")).hasSize(5)
        assertThat(ChatSettingsRules.toggleReaction(listOf("a"), "a")).containsExactly("a")
        assertThat(ChatSettingsRules.toggleReaction(listOf("a"), "b")).containsExactly("a", "b").inOrder()
    }

    @Test fun `swipe direction respects rtl`() {
        assertThat(ChatSettingsRules.swipeAccepted(SwipeReplyDirection.ANY, -5f, false)).isTrue()
        assertThat(ChatSettingsRules.swipeAccepted(SwipeReplyDirection.START, 5f, false)).isTrue()
        assertThat(ChatSettingsRules.swipeAccepted(SwipeReplyDirection.START, -5f, false)).isFalse()
        assertThat(ChatSettingsRules.swipeAccepted(SwipeReplyDirection.START, -5f, true)).isTrue()
        assertThat(ChatSettingsRules.swipeAccepted(SwipeReplyDirection.END, -5f, false)).isTrue()
        assertThat(ChatSettingsRules.swipeAccepted(SwipeReplyDirection.END, 5f, true)).isTrue()
    }

    @Test fun `unknown stored values fall back safely`() {
        assertThat(SwipeReplyDirection.fromStorage("zzz")).isEqualTo(SwipeReplyDirection.ANY)
        assertThat(PhotoQuality.fromStorage(null)).isEqualTo(PhotoQuality.COMPRESSED)
    }
}
