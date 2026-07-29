package com.xuhuangbin.xinghuozhaidu.ui.detail

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.components.CardActions
import com.xuhuangbin.xinghuozhaidu.ui.components.FlippableQuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.components.BackgroundSheet
import com.xuhuangbin.xinghuozhaidu.ui.share.ShareCardRenderer
import com.xuhuangbin.xinghuozhaidu.ui.theme.SpiritRed
import kotlinx.coroutines.launch

@Composable
fun CardDetailScreen(
    card: QuoteCard,
    onBack: () -> Unit,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
    onNote: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var flipped by remember(card.id) { mutableStateOf(false) }
    var showBackground by remember(card.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                if (card.isWithdrawn) "卡片详情 · 已下架" else "卡片详情",
                color = if (card.isWithdrawn) SpiritRed else androidx.compose.ui.graphics.Color.Unspecified,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        FlippableQuoteCard(
            card = card,
            flipped = flipped,
            onFlippedChange = { flipped = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        CardActions(
            card = card,
            onBackground = { showBackground = true },
            onLike = onLike,
            onFavorite = onFavorite,
            onNote = onNote,
            onShare = {
                coroutineScope.launch {
                    runCatching { ShareCardRenderer.share(context, card) }
                        .onFailure {
                            Toast.makeText(context, "分享图片生成失败", Toast.LENGTH_SHORT).show()
                        }
                }
            },
        )
    }
    if (showBackground) {
        BackgroundSheet(
            card = card,
            onDismissRequest = { showBackground = false },
        )
    }
}
