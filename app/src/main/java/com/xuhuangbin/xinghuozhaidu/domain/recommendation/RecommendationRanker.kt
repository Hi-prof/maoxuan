package com.xuhuangbin.xinghuozhaidu.domain.recommendation

import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import kotlin.math.ln
import kotlin.random.Random

object RecommendationRanker {
    fun rank(
        cards: List<QuoteCard>,
        profile: RecommendationProfile,
        random: Random,
    ): List<String> {
        val ranked = cards.distinctBy(QuoteCard::id).map { card ->
            val categories = InterestTaxonomy.categoriesFor(card)
            val score = categories.sumOf(profile::weightOf)
            RankedCard(
                card = card,
                categories = categories,
                score = score,
                primaryCategory = categories.maxByOrNull(profile::weightOf),
            )
        }
        val positive = ranked.filter { it.score > 0 }
            .sortedBy { candidate ->
                -ln(random.nextDouble().coerceAtLeast(MIN_RANDOM)) / candidate.score
            }
            .toMutableList()
        val neutral = ranked.filter { it.score == 0 }.shuffled(random).toMutableList()
        val discouraged = ranked.filter { it.score < 0 }
            .shuffled(random)
            .sortedByDescending(RankedCard::score)
            .toMutableList()

        val result = mutableListOf<RankedCard>()
        var personalizedCount = 0
        while (positive.isNotEmpty() || neutral.isNotEmpty() || discouraged.isNotEmpty()) {
            val explorationAvailable = neutral.isNotEmpty() || discouraged.isNotEmpty()
            val preferredLane = when {
                positive.isNotEmpty() && (personalizedCount < PERSONALIZED_BLOCK || !explorationAvailable) -> positive
                neutral.isNotEmpty() -> neutral
                discouraged.isNotEmpty() -> discouraged
                else -> positive
            }
            val fallbackLanes = when (preferredLane) {
                positive -> listOf(neutral, discouraged)
                neutral -> listOf(positive, discouraged)
                else -> listOf(positive, neutral)
            }
            val lane = (listOf(preferredLane) + fallbackLanes)
                .firstOrNull { candidates -> diverseCandidateIndex(candidates, result) != null }
                ?: preferredLane
            val candidateIndex = diverseCandidateIndex(lane, result) ?: 0
            result += lane.removeAt(candidateIndex)
            personalizedCount = if (lane === positive) personalizedCount + 1 else 0
        }
        return result.map { it.card.id }
    }

    private fun diverseCandidateIndex(
        candidates: List<RankedCard>,
        result: List<RankedCard>,
    ): Int? {
        if (candidates.isEmpty()) return null
        if (result.size < 2) return 0
        val previous = result.takeLast(2)
        return candidates.indexOfFirst { candidate ->
            val repeatsSeries = previous.all { it.card.series == candidate.card.series }
            val repeatsCategory = candidate.primaryCategory != null &&
                previous.all { it.primaryCategory == candidate.primaryCategory }
            !repeatsSeries && !repeatsCategory
        }.takeIf { it >= 0 }
    }

    private data class RankedCard(
        val card: QuoteCard,
        val categories: Set<InterestCategory>,
        val score: Int,
        val primaryCategory: InterestCategory?,
    )

    private const val PERSONALIZED_BLOCK = 4
    private const val MIN_RANDOM = 1e-12
}
