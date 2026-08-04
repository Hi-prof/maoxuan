package com.xuhuangbin.xinghuozhaidu.ui.interests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.xuhuangbin.xinghuozhaidu.domain.recommendation.InterestCategory
import com.xuhuangbin.xinghuozhaidu.ui.theme.XinghuoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InterestSelectionInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysAllInterestsAndLimitsSelectionToFive() {
        var saved: Set<InterestCategory>? = null
        composeRule.setContent {
            XinghuoTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    InterestSelectionScreen(
                        initialSelected = emptySet(),
                        isSaving = false,
                        errorMessage = null,
                        onContinue = { saved = it },
                        onSkip = {},
                    )
                }
            }
        }

        InterestCategory.entries.forEach { category ->
            composeRule.onNodeWithText(category.label).performScrollTo().assertIsDisplayed()
        }
        InterestCategory.entries.take(5).forEach { category ->
            composeRule.onNodeWithText(category.label).performScrollTo().performClick()
        }
        composeRule.onNodeWithText(InterestCategory.entries[5].label)
            .performScrollTo()
            .assertIsNotEnabled()

        composeRule.onNodeWithText(InterestCategory.entries.first().label).performScrollTo().performClick()
        composeRule.onNodeWithText(InterestCategory.entries[5].label).performScrollTo().performClick()
        composeRule.onNodeWithText("开始阅读").performClick()

        composeRule.runOnIdle {
            assertEquals(5, saved?.size)
            assertTrue(InterestCategory.entries[5] in checkNotNull(saved))
        }
    }

    @Test
    fun skipDoesNotRequireASelection() {
        var skipped = false
        composeRule.setContent {
            XinghuoTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    InterestSelectionScreen(
                        initialSelected = emptySet(),
                        isSaving = false,
                        errorMessage = null,
                        onContinue = {},
                        onSkip = { skipped = true },
                    )
                }
            }
        }

        composeRule.onNodeWithText("暂时跳过").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(skipped) }
    }

    @Test
    fun preferenceScreenSupportsAllContentAndMultipleSeries() {
        var savedSeries: Set<String>? = null
        composeRule.setContent {
            XinghuoTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    InterestPreferencesScreen(
                        initialSelected = emptySet(),
                        availableSeries = listOf("毛泽东选集", "毛泽东诗词", "名人名言", "马原思考"),
                        initialSelectedSeries = emptySet(),
                        reducedCount = 0,
                        isSaving = false,
                        errorMessage = null,
                        onBack = {},
                        onSave = { _, series -> savedSeries = series },
                        onClearReduced = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("全部内容").assertIsSelected()
        composeRule.onNodeWithText("毛泽东选集").performScrollTo().performClick()
        composeRule.onNodeWithText("毛泽东诗词").performScrollTo().performClick()
        composeRule.onNodeWithText("毛泽东选集").performScrollTo().performClick()
        composeRule.onNodeWithText("毛泽东诗词").performScrollTo().performClick()
        composeRule.onNodeWithText("保存").performClick()
        composeRule.runOnIdle { assertEquals(setOf("毛泽东诗词"), savedSeries) }

        composeRule.onNodeWithText("全部内容").performScrollTo().performClick()
        composeRule.onNodeWithText("保存").performClick()
        composeRule.runOnIdle { assertTrue(checkNotNull(savedSeries).isEmpty()) }
    }
}
