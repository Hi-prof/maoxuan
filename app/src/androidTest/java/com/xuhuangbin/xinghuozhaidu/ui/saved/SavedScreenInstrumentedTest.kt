package com.xuhuangbin.xinghuozhaidu.ui.saved

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xuhuangbin.xinghuozhaidu.domain.model.CardInterpretation
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.theme.XinghuoTheme
import org.junit.Rule
import org.junit.Test

class SavedScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun segmentIconsAndCountsTrackTheCurrentCollection() {
        composeRule.setContent {
            XinghuoTheme {
                SavedScreen(
                    favorites = listOf(card("favorite", "收藏内容")),
                    liked = listOf(card("liked-1", "点赞内容一"), card("liked-2", "点赞内容二")),
                    onCardClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("收藏 分段").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("点赞 分段").assertIsDisplayed()
        composeRule.onNodeWithText("收藏 1 条").assertIsDisplayed()
        composeRule.onNodeWithText("收藏内容").assertIsDisplayed()

        composeRule.onNodeWithText("点赞").performClick()
        composeRule.onNodeWithText("点赞 2 条").assertIsDisplayed()
        composeRule.onNodeWithText("点赞内容一").assertIsDisplayed()
    }

    private fun card(id: String, quote: String) = QuoteCard(
        id = id,
        revision = 1,
        quote = quote,
        series = "名人名言",
        volume = "鲁迅",
        workTitle = "文集",
        authoredAt = "1925",
        themes = emptyList(),
        interpretation = CardInterpretation("启示", "解读"),
        historicalEvent = "历史节点",
        background = "背景",
        story = "故事",
        imagePath = "",
        sources = emptyList(),
        isWithdrawn = false,
        isLiked = true,
        isFavorited = true,
        likedAt = 1L,
        favoritedAt = 1L,
    )
}
