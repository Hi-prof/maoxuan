package com.xuhuangbin.xinghuozhaidu.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cards", primaryKeys = ["id"])
data class CardEntity(
    val id: String,
    val revision: Int,
    val quote: String,
    val series: String,
    val volume: String,
    val workTitle: String,
    val authoredAt: String,
    val themes: String,
    val interpretationInspiration: String,
    val interpretationExplanation: String,
    val historicalEvent: String,
    val background: String,
    val story: String,
    val imageId: String,
    val availability: String,
)

@Entity(
    tableName = "card_sources",
    primaryKeys = ["cardId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cardId")],
)
data class CardSourceEntity(
    val cardId: String,
    val position: Int,
    val name: String,
    val url: String,
    val accessedAt: String,
    val evidenceType: String,
)

@Entity(tableName = "image_assets", primaryKeys = ["id"])
data class ImageAssetEntity(
    val id: String,
    val sha256: String,
    val localPath: String,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val sourceUrl: String,
    val creator: String,
    val licenseName: String,
    val licenseEvidence: String,
    val verifiedAt: String,
)

@Entity(tableName = "user_card_state", primaryKeys = ["cardId"])
data class UserCardStateEntity(
    val cardId: String,
    val liked: Boolean = false,
    val favorited: Boolean = false,
    val likedAt: Long? = null,
    val favoritedAt: Long? = null,
)

@Entity(tableName = "reading_rounds")
data class ReadingRoundEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val state: String,
    val currentPosition: Int,
    val furthestPosition: Int = currentPosition,
    val createdAt: Long,
    val completedAt: Long? = null,
)

@Entity(
    tableName = "reading_round_items",
    primaryKeys = ["roundId", "position"],
    indices = [Index(value = ["roundId", "cardId"], unique = true), Index("cardId")],
)
data class ReadingRoundItemEntity(
    val roundId: Long,
    val position: Int,
    val cardId: String,
    val readAt: Long? = null,
)

@Entity(tableName = "withdrawals", primaryKeys = ["cardId"])
data class WithdrawalEntity(
    val cardId: String,
    val revision: Int,
    val withdrawnAt: String,
)

@Entity(tableName = "content_state")
data class ContentStateEntity(
    @PrimaryKey val id: Int = 0,
    val contentVersion: String,
    val publishedAt: String,
    val lastCheckedAt: Long? = null,
    val lastUpdatedAt: Long,
)

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["keyword"], unique = true)],
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val keyword: String,
    val searchedAt: Long,
)

@Entity(
    tableName = "notes",
    indices = [Index("cardId"), Index(value = ["updatedAt", "id"])],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String? = null,
    val title: String? = null,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "recommendation_state")
data class RecommendationStateEntity(
    @PrimaryKey val id: Int = 0,
    val onboardingCompleted: Boolean,
)

@Entity(tableName = "interest_preferences")
data class InterestPreferenceEntity(
    @PrimaryKey val categoryId: String,
)

@Entity(tableName = "content_series_preferences")
data class ContentSeriesPreferenceEntity(
    @PrimaryKey val series: String,
)

@Entity(tableName = "reduced_cards")
data class ReducedCardEntity(
    @PrimaryKey val cardId: String,
    val createdAt: Long,
)
