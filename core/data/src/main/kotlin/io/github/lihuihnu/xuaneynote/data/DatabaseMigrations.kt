package io.github.lihuihnu.xuaneynote.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * PR-A 数据库 v1 → PR-B v2。
 *
 * 只增加可空字段，不重建 notes、不复制/删除记录，不改动旧主键和索引。
 * NULL 表示正常笔记；B2 再实现回收站状态与事务流程。
 */
object DatabaseMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `notes` ADD COLUMN `deletedAt` INTEGER")
        }
    }

    /** B3.1：纯新增空表/索引；不动已有笔记、删除状态和文本备份语义。 */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `attachments` (" +
                    "`id` TEXT NOT NULL, `relativePath` TEXT NOT NULL, " +
                    "`mimeType` TEXT NOT NULL, `byteSize` INTEGER NOT NULL, " +
                    "`sha256` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`width` INTEGER, `height` INTEGER, PRIMARY KEY(`id`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_sha256` ON `attachments` (`sha256`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_relativePath` ON `attachments` (`relativePath`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `note_attachments` (" +
                    "`noteId` TEXT NOT NULL, `attachmentId` TEXT NOT NULL, " +
                    "`sortOrder` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`noteId`, `attachmentId`), " +
                    "FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`attachmentId`) REFERENCES `attachments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_note_attachments_attachmentId` " +
                    "ON `note_attachments` (`attachmentId`)"
            )
        }
    }
}
