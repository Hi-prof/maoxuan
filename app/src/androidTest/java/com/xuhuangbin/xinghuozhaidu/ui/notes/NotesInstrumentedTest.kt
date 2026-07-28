package com.xuhuangbin.xinghuozhaidu.ui.notes

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import com.xuhuangbin.xinghuozhaidu.domain.model.CardInterpretation
import com.xuhuangbin.xinghuozhaidu.domain.model.PersonalNote
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.saved.SavedScreen
import com.xuhuangbin.xinghuozhaidu.ui.theme.XinghuoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NotesInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun savedScreenSwitchesBetweenIndependentFavoriteAndLikedLists() {
        val favorite = testCard(id = "favorite", quote = "收藏内容", favorited = true)
        val liked = testCard(id = "liked", quote = "点赞内容", liked = true)
        composeRule.setContent {
            XinghuoTheme {
                SavedScreen(
                    favorites = listOf(favorite),
                    liked = listOf(liked),
                    onCardClick = {},
                )
            }
        }

        composeRule.onNodeWithText("收藏内容").assertIsDisplayed()
        composeRule.onNodeWithText("点赞").performClick()
        composeRule.onNodeWithText("点赞内容").assertIsDisplayed()
    }

    @Test
    fun notesScreenShowsLinkedCardAndStandaloneAddAction() {
        val card = testCard(id = "card-1", quote = "关联卡片名言")
        val note = PersonalNote(
            id = 1,
            cardId = card.id,
            title = "阅读记录",
            body = "这是对当前卡片的个人看法。",
            createdAt = 100,
            updatedAt = 200,
        )
        composeRule.setContent {
            XinghuoTheme {
                NotesScreen(
                    notes = listOf(note),
                    cards = listOf(card),
                    onAddNote = {},
                    onNoteClick = {},
                    onCardClick = {},
                )
            }
        }

        composeRule.onNodeWithText("阅读记录").assertIsDisplayed()
        composeRule.onNodeWithText("关联卡片名言").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("新建独立笔记").assertIsDisplayed()
    }

    @Test
    fun editorSavesBodyAndWarnsBeforeDiscardingLaterChanges() {
        var savedTitle = ""
        var savedBody = ""
        composeRule.setContent {
            XinghuoTheme {
                NoteEditorScreen(
                    note = null,
                    linkedCard = null,
                    linkedCardId = null,
                    operationInProgress = false,
                    operationError = null,
                    onBack = {},
                    onClearError = {},
                    onSave = { title, body ->
                        savedTitle = title
                        savedBody = body
                    },
                    onDelete = null,
                )
            }
        }

        composeRule.onNodeWithTag("noteTitle").performTextInput("标题")
        composeRule.onNodeWithTag("noteBody").performTextInput("正文内容")
        composeRule.onNodeWithContentDescription("保存笔记").performClick()
        composeRule.runOnIdle {
            assertEquals("标题", savedTitle)
            assertEquals("正文内容", savedBody)
        }

        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
    }

    @Test
    fun editorBlocksBackWhileSaveIsInProgress() {
        val operationInProgress = mutableStateOf(false)
        var wentBack = false
        composeRule.setContent {
            XinghuoTheme {
                NoteEditorScreen(
                    note = null,
                    linkedCard = null,
                    linkedCardId = null,
                    operationInProgress = operationInProgress.value,
                    operationError = null,
                    onBack = { wentBack = true },
                    onClearError = {},
                    onSave = { _, _ -> },
                    onDelete = null,
                )
            }
        }

        composeRule.onNodeWithTag("noteBody").performTextInput("正在保存的正文")
        composeRule.runOnIdle { operationInProgress.value = true }
        composeRule.onNodeWithContentDescription("返回").assertIsNotEnabled()
        pressBack()

        assertTrue(
            composeRule.onAllNodesWithText("放弃未保存的修改？")
                .fetchSemanticsNodes().isEmpty(),
        )
        composeRule.runOnIdle { assertFalse(wentBack) }
    }

    private fun testCard(
        id: String,
        quote: String,
        liked: Boolean = false,
        favorited: Boolean = false,
    ) = QuoteCard(
        id = id,
        revision = 1,
        quote = quote,
        series = "毛泽东选集",
        volume = "第一卷",
        workTitle = "实践论",
        authoredAt = "1937-07",
        themes = listOf("实践"),
        interpretation = CardInterpretation("核心意思", "理解重点", "现实启示"),
        contextExcerpt = null,
        background = null,
        story = null,
        imagePath = "",
        sources = emptyList(),
        isWithdrawn = false,
        isLiked = liked,
        isFavorited = favorited,
        likedAt = 100.takeIf { liked }?.toLong(),
        favoritedAt = 100.takeIf { favorited }?.toLong(),
    )
}
