package com.xuhuangbin.xinghuozhaidu.data

import com.xuhuangbin.xinghuozhaidu.data.local.ReadingRoundItemEntity
import kotlin.math.abs
import kotlin.random.Random

data class ReadingRoundPlan(
    val items: List<ReadingRoundItemEntity>,
    val currentPosition: Int,
    val addedCardIds: Set<String>,
)

internal object ReadingRoundPlanner {
    fun reconcile(
        roundId: Long,
        existingItems: List<ReadingRoundItemEntity>,
        currentPosition: Int,
        activeCardIds: Set<String>,
        random: Random,
    ): ReadingRoundPlan {
        val activeExisting = existingItems.filter { it.cardId in activeCardIds }
        val existingCardIds = activeExisting.mapTo(mutableSetOf()) { it.cardId }
        val additions = (activeCardIds - existingCardIds).shuffled(random)

        val boundedCurrent = currentPosition.coerceIn(0, (existingItems.size - 1).coerceAtLeast(0))
        val anchor = existingItems
            .filter { it.cardId in activeCardIds }
            .minWithOrNull(
                compareBy<ReadingRoundItemEntity> { abs(it.position - boundedCurrent) }
                    .thenBy { if (it.position <= boundedCurrent) 0 else 1 },
            )
        val anchorPosition = anchor?.position
        val history = if (anchorPosition == null) {
            emptyList()
        } else {
            activeExisting.filter { it.position <= anchorPosition }
        }
        val future = if (anchorPosition == null) {
            mutableListOf()
        } else {
            activeExisting.filter { it.position > anchorPosition }.toMutableList()
        }
        additions.forEach { cardId ->
            future.add(
                random.nextInt(future.size + 1),
                ReadingRoundItemEntity(roundId = roundId, position = -1, cardId = cardId),
            )
        }
        val rebuilt = (history + future).mapIndexed { index, item -> item.copy(position = index) }
        val selectedCardId = anchor?.cardId ?: rebuilt.firstOrNull()?.cardId
        val selectedIndex = rebuilt.indexOfFirst { it.cardId == selectedCardId }.coerceAtLeast(0)
        return ReadingRoundPlan(
            items = rebuilt,
            currentPosition = selectedIndex,
            addedCardIds = additions.toSet(),
        )
    }
}
