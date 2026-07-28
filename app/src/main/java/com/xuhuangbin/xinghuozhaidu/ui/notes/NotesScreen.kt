package com.xuhuangbin.xinghuozhaidu.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuhuangbin.xinghuozhaidu.domain.model.PersonalNote
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.theme.Divider
import com.xuhuangbin.xinghuozhaidu.ui.theme.Ink
import com.xuhuangbin.xinghuozhaidu.ui.theme.MutedInk
import com.xuhuangbin.xinghuozhaidu.ui.theme.Paper
import com.xuhuangbin.xinghuozhaidu.ui.theme.SoftRed
import com.xuhuangbin.xinghuozhaidu.ui.theme.SpiritRed
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NotesScreen(
    notes: List<PersonalNote>,
    cards: List<QuoteCard>,
    onAddNote: () -> Unit,
    onNoteClick: (PersonalNote) -> Unit,
    onCardClick: (QuoteCard) -> Unit,
) {
    val cardsById = cards.associateBy(QuoteCard::id)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = "笔记",
                color = Ink,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
            if (notes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MutedInk,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = "还没有笔记",
                        color = MutedInk,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(notes, key = PersonalNote::id) { note ->
                        NoteSummary(
                            note = note,
                            linkedCard = note.cardId?.let(cardsById::get),
                            onNoteClick = { onNoteClick(note) },
                            onCardClick = onCardClick,
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAddNote,
            containerColor = SpiritRed,
            contentColor = Paper,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "新建独立笔记")
        }
    }
}

@Composable
private fun NoteSummary(
    note: PersonalNote,
    linkedCard: QuoteCard?,
    onNoteClick: () -> Unit,
    onCardClick: (QuoteCard) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNoteClick),
        color = Paper,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, Divider),
    ) {
        Column {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = note.title ?: "无标题笔记",
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatNoteTime(note.updatedAt),
                        color = MutedInk,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                Text(
                    text = note.body,
                    color = MutedInk,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (note.cardId != null) {
                HorizontalDivider(color = Divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = linkedCard != null) {
                            linkedCard?.let(onCardClick)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        tint = SpiritRed,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = linkedCard?.quote ?: "关联卡片暂不可用",
                            color = Ink,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        linkedCard?.let { card ->
                            Text(
                                text = "《${card.workTitle}》",
                                color = SpiritRed,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (linkedCard?.isWithdrawn == true) {
                        Surface(color = SoftRed, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "已下架",
                                color = SpiritRed,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val noteTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

private fun formatNoteTime(value: Long): String = Instant.ofEpochMilli(value)
    .atZone(ZoneId.systemDefault())
    .format(noteTimeFormatter)
