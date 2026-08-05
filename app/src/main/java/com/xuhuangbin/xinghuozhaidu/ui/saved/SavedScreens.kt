package com.xuhuangbin.xinghuozhaidu.ui.saved

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuhuangbin.xinghuozhaidu.domain.model.InstalledContentState
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.components.CardSummaryList
import com.xuhuangbin.xinghuozhaidu.ui.theme.ArchiveGreen
import com.xuhuangbin.xinghuozhaidu.ui.theme.Ink
import com.xuhuangbin.xinghuozhaidu.ui.theme.MutedInk
import com.xuhuangbin.xinghuozhaidu.ui.theme.Paper
import com.xuhuangbin.xinghuozhaidu.ui.theme.SoftRed
import com.xuhuangbin.xinghuozhaidu.ui.theme.SpiritRed
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SavedScreen(
    favorites: List<QuoteCard>,
    liked: List<QuoteCard>,
    onCardClick: (QuoteCard) -> Unit,
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val favoriteListState = rememberLazyListState()
    val likedListState = rememberLazyListState()
    val options = listOf(
        "收藏" to Icons.Outlined.BookmarkBorder,
        "点赞" to Icons.Outlined.FavoriteBorder,
    )
    val currentCards = if (selectedIndex == 0) favorites else liked
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("收藏与点赞")
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .widthIn(max = 286.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            ) {
                options.forEachIndexed { index, (label, imageVector) ->
                    SegmentedButton(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = SoftRed,
                            activeContentColor = SpiritRed,
                            inactiveContainerColor = Paper,
                            inactiveContentColor = Ink,
                        ),
                        icon = {
                            Icon(
                                imageVector = imageVector,
                                contentDescription = "$label 分段",
                                modifier = Modifier.width(18.dp),
                            )
                        },
                        label = { Text(label, letterSpacing = 0.sp) },
                    )
                }
            }
        }
        Text(
            text = "${options[selectedIndex].first} ${currentCards.size} 条",
            color = ArchiveGreen,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 2.dp),
        )
        CardSummaryList(
            cards = currentCards,
            emptyText = if (selectedIndex == 0) "还没有收藏内容" else "还没有点赞内容",
            onCardClick = onCardClick,
            state = if (selectedIndex == 0) favoriteListState else likedListState,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun MineScreen(
    appVersion: String,
    contentState: InstalledContentState?,
    selectedInterestCount: Int = 0,
    selectedSeriesCount: Int = 0,
    onInterestPreferences: () -> Unit = {},
    onCheckAppUpdate: () -> Unit,
    onCheckContentUpdate: () -> Unit,
    appUpdateEnabled: Boolean,
    contentUpdateEnabled: Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("我的")
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            color = Paper,
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "阅读偏好",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Text(
                    buildString {
                        append(
                            if (selectedInterestCount == 0) {
                                "尚未选择兴趣标签"
                            } else {
                                "已选 $selectedInterestCount 个兴趣标签"
                            },
                        )
                        append(" · ")
                        append(
                            if (selectedSeriesCount == 0) {
                                "全部内容"
                            } else {
                                "已选 $selectedSeriesCount 个内容系列"
                            },
                        )
                    },
                    color = MutedInk,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                OutlinedButton(onClick = onInterestPreferences) {
                    Icon(Icons.Outlined.Tune, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("调整阅读偏好")
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text(
                    "应用版本 $appVersion",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Text(
                    "Android 正式版",
                    color = MutedInk,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                OutlinedButton(
                    onClick = onCheckAppUpdate,
                    enabled = appUpdateEnabled,
                ) {
                    Icon(Icons.Outlined.SystemUpdateAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("检查应用更新")
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text(
                    "内容版本 ${contentState?.contentVersion ?: "初始化中"}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Text(
                    contentState?.publishedAt?.take(10)?.let { "内容发布于 $it" }
                        ?: if (contentUpdateEnabled) "正在读取内容信息" else "尚未配置内容源",
                    color = MutedInk,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                contentState?.let { state ->
                    Text(
                        buildString {
                            append("本地更新 ")
                            append(formatTimestamp(state.lastUpdatedAt))
                            state.lastCheckedAt?.let { checkedAt ->
                                append(" · 上次检查 ")
                                append(formatTimestamp(checkedAt))
                            }
                        },
                        color = MutedInk,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
                    )
                }
                OutlinedButton(
                    onClick = onCheckContentUpdate,
                    enabled = contentUpdateEnabled,
                ) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("检查内容更新")
                }
            }
        }
    }
}

private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatTimestamp(value: Long): String = Instant.ofEpochMilli(value)
    .atZone(ZoneId.systemDefault())
    .format(timestampFormatter)

@Composable
private fun ScreenTitle(title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold)
    }
}
