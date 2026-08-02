package com.xuhuangbin.xinghuozhaidu.data

import com.xuhuangbin.xinghuozhaidu.data.local.ReadingRoundItemEntity
import kotlin.math.abs

data class ReadingRoundPlan(
    val items: List<ReadingRoundItemEntity>,
    val currentPosition: Int,
    val furthestPosition: Int,
    val addedCardIds: Set<String>,
)

internal object ReadingRoundPlanner {
    fun reconcile(
        roundId: Long,
        existingItems: List<ReadingRoundItemEntity>,
        currentPosition: Int,
        furthestPosition: Int,
        rankedActiveCardIds: List<String>,
    ): ReadingRoundPlan {
        val activeCardIds = rankedActiveCardIds.toSet()
        val activeExisting = existingItems.filter { it.cardId in activeCardIds }
        val existingByCardId = existingItems.associateBy(ReadingRoundItemEntity::cardId)
        val existingCardIds = existingItems.mapTo(mutableSetOf(), ReadingRoundItemEntity::cardId)
        val additions = activeCardIds - existingCardIds

        val boundedCurrent = currentPosition.coerceIn(0, (existingItems.size - 1).coerceAtLeast(0))
        val anchor = existingItems
            .filter { it.cardId in activeCardIds }
            .minWithOrNull(
                compareBy<ReadingRoundItemEntity> { abs(it.position - boundedCurrent) }
                    .thenBy { if (it.position <= boundedCurrent) 0 else 1 },
            )
        val lockedBoundary = maxOf(furthestPosition, anchor?.position ?: -1)
        val locked = activeExisting.filter { it.position <= lockedBoundary }
        val lockedIds = locked.mapTo(mutableSetOf(), ReadingRoundItemEntity::cardId)
        val tail = rankedActiveCardIds.distinct().filterNot(lockedIds::contains).map { cardId ->
            existingByCardId[cardId]
                ?: ReadingRoundItemEntity(roundId = roundId, position = -1, cardId = cardId)
        }
        val rebuilt = (locked + tail).mapIndexed { index, item -> item.copy(position = index) }
        val selectedCardId = anchor?.cardId ?: rebuilt.firstOrNull()?.cardId
        val selectedIndex = rebuilt.indexOfFirst { it.cardId == selectedCardId }.coerceAtLeast(0)
        return ReadingRoundPlan(
            items = rebuilt,
            currentPosition = selectedIndex,
            furthestPosition = maxOf(selectedIndex, locked.lastIndex),
            addedCardIds = additions,
        )
    }
}
