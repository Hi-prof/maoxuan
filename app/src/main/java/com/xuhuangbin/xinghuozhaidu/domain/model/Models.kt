package com.xuhuangbin.xinghuozhaidu.domain.model

import com.xuhuangbin.xinghuozhaidu.domain.recommendation.InterestCategory

data class CardSource(
    val name: String,
    val url: String,
    val accessedAt: String,
    val evidenceType: String,
)

data class CardInterpretation(
    val inspiration: String,
    val explanation: String,
)

data class QuoteCard(
    val id: String,
    val revision: Int,
    val quote: String,
    val series: String,
    val volume: String,
    val workTitle: String,
    val authoredAt: String,
    val themes: List<String>,
    val interpretation: CardInterpretation,
    val historicalEvent: String,
    val background: String,
    val story: String,
    val imagePath: String,
    val sources: List<CardSource>,
    val isWithdrawn: Boolean,
    val isLiked: Boolean,
    val isFavorited: Boolean,
    val likedAt: Long?,
    val favoritedAt: Long?,
)

data class ReaderState(
    val roundId: Long? = null,
    val cards: List<QuoteCard> = emptyList(),
    val readCardIds: Set<String> = emptySet(),
    val currentIndex: Int = 0,
    val isComplete: Boolean = false,
)

data class InstalledContentState(
    val contentVersion: String,
    val publishedAt: String,
    val lastCheckedAt: Long?,
    val lastUpdatedAt: Long,
)

data class PersonalNote(
    val id: Long,
    val cardId: String?,
    val title: String?,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class RecommendationSettings(
    val requiresOnboarding: Boolean = true,
    val selected: Set<InterestCategory> = emptySet(),
    val reducedCount: Int = 0,
)
