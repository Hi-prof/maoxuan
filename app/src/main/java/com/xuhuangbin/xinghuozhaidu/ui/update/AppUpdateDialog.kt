package com.xuhuangbin.xinghuozhaidu.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xuhuangbin.xinghuozhaidu.ui.AppUpdatePhase
import com.xuhuangbin.xinghuozhaidu.ui.AppUpdateUiState

@Composable
fun AppUpdateDialog(
    state: AppUpdateUiState,
    onConfirmDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state.phase == AppUpdatePhase.Idle) return
    when (state.phase) {
        AppUpdatePhase.Checking -> StatusDialog(
            title = "正在检查应用更新",
            dismissible = false,
            onDismiss = onDismiss,
        ) { CircularProgressIndicator() }
        AppUpdatePhase.Available -> {
            val release = state.release ?: return
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("发现应用更新 ${release.versionName}") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("发布日期：${release.publishedAt.take(10)}")
                        Text("大小：${formatUpdateBytes(release.apkBytes)}")
                        Text(release.releaseNotes)
                    }
                },
                confirmButton = {
                    TextButton(onClick = onConfirmDownload) { Text("下载并安装") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } },
            )
        }
        AppUpdatePhase.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载并校验应用") },
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
        AppUpdatePhase.PermissionRequired -> StatusDialog(
            title = "允许安装应用",
            dismissible = true,
            onDismiss = onDismiss,
            confirmText = "打开设置",
            onConfirm = onInstall,
            dismissText = "稍后",
        ) { Text(state.message.orEmpty()) }
        AppUpdatePhase.ReadyToInstall -> StatusDialog(
            title = "安装应用更新",
            dismissible = true,
            onDismiss = onDismiss,
            confirmText = "安装更新",
            onConfirm = onInstall,
            dismissText = "关闭",
        ) { Text(state.message.orEmpty()) }
        AppUpdatePhase.UpToDate -> StatusDialog("无需更新", true, onDismiss) {
            Text(state.message.orEmpty())
        }
        AppUpdatePhase.Error -> StatusDialog("应用更新失败", true, onDismiss) {
            Text(state.message.orEmpty())
        }
        AppUpdatePhase.Idle -> Unit
    }
}
