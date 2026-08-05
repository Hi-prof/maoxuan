package com.xuhuangbin.xinghuozhaidu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.xuhuangbin.xinghuozhaidu.domain.model.CardInterpretation
import com.xuhuangbin.xinghuozhaidu.domain.model.CardSource
import com.xuhuangbin.xinghuozhaidu.domain.model.ImageAttribution
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.domain.model.ReaderState
import com.xuhuangbin.xinghuozhaidu.ui.detail.CardDetailScreen
import com.xuhuangbin.xinghuozhaidu.ui.reader.ReaderScreen
import com.xuhuangbin.xinghuozhaidu.ui.theme.XinghuoTheme
import org.junit.Rule
import org.junit.Test

class InterpretationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun actionsStayOnOneRowAtCompactViewportWidth() {
        composeRule.setContent {
            XinghuoTheme {
                Box(Modifier.size(width = 320.dp, height = 96.dp).padding(horizontal = 16.dp)) {
                    CardActions(
                        card = testCard(),
                        onBackground = {},
                        onLike = {},
                        onFavorite = {},
                        onNote = {},
                        onShare = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("解读").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("写笔记").assertIsDisplayed()
        composeRule.onNodeWithText("读背景").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回正面").assertDoesNotExist()
        val likeCenterY = composeRule.onNodeWithContentDescription("点赞")
            .fetchSemanticsNode().boundsInRoot.center.y
        val backgroundCenterY = composeRule.onNodeWithText("读背景")
            .fetchSemanticsNode().boundsInRoot.center.y
        check(kotlin.math.abs(likeCenterY - backgroundCenterY) <= 1f) {
            "点赞与读背景未在同一行：likeCenterY=$likeCenterY, backgroundCenterY=$backgroundCenterY"
        }
        val shareRight = composeRule.onNodeWithContentDescription("生成图片并分享")
            .fetchSemanticsNode().boundsInRoot.right
        val backgroundLeft = composeRule.onNodeWithText("读背景")
            .fetchSemanticsNode().boundsInRoot.left
        check(shareRight <= backgroundLeft) {
            "分享与读背景发生重叠：shareRight=$shareRight, backgroundLeft=$backgroundLeft"
        }
    }

    @Test
    fun backgroundSheetScrollsAndDismissesWithoutChangingInterpretationFace() {
        val card = testCard(longBackground = true)

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

        composeRule.onNodeWithText(card.quote).performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("启示").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回正面").assertDoesNotExist()

        composeRule.onNodeWithText("读背景").performClick()
        composeRule.onNodeWithText("背景").assertIsDisplayed()
        composeRule.onNodeWithText("历史节点").assertIsDisplayed()
        composeRule.onNodeWithText(card.authoredAt).assertIsDisplayed()
        composeRule.onNodeWithText(card.historicalEvent).assertIsDisplayed()
        val expectedSectionOrder = listOf(
            "历史节点",
            "时代背景",
            "篇名",
            "出处",
            "相关故事",
            "图片来源与许可",
            "参考来源（1）",
        )
        val observedSectionOrder = mutableListOf<String>()
        repeat(8) {
            expectedSectionOrder.mapNotNull { title ->
                val bounds = composeRule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
                if (bounds.width > 0f && bounds.height > 0f) title to bounds.top else null
            }.sortedBy { (_, top) -> top }
                .forEach { (title, _) ->
                    if (title !in observedSectionOrder) observedSectionOrder += title
                }
            if (observedSectionOrder == expectedSectionOrder) return@repeat
            composeRule.onNodeWithTag("backgroundContent").performTouchInput { swipeUp() }
        }
        check(observedSectionOrder == expectedSectionOrder) {
            "背景栏目顺序错误：$observedSectionOrder"
        }
        composeRule.onNodeWithContentDescription("关闭背景").assertDoesNotExist()
        val dismissDistancePx = composeRule.onAllNodes(isRoot())
            .fetchSemanticsNodes()
            .maxOf { it.boundsInRoot.height } * 0.65f

        composeRule.onNodeWithText("背景").performTouchInput {
            down(center)
            advanceEventTime(100)
            moveBy(Offset(0f, dismissDistancePx))
            advanceEventTime(400)
            up()
        }
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithText("背景").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("背景").assertDoesNotExist()
        composeRule.onNodeWithText("解读").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回正面").assertDoesNotExist()

        composeRule.onNodeWithText("读背景").performClick()
        composeRule.onNodeWithText("参考来源（1）").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("图片来源与许可").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("在浏览器打开图片来源").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("在浏览器打开图片许可说明").assertIsDisplayed()
    }

    @Test
    fun readerCanPageForwardFromInterpretationBack() {
        val firstCard = testCard(id = "first-card", quote = "第一张名言。")
        val secondCard = testCard(id = "second-card", quote = "第二张名言。")
        var position by mutableIntStateOf(0)

        composeRule.setContent {
            XinghuoTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    ReaderScreen(
                        state = ReaderState(
                            roundId = 1L,
                            cards = listOf(firstCard, secondCard),
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
                    )
                }
            }
        }

        composeRule.onNodeWithText("星火摘读").assertDoesNotExist()
        composeRule.onNodeWithText("本轮 1 / 2").assertDoesNotExist()
        composeRule.onNodeWithText(firstCard.quote).performTouchInput { swipeLeft() }
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithText("启示").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("readerPager").performTouchInput {
            swipe(
                start = Offset(center.x, height * 0.82f),
                end = Offset(center.x, height * 0.18f),
                durationMillis = 400,
            )
        }
        composeRule.waitUntil(timeoutMillis = 3_000) { position == 1 }
        composeRule.onNodeWithText(secondCard.quote).assertIsDisplayed()
    }

    private fun testCard(
        id: String = "8a3e739a-9b89-5840-a47a-6bb2c6435c1c",
        quote: String = "没有调查，没有发言权。",
        longBackground: Boolean = false,
    ) = QuoteCard(
        id = id,
        revision = 2,
        quote = quote,
        series = "毛泽东选集",
        volume = "第一卷",
        workTitle = "反对本本主义",
        authoredAt = "1930-05",
        themes = listOf("调查研究"),
        interpretation = CardInterpretation(
            inspiration = "在工作中，可以先明确问题，再收集直接证据，并根据新事实修正原有判断。",
            explanation = "对问题的判断必须建立在实际调查上。这句话批评的是脱离实际的发言和决策。",
        ),
        historicalEvent = "1930年5月，毛泽东针对脱离实际的倾向写下这篇文章。",
        background = if (longBackground) {
            "这篇文章围绕调查研究与实际工作的关系展开。".repeat(16)
        } else {
            "这篇文章围绕调查研究与实际工作的关系展开。"
        },
        story = "文章强调判断应当来自实际情况。",
        imagePath = "",
        sources = listOf(
            CardSource(
                name = "《反对本本主义》原文",
                url = "https://example.com/source",
                accessedAt = "2026-07-29",
                evidenceType = "primary_text",
            ),
        ),
        isWithdrawn = false,
        isLiked = false,
        isFavorited = false,
        likedAt = null,
        favoritedAt = null,
        imageAttribution = ImageAttribution(
            creator = "档案馆",
            sourceUrl = "https://example.com/image",
            licenseName = "Public domain",
            licenseEvidence = "https://example.com/image#Licensing",
        ),
    )
}
