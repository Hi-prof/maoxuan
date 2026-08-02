package com.xuhuangbin.xinghuozhaidu.ui.interests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
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
}
