package com.xuhuangbin.xinghuozhaidu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.theme.ArchiveGreen
import com.xuhuangbin.xinghuozhaidu.ui.theme.Divider
import com.xuhuangbin.xinghuozhaidu.ui.theme.Ink
import com.xuhuangbin.xinghuozhaidu.ui.theme.MutedInk
import com.xuhuangbin.xinghuozhaidu.ui.theme.SpiritRed

@Composable
fun CardSummaryList(
    cards: List<QuoteCard>,
    emptyText: String,
    onCardClick: (QuoteCard) -> Unit,
    state: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = MutedInk, fontSize = 15.sp, letterSpacing = 0.sp)
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(cards, key = QuoteCard::id) { card ->
            CardSummaryItem(card = card, onClick = { onCardClick(card) })
        }
    }
}

@Composable
private fun CardSummaryItem(
    card: QuoteCard,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .heightIn(min = 112.dp)
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(46.dp)
                    .padding(top = 2.dp, end = 8.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = summaryIndexLabel(card),
                    color = ArchiveGreen,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                )
                Text(
                    text = summaryDateLabel(card.authoredAt),
                    color = MutedInk,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(SpiritRed),
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 2.dp),
            ) {
                Text(
                    text = card.quote,
                    color = Ink,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "《${card.workTitle}》",
                        color = SpiritRed,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (card.isWithdrawn) {
                        Text(
                            text = "已下架",
                            color = SpiritRed,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            letterSpacing = 0.sp,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    text = "${summaryDateLabel(card.authoredAt)} · ${card.volume}",
                    color = ArchiveGreen,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 62.dp), color = Divider)
    }
}

internal fun summaryIndexLabel(card: QuoteCard): String = when (card.series) {
    "毛泽东选集" -> when (card.volume) {
        "第一卷" -> "卷一"
        "第二卷" -> "卷二"
        "第三卷" -> "卷三"
        "第四卷" -> "卷四"
        else -> "毛选"
    }
    "毛泽东诗词" -> "诗词"
    "马原思考" -> "马原"
    "名人名言" -> "名言"
    else -> card.series.trim().take(2).ifEmpty { "文摘" }
}

internal fun summaryDateLabel(authoredAt: String): String =
    MODERN_DATE.matchEntire(authoredAt.trim())?.groupValues?.get(1) ?: authoredAt.trim()

private val MODERN_DATE = Regex("^(\\d{4})(?:-\\d{2}(?:-\\d{2})?)?$")
