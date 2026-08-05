package com.xuhuangbin.xinghuozhaidu.ui.components

import com.xuhuangbin.xinghuozhaidu.domain.model.CardInterpretation
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import org.junit.Assert.assertEquals
import org.junit.Test

class CardSummaryListTest {
    @Test
    fun indexLabelsCoverVolumesAndKnownSeries() {
        assertEquals("卷一", summaryIndexLabel(card("毛泽东选集", "第一卷")))
        assertEquals("卷二", summaryIndexLabel(card("毛泽东选集", "第二卷")))
        assertEquals("卷三", summaryIndexLabel(card("毛泽东选集", "第三卷")))
        assertEquals("卷四", summaryIndexLabel(card("毛泽东选集", "第四卷")))
        assertEquals("毛选", summaryIndexLabel(card("毛泽东选集", "增补")))
        assertEquals("诗词", summaryIndexLabel(card("毛泽东诗词", "诗词")))
        assertEquals("马原", summaryIndexLabel(card("马原思考", "卡尔·马克思")))
        assertEquals("名言", summaryIndexLabel(card("名人名言", "鲁迅")))
        assertEquals("自定", summaryIndexLabel(card("自定义系列", "卷次")))
        assertEquals("文摘", summaryIndexLabel(card(" ", "卷次")))
    }

    @Test
    fun dateLabelsUseYearForModernDatesAndPreserveAncientLabels() {
        assertEquals("1930", summaryDateLabel("1930"))
        assertEquals("1930", summaryDateLabel("1930-05"))
        assertEquals("1930", summaryDateLabel("1930-05-12"))
        assertEquals("前4世纪", summaryDateLabel("前4世纪"))
        assertEquals("约公元前5世纪", summaryDateLabel("约公元前5世纪"))
    }

    private fun card(series: String, volume: String) = QuoteCard(
        id = "$series-$volume",
        revision = 1,
        quote = "测试名言",
        series = series,
        volume = volume,
        workTitle = "测试篇名",
        authoredAt = "1930-05",
        themes = emptyList(),
        interpretation = CardInterpretation("启示", "解读"),
        historicalEvent = "历史节点",
        background = "背景",
        story = "故事",
        imagePath = "",
        sources = emptyList(),
        isWithdrawn = false,
        isLiked = false,
        isFavorited = false,
        likedAt = null,
        favoritedAt = null,
    )
}
