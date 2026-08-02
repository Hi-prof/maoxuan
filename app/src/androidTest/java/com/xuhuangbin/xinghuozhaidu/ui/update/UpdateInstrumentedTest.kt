package com.xuhuangbin.xinghuozhaidu.ui.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.xuhuangbin.xinghuozhaidu.domain.model.InstalledContentState
import com.xuhuangbin.xinghuozhaidu.ui.UpdatePhase
import com.xuhuangbin.xinghuozhaidu.ui.UpdateUiState
import com.xuhuangbin.xinghuozhaidu.ui.saved.MineScreen
import com.xuhuangbin.xinghuozhaidu.ui.theme.XinghuoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UpdateInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mineScreenKeepsAppAndContentUpdateActionsVisibleAtMinimumViewport() {
        composeRule.setContent {
            XinghuoTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    MineScreen(
                        appVersion = "1.7.0",
                        contentState = InstalledContentState(
                            contentVersion = "1.5.0",
                            publishedAt = "2026-08-02T00:00:00Z",
                            lastCheckedAt = 1_775_000_000_000,
                            lastUpdatedAt = 1_775_000_000_000,
                        ),
                        onCheckAppUpdate = {},
                        onCheckContentUpdate = {},
                        appUpdateEnabled = true,
                        contentUpdateEnabled = true,
                    )
                }
            }
        }

        composeRule.onNodeWithText("应用版本 1.7.0").assertIsDisplayed()
        composeRule.onNodeWithText("检查应用更新").assertIsDisplayed()
        composeRule.onNodeWithText("内容版本 1.5.0").assertIsDisplayed()
        composeRule.onNodeWithText("检查内容更新").assertIsDisplayed()
    }

    @Test
    fun incompatibleContentOffersAppUpdateAction() {
        var appUpdateRequests = 0
        composeRule.setContent {
            XinghuoTheme {
                UpdateDialog(
                    state = UpdateUiState(
                        phase = UpdatePhase.Error,
                        message = "此内容版本需要更新应用后才能安装",
                        requiresAppUpdate = true,
                    ),
                    onConfirm = {},
                    onDismiss = {},
                    onAppUpdate = { appUpdateRequests += 1 },
                )
            }
        }

        composeRule.onNodeWithText("需要更新应用").assertIsDisplayed()
        composeRule.onNodeWithText("更新应用").performClick()
        composeRule.runOnIdle { assertEquals(1, appUpdateRequests) }
    }
}
