package com.xuhuangbin.xinghuozhaidu.domain.recommendation

import com.xuhuangbin.xinghuozhaidu.domain.model.CardInterpretation
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard

internal fun testCard(
    id: String,
    series: String = "名人名言",
    themes: List<String> = listOf("成长"),
    liked: Boolean = false,
    favorited: Boolean = false,
) = QuoteCard(
    id = id,
    revision = 1,
    quote = "引文$id",
    series = series,
    volume = "卷",
    workTitle = "篇名$id",
    authoredAt = "2026",
    themes = themes,
    interpretation = CardInterpretation("启示", "解读"),
    historicalEvent = "历史事件",
    background = "背景",
    story = "故事",
    imagePath = "",
    sources = emptyList(),
    isWithdrawn = false,
    isLiked = liked,
    isFavorited = favorited,
    likedAt = null,
    favoritedAt = null,
)
