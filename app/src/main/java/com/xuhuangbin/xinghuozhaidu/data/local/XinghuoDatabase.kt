package com.xuhuangbin.xinghuozhaidu.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CardEntity::class,
        CardSourceEntity::class,
        ImageAssetEntity::class,
        UserCardStateEntity::class,
        ReadingRoundEntity::class,
        ReadingRoundItemEntity::class,
        WithdrawalEntity::class,
        ContentStateEntity::class,
        SearchHistoryEntity::class,
        NoteEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class XinghuoDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        fun create(context: Context): XinghuoDatabase = Room.databaseBuilder(
            context,
            XinghuoDatabase::class.java,
            "xinghuo.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `search_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `keyword` TEXT NOT NULL COLLATE NOCASE,
                        `searchedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_search_history_keyword` " +
                        "ON `search_history` (`keyword`)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `cards` ADD COLUMN `interpretationCoreMeaning` " +
                        "TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE `cards` ADD COLUMN `interpretationKeyPoint` " +
                        "TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE `cards` ADD COLUMN `interpretationContemporaryRelevance` " +
                        "TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `cardId` TEXT,
                        `title` TEXT,
                        `body` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notes_cardId` ON `notes` (`cardId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notes_updatedAt_id` " +
                        "ON `notes` (`updatedAt`, `id`)",
                )
            }
        }
    }
}
