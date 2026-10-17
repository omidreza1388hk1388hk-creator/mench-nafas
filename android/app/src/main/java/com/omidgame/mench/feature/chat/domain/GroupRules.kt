package com.omidgame.mench.feature.chat.domain

/**
 * Phase 2A — client-side mirror of the server's group rules (backend group-policy.ts). The server is
 * the authority; this only decides which buttons to show, so the UI never offers what would be refused.
 */
enum class GroupRole {
    OWNER, ADMIN, MEMBER;

    val isAdmin: Boolean get() = this == OWNER || this == ADMIN

    companion object {
        fun parse(raw: String?): GroupRole = when (raw) {
            "owner" -> OWNER
            "admin" -> ADMIN
            else -> MEMBER
        }
    }
}

data class GroupSettings(
    val adminsOnlySend: Boolean = false,
    val adminsOnlyAddMembers: Boolean = false,
    val adminsOnlyEditInfo: Boolean = false,
    val adminsOnlyPin: Boolean = false,
    val slowModeSeconds: Int = 0,
)

data class GroupInfo(val myRole: GroupRole, val settings: GroupSettings) {
    /** Broadcast mode: only admins post. */
    val isChannel: Boolean get() = settings.adminsOnlySend
    val canPost: Boolean get() = !settings.adminsOnlySend || myRole.isAdmin
}

data class GroupInvite(val id: String, val code: String, val expiresAtEpochMillis: Long?, val maxUses: Int?, val uses: Int)

data class InvitePreview(val valid: Boolean, val problem: String?, val title: String?, val memberCount: Int)

object GroupRules {
    val SLOW_MODE_OPTIONS = listOf(0, 10, 30, 60, 300, 900, 3600)

    fun canRemove(actor: GroupRole, target: GroupRole): Boolean = when (actor) {
        GroupRole.OWNER -> target != GroupRole.OWNER
        GroupRole.ADMIN -> target == GroupRole.MEMBER
        GroupRole.MEMBER -> false
    }

    /** Only the owner promotes / demotes, never themselves. Returns the role a tap would switch to, or null. */
    fun roleToggleTarget(actor: GroupRole, target: GroupRole): GroupRole? = when {
        actor != GroupRole.OWNER -> null
        target == GroupRole.MEMBER -> GroupRole.ADMIN
        target == GroupRole.ADMIN -> GroupRole.MEMBER
        else -> null
    }

    fun canAddMembers(role: GroupRole, settings: GroupSettings) = !settings.adminsOnlyAddMembers || role.isAdmin
    fun canEditInfo(role: GroupRole, settings: GroupSettings) = !settings.adminsOnlyEditInfo || role.isAdmin
    fun canManage(role: GroupRole) = role.isAdmin

    /** The text sent when sharing an invite: the code is what the receiver pastes into "Join with code". */
    fun inviteShareText(title: String, code: String): String =
        "Join \"$title\" on MENCH — open Chats › ⋮ › Join group and enter this code: $code"

    /** Hours-until-expiry presets offered when creating a link (null = never). */
    val EXPIRY_PRESET_HOURS: List<Int?> = listOf(1, 24, 24 * 7, null)

    /** Accepts a pasted code or a longer text containing one (e.g. the whole share message). */
    fun extractInviteCode(pasted: String): String? {
        val tokens = pasted.uppercase().split(Regex("[^A-Z0-9-]+")).map { it.replace("-", "") }
        return tokens.firstOrNull { it.length == 12 && it.all { c -> c in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" } }
    }
}
