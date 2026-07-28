package com.xuhuangbin.xinghuozhaidu.data

import com.xuhuangbin.xinghuozhaidu.data.local.ReadingRoundItemEntity
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingRoundPlannerTest {
    @Test
    fun insertsNewCardsOnlyAfterCurrentHistory() {
        val existing = items("a", "b", "c")

        val plan = ReadingRoundPlanner.reconcile(
            roundId = 7,
            existingItems = existing,
            currentPosition = 1,
            activeCardIds = setOf("a", "b", "c", "x", "y"),
            random = Random(42),
        )

        assertEquals(listOf("a", "b"), plan.items.take(2).map { it.cardId })
        assertEquals(1, plan.currentPosition)
        assertEquals(setOf("x", "y"), plan.addedCardIds)
        assertEquals(5, plan.items.map { it.cardId }.toSet().size)
    }

    @Test
    fun selectsNearestActiveCardWhenCurrentCardWasWithdrawn() {
        val existing = items("a", "b", "c", "d")

        val plan = ReadingRoundPlanner.reconcile(
            roundId = 8,
            existingItems = existing,
            currentPosition = 2,
            activeCardIds = setOf("a", "d"),
            random = Random(1),
        )

        assertEquals(listOf("a", "d"), plan.items.map { it.cardId })
        assertEquals(1, plan.currentPosition)
        assertEquals("d", plan.items[plan.currentPosition].cardId)
    }

    @Test
    fun keepsReadTimestampsAndCreatesAValidPositionFromOnlyAdditions() {
        val existing = listOf(
            ReadingRoundItemEntity(9, 0, "withdrawn", readAt = 1234),
        )

        val plan = ReadingRoundPlanner.reconcile(
            roundId = 9,
            existingItems = existing,
            currentPosition = 0,
            activeCardIds = setOf("new"),
            random = Random(2),
        )

        assertEquals(0, plan.currentPosition)
        assertEquals("new", plan.items.single().cardId)
        assertTrue("new" in plan.addedCardIds)
    }

    private fun items(vararg ids: String) = ids.mapIndexed { index, id ->
        ReadingRoundItemEntity(
            roundId = 7,
            position = index,
            cardId = id,
            readAt = if (index == 0) 100L else null,
        )
    }
}
