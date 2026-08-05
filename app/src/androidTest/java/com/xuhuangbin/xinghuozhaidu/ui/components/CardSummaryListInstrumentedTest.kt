package com.xuhuangbin.xinghuozhaidu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.xuhuangbin.xinghuozhaidu.domain.model.CardInterpretation
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.theme.XinghuoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CardSummaryListInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactWidthKeepsIndexContentMetadataAndStatusSeparated() {
        val card = testCard()
        val title = "《${card.workTitle}》"
        val metadata = "前4世纪 · 第四卷"
        composeRule.setContent {
            XinghuoTheme {
                Box(Modifier.size(width = 360.dp, height = 220.dp)) {
                    CardSummaryList(
                        cards = listOf(card),
                        emptyText = "无内容",
                        onCardClick = {},
                    )
                }
            }
        }

        val indexBounds = composeRule.onNodeWithText("卷四", useUnmergedTree = true).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val quoteBounds = composeRule.onNodeWithText(card.quote, useUnmergedTree = true).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val titleBounds = composeRule.onNodeWithText(title, useUnmergedTree = true).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val metadataBounds = composeRule.onNodeWithText(metadata, useUnmergedTree = true).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithText("已下架").assertIsDisplayed()

        assertTrue(
            "index must stay left of quote: index=$indexBounds quote=$quoteBounds",
            indexBounds.right < quoteBounds.left,
        )
        assertTrue("quote must stay above title", quoteBounds.bottom <= titleBounds.top)
        assertTrue("title must stay above metadata", titleBounds.bottom <= metadataBounds.top)
    }

    private fun testCard() = QuoteCard(
        id = "summary-card",
        revision = 1,
        quote = "道路是曲折的，前途是光明的。重要的是根据事实持续行动，并在变化中修正判断。",
        series = "毛泽东选集",
        volume = "第四卷",
        workTitle = "关于重庆谈判",
        authoredAt = "前4世纪",
        themes = listOf("实践"),
        interpretation = CardInterpretation("启示", "解读"),
        historicalEvent = "历史节点",
        background = "背景",
        story = "故事",
        imagePath = "",
        sources = emptyList(),
        isWithdrawn = true,
        isLiked = false,
        isFavorited = true,
        likedAt = null,
        favoritedAt = 1L,
    )
}
