package com.xuhuangbin.xinghuozhaidu.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.theme.Divider
import com.xuhuangbin.xinghuozhaidu.ui.theme.Ink
import com.xuhuangbin.xinghuozhaidu.ui.theme.MutedInk
import com.xuhuangbin.xinghuozhaidu.ui.theme.Paper
import com.xuhuangbin.xinghuozhaidu.ui.theme.SpiritRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundSheet(
    card: QuoteCard,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    var sourcesExpanded by remember(card.id) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        containerColor = Paper,
        contentColor = Ink,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MutedInk) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "背景",
                    color = Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                )
            }
            HorizontalDivider(color = Divider)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .testTag("backgroundContent")
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                BackgroundSection("历史节点") {
                    Text(
                        card.authoredAt,
                        color = MutedInk,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        letterSpacing = 0.sp,
                    )
                    BackgroundBody(card.historicalEvent)
                }
                HorizontalDivider(color = Divider)
                BackgroundSection("时代背景") { BackgroundBody(card.background) }
                HorizontalDivider(color = Divider)
                BackgroundSection("篇名") {
                    BackgroundBody("《${card.workTitle}》")
                }
                HorizontalDivider(color = Divider)
                BackgroundSection("出处") {
                    BackgroundBody("${card.series} · ${card.volume}")
                }
                HorizontalDivider(color = Divider)
                BackgroundSection("相关故事") { BackgroundBody(card.story) }
                card.imageAttribution?.let { attribution ->
                    HorizontalDivider(color = Divider)
                    BackgroundSection("图片来源与许可") {
                        Text(
                            text = attribution.creator,
                            color = MutedInk,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            letterSpacing = 0.sp,
                        )
                        AttributionLink(
                            label = "查看图片来源",
                            contentDescription = "在浏览器打开图片来源",
                            url = attribution.sourceUrl,
                        )
                        AttributionLink(
                            label = "查看许可说明：${attribution.licenseName}",
                            contentDescription = "在浏览器打开图片许可说明",
                            url = attribution.licenseEvidence,
                        )
                    }
                }
                HorizontalDivider(color = Divider)
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { sourcesExpanded = !sourcesExpanded }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "参考来源（${card.sources.size}）",
                            color = Ink,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp,
                        )
                        Icon(
                            imageVector = if (sourcesExpanded) {
                                Icons.Outlined.ExpandLess
                            } else {
                                Icons.Outlined.ExpandMore
                            },
                            contentDescription = if (sourcesExpanded) {
                                "收起参考来源"
                            } else {
                                "展开参考来源"
                            },
                            tint = MutedInk,
                        )
                    }
                    if (sourcesExpanded) {
                        card.sources.forEach { source ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(source.url)),
                                        )
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        source.name,
                                        color = Ink,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        letterSpacing = 0.sp,
                                    )
                                    Text(
                                        "访问于 ${source.accessedAt}",
                                        color = MutedInk,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.sp,
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = "在浏览器打开",
                                    modifier = Modifier.size(18.dp),
                                    tint = SpiritRed,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttributionLink(
    label: String,
    contentDescription: String,
    url: String,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            .semantics { this.contentDescription = contentDescription }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = SpiritRed,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = SpiritRed,
        )
    }
}

@Composable
private fun BackgroundSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = SpiritRed,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        content()
    }
}

@Composable
private fun BackgroundBody(text: String) {
    Text(
        text = text,
        color = Ink,
        fontSize = 15.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp,
    )
}
