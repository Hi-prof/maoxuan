package com.xuhuangbin.xinghuozhaidu.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.xuhuangbin.xinghuozhaidu.domain.model.CardInterpretation
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.domain.model.ReaderState
import com.xuhuangbin.xinghuozhaidu.ui.theme.XinghuoTheme
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderRecommendationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reduceCurrentCardAdvancesAfterFeedbackIsSaved() {
        val first = testCard(id = "first", quote = "第一张卡片。")
        val second = testCard(id = "second", quote = "第二张卡片。")
        var position by mutableIntStateOf(0)
        var reducedCardId: String? = null
        composeRule.setContent {
            XinghuoTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    ReaderScreen(
                        state = ReaderState(
                            roundId = 1L,
                            cards = listOf(first, second),
                            currentIndex = position,
                        ),
                        isLoading = false,
                        errorMessage = null,
                        onRetry = {},
                        onSearch = {},
                        onPositionChanged = { position = it },
                        onRead = {},
                        onLike = {},
                        onFavorite = {},
                        onNote = {},
                        onNewRound = {},
                        onReduceSimilar = { cardId, onSaved ->
                            reducedCardId = cardId
                            onSaved()
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("更多阅读操作").performClick()
        composeRule.onNodeWithText("减少此类").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) { position == 1 }
        composeRule.onNodeWithText(second.quote).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(first.id, reducedCardId) }
    }

    @Test
    fun reduceMenuIsDisabledOnRoundCompletionPage() {
        val card = testCard(id = "completion", quote = "实践是检验真理的标准。")
        composeRule.setContent {
            XinghuoTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    ReaderScreen(
                        state = ReaderState(
                            roundId = 1L,
                            cards = listOf(card),
                            readCardIds = setOf(card.id),
                            isComplete = true,
                        ),
                        isLoading = false,
                        errorMessage = null,
                        onRetry = {},
                        onSearch = {},
                        onPositionChanged = {},
                        onRead = {},
                        onLike = {},
                        onFavorite = {},
                        onNote = {},
                        onNewRound = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("更多阅读操作").assertIsEnabled()
        composeRule.onNodeWithText(card.quote).performTouchInput { swipeUp() }
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithText("本轮已读完").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("更多阅读操作").assertIsNotEnabled()
    }

    private fun testCard(id: String, quote: String) = QuoteCard(
        id = id,
        revision = 1,
        quote = quote,
        series = "毛泽东选集",
        volume = "第一卷",
        workTitle = "实践论",
        authoredAt = "1937-07",
        themes = listOf("实践"),
        interpretation = CardInterpretation("启示", "解读"),
        historicalEvent = "历史事件",
        background = "时代背景",
        story = "延伸故事",
        imagePath = "",
        sources = emptyList(),
        isWithdrawn = false,
        isLiked = false,
        isFavorited = false,
        likedAt = null,
        favoritedAt = null,
    )
}
