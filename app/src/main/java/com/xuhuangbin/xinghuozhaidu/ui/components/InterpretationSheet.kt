package com.xuhuangbin.xinghuozhaidu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
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
fun InterpretationSheet(
    card: QuoteCard,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "解读",
                        color = Ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                    )
                    Text(
                        text = "《${card.workTitle}》",
                        color = MutedInk,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        letterSpacing = 0.sp,
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭解读",
                        tint = MutedInk,
                    )
                }
            }
            HorizontalDivider(color = Divider)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .testTag("interpretationContent")
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                InterpretationSection("核心意思", card.interpretation.coreMeaning)
                HorizontalDivider(color = Divider)
                InterpretationSection("理解重点", card.interpretation.keyPoint)
                HorizontalDivider(color = Divider)
                InterpretationSection("现实启示", card.interpretation.contemporaryRelevance)
            }
        }
    }
}

@Composable
private fun InterpretationSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = SpiritRed,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Text(
            text = body,
            color = Ink,
            fontSize = 15.sp,
            lineHeight = 25.sp,
            letterSpacing = 0.sp,
        )
    }
}
