package com.xuhuangbin.xinghuozhaidu.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.components.CardSummaryList
import com.xuhuangbin.xinghuozhaidu.ui.theme.Divider
import com.xuhuangbin.xinghuozhaidu.ui.theme.Ink
import com.xuhuangbin.xinghuozhaidu.ui.theme.MutedInk

@Composable
fun SearchScreen(
    query: String,
    results: List<QuoteCard>,
    history: List<String>,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onHistoryDelete: (String) -> Unit,
    onHistoryClear: () -> Unit,
    onBack: () -> Unit,
    onCardClick: (QuoteCard) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var hasRequestedFocus by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(hasRequestedFocus) {
        if (!hasRequestedFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
            hasRequestedFocus = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearchSubmit()
                    keyboardController?.hide()
                },
            ),
            placeholder = { Text("搜索名言或篇名") },
            leadingIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Clear, contentDescription = "清空搜索关键词")
                    }
                } else {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                }
            },
        )
        if (query.isBlank()) {
            SearchHistoryList(
                history = history,
                onHistoryClick = onQueryChange,
                onHistoryDelete = onHistoryDelete,
                onHistoryClear = onHistoryClear,
                modifier = Modifier.weight(1f),
            )
        } else {
            CardSummaryList(
                cards = results,
                emptyText = "没有找到相关内容",
                onCardClick = onCardClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SearchHistoryList(
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    onHistoryClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "搜索记录",
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (history.isNotEmpty()) {
                IconButton(onClick = onHistoryClear) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = "清空全部搜索记录")
                }
            }
        }
        HorizontalDivider(color = Divider)
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无搜索记录", color = MutedInk, fontSize = 15.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(history, key = { it }) { keyword ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onHistoryClick(keyword) }
                            .padding(start = 18.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = MutedInk,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = keyword,
                            color = Ink,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onHistoryDelete(keyword) }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "删除搜索记录：$keyword",
                                tint = MutedInk,
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 50.dp),
                        color = Divider,
                    )
                }
            }
        }
    }
}
