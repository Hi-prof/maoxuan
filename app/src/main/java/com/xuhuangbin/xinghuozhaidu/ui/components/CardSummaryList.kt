package com.xuhuangbin.xinghuozhaidu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.theme.Divider
import com.xuhuangbin.xinghuozhaidu.ui.theme.Ink
import com.xuhuangbin.xinghuozhaidu.ui.theme.MutedInk
import com.xuhuangbin.xinghuozhaidu.ui.theme.Paper
import com.xuhuangbin.xinghuozhaidu.ui.theme.SpiritRed
import java.io.File

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
            Text(emptyText, color = MutedInk, fontSize = 15.sp)
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(cards, key = QuoteCard::id) { card ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .clickable { onCardClick(card) },
                color = Paper,
                shape = RoundedCornerShape(7.dp),
                border = BorderStroke(1.dp, Divider),
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(
                        modifier = Modifier.size(width = 76.dp, height = 102.dp),
                        shape = RoundedCornerShape(5.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        AsyncImage(
                            model = File(card.imagePath),
                            contentDescription = null,
                            modifier = Modifier.alpha(0.48f),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            card.quote,
                            color = Ink,
                            fontSize = 16.sp,
                            lineHeight = 23.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "《${card.workTitle}》",
                                color = SpiritRed,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (card.isWithdrawn) {
                                Text("已下架", color = SpiritRed, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
