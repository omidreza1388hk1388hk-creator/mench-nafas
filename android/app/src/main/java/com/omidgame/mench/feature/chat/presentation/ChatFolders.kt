package com.omidgame.mench.feature.chat.presentation

/** A user-made chat-list folder. Membership is by conversation id; stored on this phone only. */
data class ChatFolder(val id: String, val name: String, val conversationIds: Set<String>)

/** Which slice of the chat list is showing. */
sealed interface ChatFilter {
    data object All : ChatFilter
    data object Unread : ChatFilter
    data object Groups : ChatFilter
    data class Folder(val id: String) : ChatFilter
}

/**
 * Pure folder logic for the chat list (Phase 2A): create / rename / delete, membership,
 * filtering and the on-disk text format. No Android types so it is unit-tested on the JVM.
 *
 * Text format, one folder per line:  id TAB name TAB id1,id2,...
 * Names have tabs / newlines flattened to spaces on the way in, so the format cannot be broken
 * by user input; ids are UUIDs (no tab, comma or newline).
 */
object ChatFolders {
    const val MAX_NAME_LENGTH = 24
    const val MAX_FOLDERS = 12

    fun encode(folders: List<ChatFolder>): String =
        folders.joinToString("\n") { "${it.id}\t${it.name}\t${it.conversationIds.joinToString(",")}" }

    fun decode(raw: String?): List<ChatFolder> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) return@mapNotNull null
            val ids = parts.getOrNull(2).orEmpty().split(',').filter { it.isNotBlank() }.toSet()
            ChatFolder(parts[0], parts[1], ids)
        }.toList()
    }

    /** Trimmed, whitespace-flattened, capped name; null when nothing usable is left. */
    fun cleanName(raw: String): String? {
        val flattened = raw.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
        return flattened.take(MAX_NAME_LENGTH).trim().takeIf { it.isNotEmpty() }
    }

    private fun nameTaken(folders: List<ChatFolder>, name: String, exceptId: String? = null) =
        folders.any { it.id != exceptId && it.name.equals(name, ignoreCase = true) }

    /** Returns [folders] unchanged when the name is blank / duplicate or the folder limit is reached. */
    fun create(folders: List<ChatFolder>, id: String, rawName: String): List<ChatFolder> {
        val name = cleanName(rawName) ?: return folders
        if (folders.size >= MAX_FOLDERS || nameTaken(folders, name) || folders.any { it.id == id }) return folders
        return folders + ChatFolder(id, name, emptySet())
    }

    fun rename(folders: List<ChatFolder>, id: String, rawName: String): List<ChatFolder> {
        val name = cleanName(rawName) ?: return folders
        if (nameTaken(folders, name, exceptId = id)) return folders
        return folders.map { if (it.id == id) it.copy(name = name) else it }
    }

    fun delete(folders: List<ChatFolder>, id: String): List<ChatFolder> = folders.filter { it.id != id }

    fun setMembership(folders: List<ChatFolder>, folderId: String, conversationId: String, member: Boolean): List<ChatFolder> =
        folders.map {
            if (it.id != folderId) it
            else it.copy(conversationIds = if (member) it.conversationIds + conversationId else it.conversationIds - conversationId)
        }

    fun foldersContaining(folders: List<ChatFolder>, conversationId: String): Set<String> =
        folders.filter { conversationId in it.conversationIds }.mapTo(HashSet()) { it.id }

    /** Drops ids of conversations that no longer exist (left a group, deleted) so folders do not grow stale. */
    fun prune(folders: List<ChatFolder>, existingConversationIds: Set<String>): List<ChatFolder> {
        var changed = false
        val pruned = folders.map { f ->
            val kept = f.conversationIds.filterTo(HashSet()) { it in existingConversationIds }
            if (kept.size != f.conversationIds.size) {
                changed = true
                f.copy(conversationIds = kept)
            } else f
        }
        return if (changed) pruned else folders
    }

    /** True when a conversation belongs in [filter]. [unread] already includes the manual "marked unread" flag. */
    fun matches(
        filter: ChatFilter,
        folders: List<ChatFolder>,
        conversationId: String,
        isGroup: Boolean,
        unread: Boolean,
    ): Boolean = when (filter) {
        ChatFilter.All -> true
        ChatFilter.Unread -> unread
        ChatFilter.Groups -> isGroup
        is ChatFilter.Folder -> folders.firstOrNull { it.id == filter.id }?.let { conversationId in it.conversationIds } ?: false
    }

    /** A filter that points at a folder that was deleted falls back to All. */
    fun sanitize(filter: ChatFilter, folders: List<ChatFolder>): ChatFilter =
        if (filter is ChatFilter.Folder && folders.none { it.id == filter.id }) ChatFilter.All else filter
}
