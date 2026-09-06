package com.omidgame.mench.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Single local Room database for the app. Future phases add entities via
 * new @Database versions + explicit Migration objects — never
 * fallbackToDestructiveMigration in a real build, since that would
 * silently delete the user's offline data (violates spec section 13/41).
 */
@Database(
    entities = [
        UserEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        OutboxEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun outboxDao(): OutboxDao
}

/**
 * Phase 2: adds conversations, messages, and the outbox. Written by hand
 * against the exact column set declared in ConversationEntity /
 * MessageEntity / OutboxEntity above — every column, type, and nullability
 * here must match those data classes exactly, since Room validates the
 * live schema against the entity definitions at runtime and fails loudly
 * (not silently) if they diverge.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `conversations` (
                `id` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `otherUserId` TEXT,
                `otherUserDisplayName` TEXT,
                `otherUserPhoneE164` TEXT,
                `lastMessageAtEpochMillis` INTEGER,
                `lastReadSequence` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `messages` (
                `clientMsgId` TEXT NOT NULL,
                `conversationId` TEXT NOT NULL,
                `senderId` TEXT NOT NULL,
                `serverId` TEXT,
                `body` TEXT NOT NULL,
                `sequence` INTEGER,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `state` TEXT NOT NULL,
                PRIMARY KEY(`clientMsgId`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `outbox` (
                `operationId` TEXT NOT NULL,
                `operationType` TEXT NOT NULL,
                `conversationId` TEXT NOT NULL,
                `payloadJson` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `retryCount` INTEGER NOT NULL,
                `lastAttemptAtEpochMillis` INTEGER,
                `state` TEXT NOT NULL,
                PRIMARY KEY(`operationId`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * Phase 3a: adds attachment support to messages, and — the part that
 * actually needs a table rebuild rather than a plain ALTER TABLE — makes
 * `body` nullable (an attachment-only message, e.g. a photo with no
 * caption, has nothing to put there). SQLite has no "drop NOT NULL"
 * operation; changing an existing column's nullability requires the
 * standard SQLite migration pattern: create the new table shape, copy
 * data across, drop the old table, rename. The new attachment columns
 * alone could have been plain ALTER TABLE ADD COLUMNs, but since the
 * whole table already needs rebuilding for the body change, they're
 * included in the same rebuild rather than split across two migrations.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `messages_new` (
                `clientMsgId` TEXT NOT NULL,
                `conversationId` TEXT NOT NULL,
                `senderId` TEXT NOT NULL,
                `serverId` TEXT,
                `kind` TEXT NOT NULL,
                `body` TEXT,
                `sequence` INTEGER,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `state` TEXT NOT NULL,
                `attachmentId` TEXT,
                `attachmentMimeType` TEXT,
                `attachmentOriginalFilename` TEXT,
                `attachmentSizeBytes` INTEGER,
                `attachmentWidthPx` INTEGER,
                `attachmentHeightPx` INTEGER,
                `attachmentHasThumbnail` INTEGER NOT NULL DEFAULT 0,
                `localContentUri` TEXT,
                `localThumbnailUri` TEXT,
                PRIMARY KEY(`clientMsgId`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `messages_new`
                (clientMsgId, conversationId, senderId, serverId, kind, body,
                 sequence, createdAtEpochMillis, state,
                 attachmentId, attachmentMimeType, attachmentOriginalFilename,
                 attachmentSizeBytes, attachmentWidthPx, attachmentHeightPx, attachmentHasThumbnail,
                 localContentUri, localThumbnailUri)
            SELECT
                clientMsgId, conversationId, senderId, serverId, 'text', body,
                sequence, createdAtEpochMillis, state,
                NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL
            FROM `messages`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `messages`")
        db.execSQL("ALTER TABLE `messages_new` RENAME TO `messages`")
    }
}

/**
 * Phase 3 (voice messages): adds attachmentDurationMs. A plain
 * ALTER TABLE ADD COLUMN is sufficient this time — unlike MIGRATION_2_3,
 * nothing about nullability of an existing column is changing, just a
 * new nullable column appearing.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `attachmentDurationMs` INTEGER")
    }
}

/**
 * Phase 4 (message edit/delete/forward/reactions): four new nullable
 * columns, all plain ALTER TABLE ADD COLUMNs — nothing about an existing
 * column's type or nullability changes, so unlike MIGRATION_2_3 this
 * doesn't need the create-copy-drop-rename dance.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `editedAtEpochMillis` INTEGER")
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `deletedAtEpochMillis` INTEGER")
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `forwardedFromMessageId` TEXT")
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `reactionsJson` TEXT")
    }
}

/** Phase 5 (group conversations): one new nullable column, mirroring the backend's conversations.title — see that migration's comment for why conversation_members/kind needed no schema change at all. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `conversations` ADD COLUMN `title` TEXT")
    }
}

/** Phase 6 (Account settings): caches the user's own bio locally, mirroring the backend's users.bio, so the Account screen has something to show offline immediately on open. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `cached_self_user` ADD COLUMN `bio` TEXT")
    }
}
