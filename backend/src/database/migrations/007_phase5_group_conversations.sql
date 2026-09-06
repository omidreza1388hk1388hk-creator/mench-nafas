-- Phase 5: group conversations.
--
-- conversations.kind already allowed 'group' and conversation_members
-- already had an owner/member role since 001_init.sql — those were laid
-- down early so this phase's schema change is small: just a place to put
-- a group's name. Nothing else needs to change; a direct conversation's
-- title stays NULL forever (the client derives its display name from the
-- other member instead — see ConversationsRepository.getOtherMember).

ALTER TABLE conversations
    ADD COLUMN title TEXT;
