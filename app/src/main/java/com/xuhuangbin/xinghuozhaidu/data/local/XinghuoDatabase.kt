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
        RecommendationStateEntity::class,
        InterestPreferenceEntity::class,
        ReducedCardEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class XinghuoDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        fun create(context: Context): XinghuoDatabase = Room.databaseBuilder(
            context,
            XinghuoDatabase::class.java,
            "xinghuo.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()

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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `reading_rounds` ADD COLUMN `furthestPosition` " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    """
                    UPDATE `reading_rounds`
                    SET `furthestPosition` = MAX(
                        `currentPosition`,
                        COALESCE(
                            (
                                SELECT MAX(`position`)
                                FROM `reading_round_items`
                                WHERE `roundId` = `reading_rounds`.`id`
                                  AND `readAt` IS NOT NULL
                            ),
                            `currentPosition`
                        )
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recommendation_state` (
                        `id` INTEGER NOT NULL,
                        `onboardingCompleted` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `interest_preferences` (
                        `categoryId` TEXT NOT NULL,
                        PRIMARY KEY(`categoryId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reduced_cards` (
                        `cardId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`cardId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `recommendation_state` (`id`, `onboardingCompleted`)
                    VALUES (0, 1)
                    """.trimIndent(),
                )
            }
        }
    }
}
