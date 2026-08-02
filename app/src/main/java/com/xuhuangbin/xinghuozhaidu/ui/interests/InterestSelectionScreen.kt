package com.xuhuangbin.xinghuozhaidu.ui.interests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuhuangbin.xinghuozhaidu.domain.recommendation.InterestCategory
import com.xuhuangbin.xinghuozhaidu.ui.theme.Canvas
import com.xuhuangbin.xinghuozhaidu.ui.theme.Ink
import com.xuhuangbin.xinghuozhaidu.ui.theme.MutedInk

@Composable
fun InterestSelectionScreen(
    initialSelected: Set<InterestCategory>,
    isSaving: Boolean,
    errorMessage: String?,
    onContinue: (Set<InterestCategory>) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InterestPickerLayout(
        title = "选择你感兴趣的内容",
        initialSelected = initialSelected,
        isSaving = isSaving,
        errorMessage = errorMessage,
        primaryLabel = "开始阅读",
        onPrimary = onContinue,
        secondaryLabel = "暂时跳过",
        onSecondary = onSkip,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InterestPickerLayout(
    title: String,
    initialSelected: Set<InterestCategory>,
    isSaving: Boolean,
    errorMessage: String?,
    primaryLabel: String,
    onPrimary: (Set<InterestCategory>) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    clearFeedbackLabel: String? = null,
    onClearFeedback: (() -> Unit)? = null,
) {
    var selectedIds by rememberSaveable(initialSelected) {
        mutableStateOf(initialSelected.map(InterestCategory::id))
    }
    val selected = selectedIds.mapNotNullTo(linkedSetOf(), InterestCategory::fromId)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            }
            Text(
                text = title,
                color = Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = 0.sp,
            )
        }
        Text(
            text = "已选 ${selected.size}/$MAX_SELECTED_INTERESTS",
            color = MutedInk,
            fontSize = 13.sp,
            letterSpacing = 0.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                InterestCategory.entries.forEach { category ->
                    val isSelected = category in selected
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedIds = if (isSelected) {
                                selectedIds - category.id
                            } else {
                                selectedIds + category.id
                            }
                        },
                        enabled = !isSaving && (isSelected || selected.size < MAX_SELECTED_INTERESTS),
                        label = { Text(category.label, letterSpacing = 0.sp) },
                    )
                }
            }
            if (clearFeedbackLabel != null && onClearFeedback != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onClearFeedback,
                    enabled = !isSaving,
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                    Text(clearFeedbackLabel, letterSpacing = 0.sp)
                }
            }
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        Button(
            onClick = { onPrimary(selected) },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(primaryLabel, letterSpacing = 0.sp)
        }
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(
                onClick = onSecondary,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(secondaryLabel, letterSpacing = 0.sp)
            }
        }
    }
}

private const val MAX_SELECTED_INTERESTS = 5
