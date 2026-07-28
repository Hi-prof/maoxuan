package com.xuhuangbin.xinghuozhaidu.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Transaction
    @Query("SELECT * FROM cards ORDER BY workTitle, id")
    fun observeCards(): Flow<List<CardWithSources>>

    @Query("SELECT * FROM image_assets")
    fun observeImages(): Flow<List<ImageAssetEntity>>

    @Query("SELECT * FROM user_card_state")
    fun observeUserStates(): Flow<List<UserCardStateEntity>>

    @Query("SELECT * FROM content_state WHERE id = 0")
    fun observeContentState(): Flow<ContentStateEntity?>

    @Query("SELECT * FROM content_state WHERE id = 0")
    suspend fun getContentState(): ContentStateEntity?

    @Upsert
    suspend fun upsertContentState(value: ContentStateEntity)

    @Query("UPDATE content_state SET lastCheckedAt = :checkedAt WHERE id = 0")
    suspend fun updateLastCheckedAt(checkedAt: Long)

    @Upsert
    suspend fun upsertCards(values: List<CardEntity>)

    @Upsert
    suspend fun upsertImages(values: List<ImageAssetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(values: List<CardSourceEntity>)

    @Query("DELETE FROM card_sources WHERE cardId = :cardId")
    suspend fun deleteSources(cardId: String)

    @Query("SELECT * FROM cards WHERE id = :cardId")
    suspend fun getCard(cardId: String): CardEntity?

    @Transaction
    @Query("SELECT * FROM cards WHERE id = :cardId")
    suspend fun getCardWithSources(cardId: String): CardWithSources?

    @Query("DELETE FROM cards WHERE id = :cardId")
    suspend fun deleteCard(cardId: String)

    @Query("SELECT DISTINCT imageId FROM cards")
    suspend fun getReferencedImageIds(): List<String>

    @Query("SELECT * FROM image_assets")
    suspend fun getImages(): List<ImageAssetEntity>

    @Query("DELETE FROM image_assets WHERE id = :imageId")
    suspend fun deleteImage(imageId: String)

    @Query("SELECT id FROM cards WHERE availability = 'active'")
    suspend fun getActiveCardIds(): List<String>

    @Query("UPDATE cards SET availability = 'withdrawn' WHERE id = :cardId")
    suspend fun markWithdrawn(cardId: String)

    @Upsert
    suspend fun upsertWithdrawals(values: List<WithdrawalEntity>)

    @Query("SELECT * FROM withdrawals WHERE cardId = :cardId")
    suspend fun getWithdrawal(cardId: String): WithdrawalEntity?

    @Query("SELECT * FROM user_card_state WHERE cardId = :cardId")
    suspend fun getUserState(cardId: String): UserCardStateEntity?

    @Upsert
    suspend fun upsertUserState(value: UserCardStateEntity)

    @Query("DELETE FROM user_card_state WHERE cardId = :cardId")
    suspend fun deleteUserState(cardId: String)

    @Query("SELECT * FROM reading_rounds WHERE state IN ('active', 'completed') ORDER BY id DESC LIMIT 1")
    fun observeActiveRound(): Flow<ReadingRoundEntity?>

    @Query("SELECT * FROM reading_rounds WHERE state IN ('active', 'completed') ORDER BY id DESC LIMIT 1")
    suspend fun getActiveRound(): ReadingRoundEntity?

    @Insert
    suspend fun insertRound(value: ReadingRoundEntity): Long

    @Query("UPDATE reading_rounds SET state = 'completed', completedAt = :completedAt WHERE id = :roundId")
    suspend fun completeRound(roundId: Long, completedAt: Long)

    @Query("UPDATE reading_rounds SET state = 'active', completedAt = NULL WHERE id = :roundId")
    suspend fun reactivateRound(roundId: Long)

    @Query("UPDATE reading_rounds SET state = 'archived' WHERE state IN ('active', 'completed')")
    suspend fun archiveRounds()

    @Query("UPDATE reading_rounds SET currentPosition = :position WHERE id = :roundId")
    suspend fun updateRoundPosition(roundId: Long, position: Int)

    @Query("SELECT * FROM reading_round_items WHERE roundId = :roundId ORDER BY position")
    fun observeRoundItems(roundId: Long): Flow<List<ReadingRoundItemEntity>>

    @Query("SELECT * FROM reading_round_items WHERE roundId = :roundId ORDER BY position")
    suspend fun getRoundItems(roundId: Long): List<ReadingRoundItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoundItems(values: List<ReadingRoundItemEntity>)

    @Query("DELETE FROM reading_round_items WHERE roundId = :roundId")
    suspend fun deleteRoundItems(roundId: Long)

    @Query("UPDATE reading_round_items SET readAt = COALESCE(readAt, :readAt) WHERE roundId = :roundId AND cardId = :cardId")
    suspend fun markRead(roundId: Long, cardId: String, readAt: Long)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC, id DESC")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(value: SearchHistoryEntity)

    @Query(
        """
        DELETE FROM search_history
        WHERE id NOT IN (
            SELECT id FROM search_history ORDER BY searchedAt DESC, id DESC LIMIT :limit
        )
        """,
    )
    suspend fun trimSearchHistory(limit: Int)

    @Query("DELETE FROM search_history WHERE keyword = :keyword COLLATE NOCASE")
    suspend fun deleteSearchHistory(keyword: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC, id DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNote(noteId: Long): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes WHERE cardId = :cardId")
    suspend fun countNotesForCard(cardId: String): Int

    @Insert
    suspend fun insertNote(value: NoteEntity): Long

    @Query("UPDATE notes SET title = :title, body = :body, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun updateNote(noteId: Long, title: String?, body: String, updatedAt: Long): Int

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNote(noteId: Long): Int
}
