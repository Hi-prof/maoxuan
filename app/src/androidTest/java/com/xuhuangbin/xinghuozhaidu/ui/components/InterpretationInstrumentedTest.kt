package com.xuhuangbin.xinghuozhaidu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.xuhuangbin.xinghuozhaidu.domain.model.CardInterpretation
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.detail.CardDetailScreen
import com.xuhuangbin.xinghuozhaidu.ui.theme.XinghuoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InterpretationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun interpretationActionIsImmediatelyLeftOfBackgroundAtMinimumWidth() {
        composeRule.setContent {
            XinghuoTheme {
                Box(Modifier.size(width = 360.dp, height = 96.dp).padding(horizontal = 16.dp)) {
                    CardActions(
                        card = testCard(),
                        isFlipped = false,
                        onInterpret = {},
                        onFlip = {},
                        onLike = {},
                        onFavorite = {},
                        onNote = {},
                        onShare = {},
                    )
                }
            }
        }

        val interpretationBounds = composeRule.onNodeWithText("解读")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        composeRule.onNodeWithContentDescription("写笔记").assertIsDisplayed()
        val backgroundBounds = composeRule.onNodeWithText("读背景")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue("解读必须位于读背景左侧", interpretationBounds.right <= backgroundBounds.left)
    }

    @Test
    fun sheetScrollsAndDismissesWithoutChangingBackState() {
        val card = testCard(longInterpretation = true)

        composeRule.setContent {
            XinghuoTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    CardDetailScreen(
                        card = card,
                        onBack = {},
                        onLike = {},
                        onFavorite = {},
                        onNote = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("读背景").performClick()
        composeRule.onNodeWithText("返回正面").assertIsDisplayed()
        composeRule.onNodeWithText("解读").performClick()
        composeRule.onNodeWithText("核心意思").assertIsDisplayed()
        repeat(3) {
            composeRule.onNodeWithTag("interpretationContent").performTouchInput { swipeUp() }
        }
        composeRule.onNodeWithText("现实启示").assertIsDisplayed()
        composeRule.onNodeWithText(card.interpretation.contemporaryRelevance)
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription("关闭解读").performClick()
        composeRule.onNodeWithText("返回正面").assertIsDisplayed()
    }

    private fun testCard(longInterpretation: Boolean = false) = QuoteCard(
        id = "8a3e739a-9b89-5840-a47a-6bb2c6435c1c",
        revision = 2,
        quote = "没有调查，没有发言权。",
        series = "毛泽东选集",
        volume = "第一卷",
        workTitle = "反对本本主义",
        authoredAt = "1930-05",
        themes = listOf("调查研究"),
        interpretation = CardInterpretation(
            coreMeaning = if (longInterpretation) {
                "对问题的判断必须建立在实际调查上。".repeat(8)
            } else {
                "对问题的判断必须建立在实际调查上。"
            },
            keyPoint = "这句话批评的是脱离实际的发言和决策，不是要求对所有问题无限期地搜集材料。".repeat(3),
            contemporaryRelevance = "在工作中，可以先明确问题，再收集直接证据，并根据新事实修正原有判断。这是现实启示的结尾。",
        ),
        contextExcerpt = "调查就是解决问题。",
        background = "这篇文章围绕调查研究与实际工作的关系展开。",
        story = null,
        imagePath = "",
        sources = emptyList(),
        isWithdrawn = false,
        isLiked = false,
        isFavorited = false,
        likedAt = null,
        favoritedAt = null,
    )
}
