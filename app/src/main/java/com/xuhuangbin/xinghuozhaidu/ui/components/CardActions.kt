package com.xuhuangbin.xinghuozhaidu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.theme.MutedInk
import com.xuhuangbin.xinghuozhaidu.ui.theme.SpiritRed

private val MinimumSingleRowContentWidth = (48.dp * 4) + 96.dp

@Composable
fun CardActions(
    card: QuoteCard,
    onBackground: () -> Unit,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
    onNote: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxWidth < MinimumSingleRowContentWidth
        val actions: @Composable () -> Unit = {
            Row {
                IconButton(onClick = onLike, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (card.isLiked) {
                            Icons.Filled.Favorite
                        } else {
                            Icons.Outlined.FavoriteBorder
                        },
                        contentDescription = if (card.isLiked) "取消点赞" else "点赞",
                        tint = if (card.isLiked) SpiritRed else MutedInk,
                    )
                }
                IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (card.isFavorited) {
                            Icons.Filled.Bookmark
                        } else {
                            Icons.Outlined.BookmarkBorder
                        },
                        contentDescription = if (card.isFavorited) "取消收藏" else "收藏",
                        tint = if (card.isFavorited) SpiritRed else MutedInk,
                    )
                }
                IconButton(onClick = onNote, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.EditNote, contentDescription = "写笔记", tint = MutedInk)
                }
                IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Share, contentDescription = "生成图片并分享", tint = MutedInk)
                }
            }
        }
        val readingActions: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onBackground,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    Icon(
                        Icons.Outlined.AutoStories,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = SpiritRed,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "读背景",
                        color = SpiritRed,
                        fontSize = 14.sp,
                        letterSpacing = 0.sp,
                    )
                }
            }
        }
        if (compact) {
            Column(Modifier.fillMaxWidth()) {
                actions()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) { readingActions() }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
                readingActions()
            }
        }
    }
}
