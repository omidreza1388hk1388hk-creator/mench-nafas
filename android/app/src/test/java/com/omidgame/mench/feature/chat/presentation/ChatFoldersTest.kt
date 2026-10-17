package com.omidgame.mench.feature.chat.presentation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatFoldersTest {
    private fun sample(): List<ChatFolder> {
        var f = ChatFolders.create(emptyList(), "f1", "Family")
        f = ChatFolders.create(f, "f2", "Work")
        f = ChatFolders.setMembership(f, "f1", "c1", true)
        f = ChatFolders.setMembership(f, "f1", "c2", true)
        return ChatFolders.setMembership(f, "f2", "c2", true)
    }

    @Test
    fun `create trims, flattens whitespace and rejects blank, duplicate and over-limit names`() {
        val f = ChatFolders.create(emptyList(), "f1", "  Family ")
        assertThat(f.single().name).isEqualTo("Family")
        assertThat(ChatFolders.create(f, "f2", "   ")).isEqualTo(f)
        assertThat(ChatFolders.create(f, "f2", "FAMILY")).isEqualTo(f)
        assertThat(ChatFolders.create(f, "f1", "Other")).isEqualTo(f)
        assertThat(ChatFolders.create(f, "f2", "Work\tteam\nA")[1].name).isEqualTo("Work team A")
        var many = emptyList<ChatFolder>()
        repeat(20) { many = ChatFolders.create(many, "i$it", "n$it") }
        assertThat(many).hasSize(ChatFolders.MAX_FOLDERS)
    }

    @Test
    fun `encode and decode round trip, and decode survives garbage`() {
        val f = sample()
        assertThat(ChatFolders.decode(ChatFolders.encode(f))).isEqualTo(f)
        assertThat(ChatFolders.decode(null)).isEmpty()
        assertThat(ChatFolders.decode("nonsense\n\t\t\n")).isEmpty()
        assertThat(ChatFolders.decode("a\tName\t").single().conversationIds).isEmpty()
    }

    @Test
    fun `membership rename delete and prune`() {
        val f = sample()
        assertThat(ChatFolders.foldersContaining(f, "c2")).containsExactly("f1", "f2")
        assertThat(ChatFolders.setMembership(f, "f1", "c1", false)[0].conversationIds).containsExactly("c2")
        assertThat(ChatFolders.rename(f, "f2", "Office")[1].name).isEqualTo("Office")
        assertThat(ChatFolders.rename(f, "f2", "family")).isEqualTo(f)
        assertThat(ChatFolders.delete(f, "f1").map { it.id }).containsExactly("f2")
        assertThat(ChatFolders.prune(f, setOf("c2", "zz"))).isSameInstanceAs(f)
        assertThat(ChatFolders.prune(f, emptySet()).all { it.conversationIds.isEmpty() }).isTrue()
    }

    @Test
    fun `filters match unread, groups and folders; a deleted folder falls back to All`() {
        val f = sample()
        assertThat(ChatFolders.matches(ChatFilter.All, f, "x", isGroup = false, unread = false)).isTrue()
        assertThat(ChatFolders.matches(ChatFilter.Unread, f, "x", isGroup = false, unread = true)).isTrue()
        assertThat(ChatFolders.matches(ChatFilter.Unread, f, "x", isGroup = false, unread = false)).isFalse()
        assertThat(ChatFolders.matches(ChatFilter.Groups, f, "x", isGroup = true, unread = false)).isTrue()
        assertThat(ChatFolders.matches(ChatFilter.Folder("f1"), f, "c2", isGroup = false, unread = false)).isTrue()
        assertThat(ChatFolders.matches(ChatFilter.Folder("f1"), f, "c9", isGroup = false, unread = false)).isFalse()
        assertThat(ChatFolders.matches(ChatFilter.Folder("nope"), f, "c2", isGroup = false, unread = false)).isFalse()
        assertThat(ChatFolders.sanitize(ChatFilter.Folder("nope"), f)).isEqualTo(ChatFilter.All)
        assertThat(ChatFolders.sanitize(ChatFilter.Folder("f1"), f)).isEqualTo(ChatFilter.Folder("f1"))
    }
}
