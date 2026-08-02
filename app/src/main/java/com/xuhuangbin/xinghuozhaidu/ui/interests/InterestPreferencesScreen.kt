package com.xuhuangbin.xinghuozhaidu.ui.interests

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.xuhuangbin.xinghuozhaidu.domain.recommendation.InterestCategory

@Composable
fun InterestPreferencesScreen(
    initialSelected: Set<InterestCategory>,
    reducedCount: Int,
    isSaving: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSave: (Set<InterestCategory>) -> Unit,
    onClearReduced: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    InterestPickerLayout(
        title = "兴趣偏好",
        initialSelected = initialSelected,
        isSaving = isSaving,
        errorMessage = errorMessage,
        primaryLabel = "保存",
        onPrimary = onSave,
        onBack = onBack,
        clearFeedbackLabel = if (reducedCount > 0) "清除“减少此类”记录（$reducedCount）" else null,
        onClearFeedback = { confirmClear = true },
        modifier = modifier,
    )
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除减少记录？") },
            text = { Text("相关内容将恢复正常推荐权重。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearReduced()
                    },
                ) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}
