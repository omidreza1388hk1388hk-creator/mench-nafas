package com.omidgame.mench.feature.chat.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GroupRulesTest {
    @Test
    fun `removal follows owner - admin - member`() {
        assertThat(GroupRules.canRemove(GroupRole.OWNER, GroupRole.ADMIN)).isTrue()
        assertThat(GroupRules.canRemove(GroupRole.OWNER, GroupRole.OWNER)).isFalse()
        assertThat(GroupRules.canRemove(GroupRole.ADMIN, GroupRole.MEMBER)).isTrue()
        assertThat(GroupRules.canRemove(GroupRole.ADMIN, GroupRole.ADMIN)).isFalse()
        assertThat(GroupRules.canRemove(GroupRole.MEMBER, GroupRole.MEMBER)).isFalse()
    }

    @Test
    fun `only the owner toggles admin, never on themselves`() {
        assertThat(GroupRules.roleToggleTarget(GroupRole.OWNER, GroupRole.MEMBER)).isEqualTo(GroupRole.ADMIN)
        assertThat(GroupRules.roleToggleTarget(GroupRole.OWNER, GroupRole.ADMIN)).isEqualTo(GroupRole.MEMBER)
        assertThat(GroupRules.roleToggleTarget(GroupRole.OWNER, GroupRole.OWNER)).isNull()
        assertThat(GroupRules.roleToggleTarget(GroupRole.ADMIN, GroupRole.MEMBER)).isNull()
    }

    @Test
    fun `channel mode lets only admins post`() {
        val channel = GroupInfo(GroupRole.MEMBER, GroupSettings(adminsOnlySend = true))
        assertThat(channel.isChannel).isTrue()
        assertThat(channel.canPost).isFalse()
        assertThat(GroupInfo(GroupRole.ADMIN, GroupSettings(adminsOnlySend = true)).canPost).isTrue()
        assertThat(GroupInfo(GroupRole.MEMBER, GroupSettings()).canPost).isTrue()
    }

    @Test
    fun `permissions gate adding and editing`() {
        val locked = GroupSettings(adminsOnlyAddMembers = true, adminsOnlyEditInfo = true)
        assertThat(GroupRules.canAddMembers(GroupRole.MEMBER, locked)).isFalse()
        assertThat(GroupRules.canAddMembers(GroupRole.ADMIN, locked)).isTrue()
        assertThat(GroupRules.canEditInfo(GroupRole.MEMBER, GroupSettings())).isTrue()
        assertThat(GroupRules.canEditInfo(GroupRole.MEMBER, locked)).isFalse()
    }

    @Test
    fun `invite codes are extracted from pasted text`() {
        assertThat(GroupRules.extractInviteCode("abcd-efgh-2345")).isEqualTo("ABCDEFGH2345")
        assertThat(GroupRules.extractInviteCode(GroupRules.inviteShareText("Us", "ABCDEFGH2345"))).isEqualTo("ABCDEFGH2345")
        assertThat(GroupRules.extractInviteCode("hello world")).isNull()
        assertThat(GroupRules.extractInviteCode("OOOO1111IIII")).isNull()
        assertThat(GroupRules.SLOW_MODE_OPTIONS).containsExactly(0, 10, 30, 60, 300, 900, 3600).inOrder()
    }
}
