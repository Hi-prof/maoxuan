package com.xuhuangbin.xinghuozhaidu.data

import com.xuhuangbin.xinghuozhaidu.data.local.ReadingRoundItemEntity
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
            furthestPosition = 1,
            rankedActiveCardIds = listOf("y", "x", "c", "b", "a"),
        )

        assertEquals(listOf("a", "b"), plan.items.take(2).map { it.cardId })
        assertEquals(listOf("y", "x", "c"), plan.items.drop(2).map { it.cardId })
        assertEquals(1, plan.currentPosition)
        assertEquals(1, plan.furthestPosition)
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
            furthestPosition = 2,
            rankedActiveCardIds = listOf("d", "a"),
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
            furthestPosition = 0,
            rankedActiveCardIds = listOf("new"),
        )

        assertEquals(0, plan.currentPosition)
        assertEquals("new", plan.items.single().cardId)
        assertTrue("new" in plan.addedCardIds)
    }

    @Test
    fun reranksOnlyTheUnseenTailAndPreservesReadTimestamps() {
        val existing = items("a", "b", "c", "d", "e").map { item ->
            if (item.cardId == "c") item.copy(readAt = 300L) else item
        }

        val plan = ReadingRoundPlanner.reconcile(
            roundId = 7,
            existingItems = existing,
            currentPosition = 1,
            furthestPosition = 2,
            rankedActiveCardIds = listOf("e", "d", "c", "b", "a"),
        )

        assertEquals(listOf("a", "b", "c", "e", "d"), plan.items.map { it.cardId })
        assertEquals(300L, plan.items.first { it.cardId == "c" }.readAt)
        assertEquals(1, plan.currentPosition)
        assertEquals(2, plan.furthestPosition)
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
