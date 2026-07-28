package com.xuhuangbin.xinghuozhaidu.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xuhuangbin.xinghuozhaidu.ui.UpdatePhase
import com.xuhuangbin.xinghuozhaidu.ui.UpdateUiState

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state.phase == UpdatePhase.Idle) return
    when (state.phase) {
        UpdatePhase.Checking -> StatusDialog(
            title = "正在检查更新",
            dismissible = false,
            onDismiss = onDismiss,
        ) { CircularProgressIndicator() }
        UpdatePhase.Available -> {
            val manifest = state.manifest ?: return
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("发现内容更新 ${manifest.contentVersion}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("发布日期：${manifest.publishedAt.take(10)}")
                        Text("大小：${formatBytes(manifest.packageBytes)}")
                        Text(
                            "新增 ${manifest.changes.added} · 修改 ${manifest.changes.updated} · 下架 ${manifest.changes.withdrawn}",
                        )
                        Text(manifest.releaseNotes)
                    }
                },
                confirmButton = { TextButton(onClick = onConfirm) { Text("立即更新") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } },
            )
        }
        UpdatePhase.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载并校验") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${(state.progress * 100).toInt()}%")
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消下载") } },
        )
        UpdatePhase.Success -> StatusDialog("更新完成", true, onDismiss) {
            Text(state.message.orEmpty())
        }
        UpdatePhase.UpToDate -> StatusDialog("无需更新", true, onDismiss) {
            Text(state.message.orEmpty())
        }
        UpdatePhase.Error -> StatusDialog("更新失败", true, onDismiss) {
            Text(state.message.orEmpty())
        }
        UpdatePhase.Idle -> Unit
    }
}

@Composable
private fun StatusDialog(
    title: String,
    dismissible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        title = { Text(title) },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) { content() }
        },
        confirmButton = {
            if (dismissible) TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun formatBytes(value: Long): String = when {
    value >= 1024L * 1024L -> "%.1f MB".format(value / (1024f * 1024f))
    value >= 1024L -> "%.1f KB".format(value / 1024f)
    else -> "$value B"
}
