package com.xuhuangbin.xinghuozhaidu.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.xuhuangbin.xinghuozhaidu.ui.theme.XinghuoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SearchScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onlyImeSubmissionSavesAndHistoryActionsStayIndependent() {
        var query by mutableStateOf("")
        var submitCount = 0
        var deletedKeyword: String? = null
        var clearCount = 0

        composeRule.setContent {
            XinghuoTheme {
                SearchScreen(
                    query = query,
                    results = emptyList(),
                    history = listOf("实践论"),
                    onQueryChange = { query = it },
                    onSearchSubmit = { submitCount += 1 },
                    onHistoryDelete = { deletedKeyword = it },
                    onHistoryClear = { clearCount += 1 },
                    onBack = {},
                    onCardClick = {},
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("实践")
        composeRule.runOnIdle {
            assertEquals("实践", query)
            assertEquals(0, submitCount)
        }

        composeRule.onNode(hasSetTextAction()).performImeAction()
        composeRule.runOnIdle { assertEquals(1, submitCount) }

        composeRule.onNodeWithContentDescription("清空搜索关键词").performClick()
        composeRule.onNodeWithText("实践论").performClick()
        composeRule.runOnIdle {
            assertEquals("实践论", query)
            assertEquals(1, submitCount)
            query = ""
        }

        composeRule.onNodeWithContentDescription("删除搜索记录：实践论").performClick()
        composeRule.runOnIdle {
            assertEquals("实践论", deletedKeyword)
            assertEquals("", query)
            assertEquals(1, submitCount)
        }

        composeRule.onNodeWithContentDescription("清空全部搜索记录").performClick()
        composeRule.runOnIdle {
            assertEquals(1, clearCount)
            assertNull(query.takeIf(String::isNotEmpty))
        }
    }
}
