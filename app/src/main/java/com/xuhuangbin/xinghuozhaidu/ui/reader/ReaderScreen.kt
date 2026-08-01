package com.xuhuangbin.xinghuozhaidu.ui.reader

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xuhuangbin.xinghuozhaidu.domain.model.ReaderState
import com.xuhuangbin.xinghuozhaidu.ui.components.CardActions
import com.xuhuangbin.xinghuozhaidu.ui.components.FlippableQuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.components.BackgroundSheet
import com.xuhuangbin.xinghuozhaidu.ui.share.ShareCardRenderer
import com.xuhuangbin.xinghuozhaidu.ui.theme.MutedInk
import com.xuhuangbin.xinghuozhaidu.ui.theme.SpiritRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    state: ReaderState,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onSearch: () -> Unit,
    onPositionChanged: (Int) -> Unit,
    onRead: (String) -> Unit,
    onLike: (String) -> Unit,
    onFavorite: (String) -> Unit,
    onNote: (String) -> Unit,
    onNewRound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        ReaderHeader(
            onSearch = onSearch,
        )
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SpiritRed)
            }
            errorMessage != null -> ReaderError(errorMessage, onRetry)
            state.cards.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("当前没有可阅读的卡片", color = MutedInk)
            }
            else -> ReaderPager(
                state = state,
                onPositionChanged = onPositionChanged,
                onRead = onRead,
                onLike = onLike,
                onFavorite = onFavorite,
                onNote = onNote,
                onNewRound = onNewRound,
            )
        }
    }
}

@Composable
private fun ReaderHeader(
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSearch) {
            Icon(Icons.Outlined.Search, contentDescription = "搜索名言")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderPager(
    state: ReaderState,
    onPositionChanged: (Int) -> Unit,
    onRead: (String) -> Unit,
    onLike: (String) -> Unit,
    onFavorite: (String) -> Unit,
    onNote: (String) -> Unit,
    onNewRound: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    var flippedCardId by remember(state.roundId) { mutableStateOf<String?>(null) }
    var backgroundCardId by remember(state.roundId) { mutableStateOf<String?>(null) }
    val pageCount = state.cards.size + if (state.isComplete) 1 else 0
    val pagerState = rememberPagerState(pageCount = { pageCount })

    LaunchedEffect(state.roundId, state.cards.size) {
        if (state.cards.isNotEmpty()) pagerState.scrollToPage(state.currentIndex)
    }
    LaunchedEffect(pagerState.settledPage, state.roundId) {
        val page = pagerState.settledPage
        flippedCardId = null
        backgroundCardId = null
        if (page < state.cards.size) onPositionChanged(page)
    }
    LaunchedEffect(
        pagerState.settledPage,
        pagerState.isScrollInProgress,
        lifecycleState,
        state.roundId,
    ) {
        val page = pagerState.settledPage
        if (!pagerState.isScrollInProgress &&
            page < state.cards.size &&
            lifecycleState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            val cardId = state.cards[page].id
            delay(3_000)
            if (!pagerState.isScrollInProgress &&
                pagerState.settledPage == page &&
                lifecycleState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                onRead(cardId)
            }
        }
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = backgroundCardId == null,
        beyondViewportPageCount = 1,
    ) { page ->
        if (page == state.cards.size) {
            RoundComplete(state, onNewRound)
        } else {
            val card = state.cards[page]
            val flipped = flippedCardId == card.id
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                FlippableQuoteCard(
                    card = card,
                    flipped = flipped,
                    onFlippedChange = { shouldFlip ->
                        flippedCardId = card.id.takeIf { shouldFlip }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                CardActions(
                    card = card,
                    onBackground = { backgroundCardId = card.id },
                    onLike = { onLike(card.id) },
                    onFavorite = { onFavorite(card.id) },
                    onNote = { onNote(card.id) },
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
        }
    }
    state.cards.firstOrNull { it.id == backgroundCardId }?.let { card ->
        BackgroundSheet(
            card = card,
            onDismissRequest = { backgroundCardId = null },
        )
    }
}

@Composable
private fun RoundComplete(state: ReaderState, onNewRound: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("本轮已读完", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(18.dp))
        Text(
            "已读 ${state.readCardIds.size} · 点赞 ${state.cards.count { it.isLiked }} · 收藏 ${state.cards.count { it.isFavorited }}",
            color = MutedInk,
            fontSize = 14.sp,
        )
        Spacer(Modifier.size(28.dp))
        Button(onClick = onNewRound) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Text("开始新一轮")
        }
    }
}

@Composable
private fun ReaderError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.size(20.dp))
        Button(onClick = onRetry) { Text("重新初始化") }
    }
}
