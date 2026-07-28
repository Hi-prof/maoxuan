package com.xuhuangbin.xinghuozhaidu.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuhuangbin.xinghuozhaidu.domain.model.PersonalNote
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.theme.Ink
import com.xuhuangbin.xinghuozhaidu.ui.theme.MutedInk
import com.xuhuangbin.xinghuozhaidu.ui.theme.SpiritRed

@Composable
fun NoteEditorScreen(
    note: PersonalNote?,
    linkedCard: QuoteCard?,
    linkedCardId: String?,
    operationInProgress: Boolean,
    operationError: String?,
    onBack: () -> Unit,
    onClearError: () -> Unit,
    onSave: (title: String, body: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val editorKey = note?.id?.toString() ?: linkedCardId.orEmpty()
    val initialTitle = note?.title.orEmpty()
    val initialBody = note?.body.orEmpty()
    var title by rememberSaveable(editorKey) { mutableStateOf(initialTitle) }
    var body by rememberSaveable(editorKey) { mutableStateOf(initialBody) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isDirty = title != initialTitle || body != initialBody

    fun requestBack() {
        if (operationInProgress) return
        if (isDirty) showDiscardDialog = true else onBack()
    }

    BackHandler(enabled = isDirty || operationInProgress) {
        if (!operationInProgress) {
            if (isDirty) showDiscardDialog = true else onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = ::requestBack, enabled = !operationInProgress) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = if (note == null) "新建笔记" else "编辑笔记",
                color = Ink,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (onDelete != null) {
                IconButton(
                    onClick = { showDeleteDialog = true },
                    enabled = !operationInProgress,
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除笔记")
                }
            }
            IconButton(
                onClick = { onSave(title, body) },
                enabled = body.isNotBlank() && isDirty && !operationInProgress,
            ) {
                Icon(Icons.Outlined.Save, contentDescription = "保存笔记")
            }
        }
        linkedCardId?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = SpiritRed,
                    modifier = Modifier.size(20.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = linkedCard?.quote ?: "关联卡片暂不可用",
                        color = Ink,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    linkedCard?.let { card ->
                        Text(
                            text = "《${card.workTitle}》${if (card.isWithdrawn) " · 已下架" else ""}",
                            color = SpiritRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it
                onClearError()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("noteTitle")
                .padding(horizontal = 16.dp, vertical = 6.dp),
            enabled = !operationInProgress,
            singleLine = true,
            label = { Text("标题（可选）") },
        )
        OutlinedTextField(
            value = body,
            onValueChange = {
                body = it
                onClearError()
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("noteBody")
                .padding(horizontal = 16.dp, vertical = 6.dp),
            enabled = !operationInProgress,
            label = { Text("正文") },
            placeholder = { Text("写下此刻的想法") },
        )
        operationError?.let { error ->
            Text(
                text = error,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }
        Text(
            text = if (linkedCardId == null) "独立笔记" else "关联卡片笔记",
            color = MutedInk,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("当前修改尚未保存，返回后将无法恢复。") },
            confirmButton = {
                TextButton(onClick = onBack) { Text("放弃修改", color = SpiritRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("继续编辑") }
            },
        )
    }

    if (showDeleteDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除这篇笔记？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = onDelete, enabled = !operationInProgress) {
                    Text("删除", color = SpiritRed)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !operationInProgress,
                ) { Text("取消") }
            },
        )
    }
}
