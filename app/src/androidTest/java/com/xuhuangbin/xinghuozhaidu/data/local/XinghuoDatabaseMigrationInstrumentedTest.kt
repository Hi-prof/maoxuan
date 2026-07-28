package com.xuhuangbin.xinghuozhaidu.data.local

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class XinghuoDatabaseMigrationInstrumentedTest {
    private val databaseName = "migration-${UUID.randomUUID()}.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        XinghuoDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    @Test
    fun migrationFromOneToTwoPreservesUserStateAndCreatesSearchHistory() {
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                """
                INSERT INTO user_card_state(cardId, liked, favorited, likedAt, favoritedAt)
                VALUES ('card-1', 1, 1, 100, 200)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            2,
            true,
            XinghuoDatabase.MIGRATION_1_2,
        )

        migrated.query(
            "SELECT liked, favorited FROM user_card_state WHERE cardId = 'card-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(1, cursor.getInt(1))
        }
        migrated.query("SELECT COUNT(*) FROM search_history").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migrationFromTwoToThreeAddsInterpretationAndPreservesExistingState() {
        helper.createDatabase(databaseName, 2).apply {
            execSQL(
                """
                INSERT INTO cards(
                    id, revision, quote, series, volume, workTitle, authoredAt, themes,
                    contextExcerpt, background, story, imageId, availability
                ) VALUES (
                    'card-1', 1, '引文', '毛泽东选集', '第一卷', '篇名', '1937', '实践',
                    NULL, NULL, NULL, 'image-1', 'active'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO user_card_state(cardId, liked, favorited, likedAt, favoritedAt)
                VALUES ('card-1', 1, 1, 100, 200)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO reading_rounds(id, state, currentPosition, createdAt, completedAt)
                VALUES (7, 'active', 2, 250, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO reading_round_items(roundId, position, cardId, readAt)
                VALUES (7, 2, 'card-1', 275)
                """.trimIndent(),
            )
            execSQL("INSERT INTO search_history(keyword, searchedAt) VALUES ('实践', 300)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            XinghuoDatabase.MIGRATION_2_3,
        )

        migrated.query(
            """
            SELECT interpretationCoreMeaning, interpretationKeyPoint,
                   interpretationContemporaryRelevance
            FROM cards WHERE id = 'card-1'
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertEquals("", cursor.getString(2))
        }
        migrated.query(
            "SELECT liked, favorited FROM user_card_state WHERE cardId = 'card-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(1, cursor.getInt(1))
        }
        migrated.query("SELECT keyword FROM search_history").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("实践", cursor.getString(0))
        }
        migrated.query(
            "SELECT currentPosition FROM reading_rounds WHERE id = 7",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        migrated.query(
            "SELECT position, readAt FROM reading_round_items WHERE roundId = 7",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
            assertEquals(275, cursor.getLong(1))
        }
        migrated.close()
    }

    @Test
    fun migrationFromThreeToFourCreatesNotesAndPreservesExistingState() {
        helper.createDatabase(databaseName, 3).apply {
            execSQL(
                """
                INSERT INTO user_card_state(cardId, liked, favorited, likedAt, favoritedAt)
                VALUES ('card-1', 1, 0, 100, NULL)
                """.trimIndent(),
            )
            execSQL("INSERT INTO search_history(keyword, searchedAt) VALUES ('实践', 300)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            XinghuoDatabase.MIGRATION_3_4,
        )

        migrated.query(
            "SELECT liked, favorited FROM user_card_state WHERE cardId = 'card-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
        }
        migrated.query("SELECT keyword FROM search_history").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("实践", cursor.getString(0))
        }
        migrated.execSQL(
            """
            INSERT INTO notes(cardId, title, body, createdAt, updatedAt)
            VALUES ('card-1', '标题', '正文', 400, 500)
            """.trimIndent(),
        )
        migrated.query("SELECT cardId, title, body FROM notes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("card-1", cursor.getString(0))
            assertEquals("标题", cursor.getString(1))
            assertEquals("正文", cursor.getString(2))
        }
        migrated.close()
    }
}
