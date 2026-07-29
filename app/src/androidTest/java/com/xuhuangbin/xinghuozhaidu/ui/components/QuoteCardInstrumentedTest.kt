package com.xuhuangbin.xinghuozhaidu.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.xuhuangbin.xinghuozhaidu.domain.model.CardInterpretation
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.theme.XinghuoTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuoteCardInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactCardKeepsNinetyCharacterQuoteAndSourceVisible() {
        val quote = "星火".repeat(45)
        val source = "《星星之火，可以燎原》"
        val card = QuoteCard(
            id = "8a3e739a-9b89-5840-a47a-6bb2c6435c1c",
            revision = 1,
            quote = quote,
            series = "毛泽东选集",
            volume = "第一卷",
            workTitle = "星星之火，可以燎原",
            authoredAt = "1930-01-05",
            themes = listOf("前途"),
            interpretation = testInterpretation(),
            historicalEvent = "1930年1月5日，毛泽东写信分析革命形势。",
            background = "文章回应红军内部对革命前途的悲观估计。",
            story = "这封信后来以今天通行的篇名公开发表。",
            imagePath = "",
            sources = emptyList(),
            isWithdrawn = false,
            isLiked = false,
            isFavorited = false,
            likedAt = null,
            favoritedAt = null,
        )

        composeRule.setContent {
            XinghuoTheme {
                FlippableQuoteCard(
                    card = card,
                    flipped = false,
                    onFlippedChange = {},
                    modifier = Modifier
                        .size(width = 360.dp, height = 357.dp)
                        .testTag("compactQuoteCard")
                )
            }
        }

        val cardBounds = composeRule.onNodeWithTag("compactQuoteCard")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val quoteBounds = composeRule.onNodeWithText(quote)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val sourceBounds = composeRule.onNodeWithText(source)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue("quote must not overlap source", quoteBounds.bottom <= sourceBounds.top)
        assertTrue("source must remain inside card", sourceBounds.bottom <= cardBounds.bottom)
    }

    @Test
    fun horizontalSwipesFlipBothWaysAndShortDragSpringsBack() {
        val card = testCard()
        var flipped by mutableStateOf(false)

        composeRule.setContent {
            XinghuoTheme {
                FlippableQuoteCard(
                    card = card,
                    flipped = flipped,
                    onFlippedChange = { flipped = it },
                    modifier = Modifier
                        .size(width = 360.dp, height = 520.dp)
                        .testTag("swipeQuoteCard"),
                )
            }
        }

        composeRule.onNodeWithTag("swipeQuoteCard").performTouchInput {
            down(center)
            advanceEventTime(100)
            up()
        }
        composeRule.waitForIdle()
        assertFalse("tapping the card must not flip it", flipped)

        composeRule.onNodeWithTag("swipeQuoteCard").performTouchInput { swipeLeft() }
        composeRule.waitUntil(timeoutMillis = 3_000) { flipped }
        composeRule.onNodeWithText("解读").assertIsDisplayed()
        composeRule.onNodeWithText("启示").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回正面").assertDoesNotExist()

        composeRule.onNodeWithTag("swipeQuoteCard").performTouchInput { swipeRight() }
        composeRule.waitUntil(timeoutMillis = 3_000) { !flipped }
        composeRule.onNodeWithText(card.quote).assertIsDisplayed()

        composeRule.onNodeWithTag("swipeQuoteCard").performTouchInput {
            down(center)
            advanceEventTime(120)
            moveBy(Offset(center.x * 0.16f, 0f))
            advanceEventTime(400)
            up()
        }
        composeRule.waitForIdle()

        assertFalse("a short, slow drag must return to the front", flipped)
        composeRule.onNodeWithText(card.quote).assertIsDisplayed()

        composeRule.onNodeWithTag("swipeQuoteCard").performTouchInput { swipeRight() }
        composeRule.waitUntil(timeoutMillis = 3_000) { flipped }
        composeRule.onNodeWithText("解读").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回正面").assertDoesNotExist()

        composeRule.onNodeWithTag("swipeQuoteCard").performTouchInput { swipeLeft() }
        composeRule.waitUntil(timeoutMillis = 3_000) { !flipped }
        composeRule.onNodeWithText(card.quote).assertIsDisplayed()
    }

    private fun testCard() = QuoteCard(
        id = "8a3e739a-9b89-5840-a47a-6bb2c6435c1c",
        revision = 1,
        quote = "没有调查，没有发言权。",
        series = "毛泽东选集",
        volume = "第一卷",
        workTitle = "反对本本主义",
        authoredAt = "1930-05",
        themes = listOf("调查研究"),
        interpretation = testInterpretation(),
        historicalEvent = "1930年5月，毛泽东针对脱离实际的倾向写下这篇文章。",
        background = "这篇文章围绕调查研究与实际工作的关系展开。",
        story = "正文进一步讨论怎样开调查会和记录问题。",
        imagePath = "",
        sources = emptyList(),
        isWithdrawn = false,
        isLiked = false,
        isFavorited = false,
        likedAt = null,
        favoritedAt = null,
    )

    private fun testInterpretation() = CardInterpretation(
        inspiration = "先了解事实，再作决定。",
        explanation = "从实际情况出发认识问题，调查是形成判断的前提。",
    )
}
