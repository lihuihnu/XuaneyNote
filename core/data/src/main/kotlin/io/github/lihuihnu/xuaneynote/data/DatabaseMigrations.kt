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
}
