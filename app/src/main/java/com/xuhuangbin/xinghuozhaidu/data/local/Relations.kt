package com.xuhuangbin.xinghuozhaidu.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class CardWithSources(
    @Embedded val card: CardEntity,
    @Relation(parentColumn = "id", entityColumn = "cardId")
    val sources: List<CardSourceEntity>,
)
