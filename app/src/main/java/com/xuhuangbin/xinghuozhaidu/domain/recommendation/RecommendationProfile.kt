package com.xuhuangbin.xinghuozhaidu.domain.recommendation

import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard

data class RecommendationSignals(
    val selected: Set<InterestCategory> = emptySet(),
    val likedCards: List<QuoteCard> = emptyList(),
    val favoriteCards: List<QuoteCard> = emptyList(),
    val linkedNoteCards: List<QuoteCard> = emptyList(),
    val reducedCards: List<QuoteCard> = emptyList(),
)

class RecommendationProfile internal constructor(
    private val weights: Map<InterestCategory, Int>,
) {
    fun weightOf(category: InterestCategory): Int = weights[category] ?: 0

    val isEmpty: Boolean get() = weights.values.all { it == 0 }

    companion object {
        val Empty = RecommendationProfile(emptyMap())
    }
}

object RecommendationProfileBuilder {
    fun build(input: RecommendationSignals): RecommendationProfile {
        val weights = InterestCategory.entries.associateWithTo(mutableMapOf()) { category ->
            if (category in input.selected) SELECTED_WEIGHT else 0
        }
        addCardEvidence(weights, input.likedCards, amount = 1, cap = 3)
        addCardEvidence(weights, input.favoriteCards, amount = 2, cap = 6)
        addCardEvidence(weights, input.linkedNoteCards, amount = 2, cap = 6)
        addCardEvidence(weights, input.reducedCards, amount = -6, cap = 12)
        return RecommendationProfile(weights.filterValues { it != 0 })
    }

    private fun addCardEvidence(
        target: MutableMap<InterestCategory, Int>,
        cards: List<QuoteCard>,
        amount: Int,
        cap: Int,
    ) {
        val counts = mutableMapOf<InterestCategory, Int>()
        cards.distinctBy(QuoteCard::id).forEach { card ->
            InterestTaxonomy.categoriesFor(card).forEach { category ->
                counts[category] = (counts[category] ?: 0) + 1
            }
        }
        counts.forEach { (category, count) ->
            val contribution = (count * amount).coerceIn(-cap, cap)
            target[category] = target.getValue(category) + contribution
        }
    }

    private const val SELECTED_WEIGHT = 8
}
