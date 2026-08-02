package com.xuhuangbin.xinghuozhaidu.domain.recommendation

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationRankerTest {
    @Test
    fun profileUsesExplicitSignalsAndCapsRepeatedEvidence() {
        val growthCards = (1..20).map { testCard("g$it") }
        val signals = RecommendationSignals(
            selected = setOf(InterestCategory.SelfGrowth),
            likedCards = growthCards,
            favoriteCards = growthCards,
            linkedNoteCards = growthCards + growthCards,
            reducedCards = growthCards,
        )

        val profile = RecommendationProfileBuilder.build(signals)

        assertEquals(11, profile.weightOf(InterestCategory.SelfGrowth))
        assertEquals(0, profile.weightOf(InterestCategory.Learning))
    }

    @Test
    fun profileAppliesEachSignalAtItsDocumentedStrength() {
        val growth = testCard("growth")

        assertEquals(
            8,
            RecommendationProfileBuilder.build(
                RecommendationSignals(selected = setOf(InterestCategory.SelfGrowth)),
            ).weightOf(InterestCategory.SelfGrowth),
        )
        assertEquals(
            1,
            RecommendationProfileBuilder.build(
                RecommendationSignals(likedCards = listOf(growth)),
            ).weightOf(InterestCategory.SelfGrowth),
        )
        assertEquals(
            2,
            RecommendationProfileBuilder.build(
                RecommendationSignals(favoriteCards = listOf(growth)),
            ).weightOf(InterestCategory.SelfGrowth),
        )
        assertEquals(
            2,
            RecommendationProfileBuilder.build(
                RecommendationSignals(linkedNoteCards = listOf(growth, growth)),
            ).weightOf(InterestCategory.SelfGrowth),
        )
        assertEquals(
            -6,
            RecommendationProfileBuilder.build(
                RecommendationSignals(reducedCards = listOf(growth)),
            ).weightOf(InterestCategory.SelfGrowth),
        )
    }

    @Test
    fun rankIsDeterministicCompleteAndDuplicateFree() {
        val cards = (1..30).map { index ->
            if (index <= 20) testCard("growth-$index")
            else testCard("life-$index", themes = listOf("人生"))
        }
        val profile = RecommendationProfileBuilder.build(
            RecommendationSignals(selected = setOf(InterestCategory.SelfGrowth)),
        )

        val first = RecommendationRanker.rank(cards, profile, Random(42))
        val second = RecommendationRanker.rank(cards, profile, Random(42))

        assertEquals(first, second)
        assertEquals(cards.size, first.size)
        assertEquals(cards.map { it.id }.toSet(), first.toSet())
    }

    @Test
    fun personalizedLaneIncludesRegularExploration() {
        val cards = (1..25).map { index ->
            if (index <= 20) testCard("growth-$index")
            else testCard("life-$index", themes = listOf("人生"))
        }
        val profile = RecommendationProfileBuilder.build(
            RecommendationSignals(selected = setOf(InterestCategory.SelfGrowth)),
        )

        val ranked = RecommendationRanker.rank(cards, profile, Random(7))

        assertTrue(ranked.take(5).any { it.startsWith("life-") })
        assertTrue(ranked.take(10).count { it.startsWith("life-") } >= 2)
    }

    @Test
    fun diversityUsesExplorationWhenPersonalizedCardsWouldRepeatThreeTimes() {
        val cards = (1..8).map { index ->
            testCard("growth-$index", series = "成长系列")
        } + (1..4).map { index ->
            testCard("life-$index", series = "人生系列-$index", themes = listOf("人生"))
        }
        val profile = RecommendationProfileBuilder.build(
            RecommendationSignals(selected = setOf(InterestCategory.SelfGrowth)),
        )

        val ranked = RecommendationRanker.rank(cards, profile, Random(11))

        assertTrue(
            ranked.take(10).windowed(3).none { window ->
                window.all { it.startsWith("growth-") }
            },
        )
    }

    @Test
    fun reducedCategoryFallsBehindNeutralExploration() {
        val reduced = testCard("reduced", themes = listOf("关系"))
        val neutral = testCard("neutral", series = "未知系列", themes = listOf("未知"))
        val profile = RecommendationProfileBuilder.build(
            RecommendationSignals(reducedCards = listOf(reduced)),
        )

        val ranked = RecommendationRanker.rank(listOf(reduced, neutral), profile, Random(1))

        assertEquals(listOf("neutral", "reduced"), ranked)
    }

    @Test
    fun emptyProfileStillReturnsAllCards() {
        val cards = listOf(
            testCard("a", series = "毛泽东诗词", themes = listOf("奋斗")),
            testCard("b", series = "马原思考", themes = listOf("劳动")),
            testCard("c", series = "名人名言", themes = listOf("人生")),
        )

        val ranked = RecommendationRanker.rank(cards, RecommendationProfile.Empty, Random(3))

        assertEquals(cards.map { it.id }.toSet(), ranked.toSet())
    }
}
