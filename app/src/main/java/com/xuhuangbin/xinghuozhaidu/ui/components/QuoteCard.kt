package com.xuhuangbin.xinghuozhaidu.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.theme.Divider as DividerColor
import com.xuhuangbin.xinghuozhaidu.ui.theme.Ink
import com.xuhuangbin.xinghuozhaidu.ui.theme.MutedInk
import com.xuhuangbin.xinghuozhaidu.ui.theme.Paper
import com.xuhuangbin.xinghuozhaidu.ui.theme.QuoteFontFamily
import com.xuhuangbin.xinghuozhaidu.ui.theme.SpiritRed
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlinx.coroutines.launch

private const val DegreesPerFlip = 180f
private const val DragWidthPerFlip = 0.72f
private const val FlipDistanceThreshold = 0.22f

private val FlipSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

@Composable
fun FlippableQuoteCard(
    card: QuoteCard,
    flipped: Boolean,
    onFlippedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val localDensity = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val cardShape = remember { RoundedCornerShape(8.dp) }
    val rotation = remember(card.id) { Animatable(if (flipped) DegreesPerFlip else 0f) }
    var cardWidthPx by remember(card.id) { mutableIntStateOf(1) }
    var dragStartRotation by remember(card.id) { mutableFloatStateOf(rotation.value) }
    var dragDistancePx by remember(card.id) { mutableFloatStateOf(0f) }
    var isDragging by remember(card.id) { mutableStateOf(false) }
    var isSettling by remember(card.id) { mutableStateOf(false) }
    val flingThresholdPx = with(localDensity) { 900.dp.toPx() }

    fun applyHorizontalDrag(deltaPx: Float) {
        dragDistancePx += deltaPx
        val degreesPerPixel = DegreesPerFlip / (cardWidthPx * DragWidthPerFlip)
        val targetRotation = dragStartRotation +
            (dragDistancePx * degreesPerPixel).coerceIn(-DegreesPerFlip, DegreesPerFlip)
        coroutineScope.launch { rotation.snapTo(targetRotation) }
    }

    fun beginHorizontalDrag(initialDeltaPx: Float) {
        coroutineScope.launch { rotation.stop() }
        dragStartRotation = rotation.value
        dragDistancePx = 0f
        isDragging = true
        if (initialDeltaPx != 0f) {
            applyHorizontalDrag(initialDeltaPx)
        }
    }

    fun settleHorizontalDrag(velocityPx: Float) {
        val startFlipped = isBackFace(dragStartRotation)
        val baseRotation = nearestStableRotation(dragStartRotation, startFlipped)
        val distanceReached = abs(dragDistancePx) >= cardWidthPx * FlipDistanceThreshold
        val flingReached = abs(velocityPx) >= flingThresholdPx
        val direction = when {
            flingReached -> velocityPx.sign
            dragDistancePx != 0f -> dragDistancePx.sign
            else -> 1f
        }
        val targetRotation = if (distanceReached || flingReached) {
            baseRotation + direction * DegreesPerFlip
        } else {
            baseRotation
        }
        val targetFlipped = stableRotationIsBack(targetRotation)

        isDragging = false
        isSettling = true
        coroutineScope.launch {
            try {
                onFlippedChange(targetFlipped)
                rotation.animateTo(
                    targetValue = targetRotation,
                    animationSpec = FlipSpring,
                    initialVelocity = velocityPx * DegreesPerFlip /
                        (cardWidthPx * DragWidthPerFlip),
                )
                rotation.snapTo(normalizedStableRotation(targetRotation, targetFlipped))
            } finally {
                isSettling = false
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(card.id, flipped) {
        if (!isDragging && !isSettling && isBackFace(rotation.value) != flipped) {
            rotation.animateTo(
                targetValue = nearestStableRotation(rotation.value, flipped),
                animationSpec = FlipSpring,
            )
            rotation.snapTo(normalizedStableRotation(rotation.value, flipped))
        }
    }

    val rotationValue = rotation.value
    val sideProfile = abs(sin(Math.toRadians(rotationValue.toDouble()))).toFloat()
    val showingBack = isBackFace(rotationValue)
    Surface(
        modifier = modifier
            .onSizeChanged { cardWidthPx = it.width.coerceAtLeast(1) }
            .semantics {
                stateDescription = if (flipped) "卡片解读面" else "卡片正面"
                customActions = listOf(
                    CustomAccessibilityAction(if (flipped) "返回正面" else "查看解读") {
                        onFlippedChange(!flipped)
                        true
                    },
                )
            }
            .pointerInput(card.id, flipped, cardWidthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    var pointerId = down.id
                    var totalDrag = Offset.Zero
                    var horizontalDragActive = false
                    var shouldSettle = false

                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val trackedChange = event.changes.firstOrNull { it.id == pointerId }
                            val change = if (trackedChange?.pressed == true) {
                                trackedChange
                            } else {
                                event.changes.firstOrNull { it.pressed }?.also {
                                    pointerId = it.id
                                    velocityTracker.addPosition(it.uptimeMillis, it.position)
                                } ?: break
                            }
                            val delta = change.positionChangeIgnoreConsumed()
                            if (delta == Offset.Zero) continue

                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            if (!horizontalDragActive) {
                                totalDrag += delta
                                val absX = abs(totalDrag.x)
                                val absY = abs(totalDrag.y)
                                if (absY > viewConfiguration.touchSlop && absY > absX) {
                                    return@awaitEachGesture
                                }
                                if (absX > viewConfiguration.touchSlop && absX > absY) {
                                    horizontalDragActive = true
                                    shouldSettle = true
                                    val overSlop = totalDrag.x - totalDrag.x.sign *
                                        viewConfiguration.touchSlop
                                    beginHorizontalDrag(overSlop)
                                }
                            } else {
                                applyHorizontalDrag(delta.x)
                            }
                        }
                    } finally {
                        if (shouldSettle) {
                            settleHorizontalDrag(velocityTracker.calculateVelocity().x)
                        }
                    }
                }
            }
            .graphicsLayer {
                rotationY = rotationValue
                cameraDistance = 24f * density
                scaleX = 1f - sideProfile * 0.045f
                scaleY = 1f - sideProfile * 0.016f
            },
        shape = cardShape,
        color = Paper,
        border = BorderStroke(1.dp, DividerColor),
        shadowElevation = 2.dp,
    ) {
        if (!showingBack) {
            QuoteFront(card)
        } else {
            Box(Modifier.graphicsLayer { rotationY = 180f }) {
                InterpretationBack(card)
            }
        }
    }
}

@Composable
private fun QuoteFront(card: QuoteCard) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val quoteLength = card.quote.codePointCount(0, card.quote.length)
        val compactHeight = maxHeight < 480.dp
        if (card.imagePath.isNotBlank()) {
            AsyncImage(
                model = File(card.imagePath),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.17f),
                contentScale = ContentScale.Crop,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (compactHeight) 24.dp else 28.dp,
                    vertical = if (compactHeight) 24.dp else 34.dp,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = card.quote,
                    color = Ink,
                    fontSize = quoteFontSize(quoteLength, compactHeight),
                    lineHeight = quoteLineHeight(quoteLength, compactHeight),
                    fontFamily = QuoteFontFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                )
            }
            Column {
                Box(
                    Modifier
                        .fillMaxWidth(0.18f)
                        .height(3.dp)
                        .background(SpiritRed),
                )
                Spacer(Modifier.height(if (compactHeight) 8.dp else 12.dp))
                Text(
                    text = "《${card.workTitle}》",
                    color = SpiritRed,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                )
                Text(
                    text = "${card.series} · ${card.volume} · ${card.authoredAt}",
                    color = MutedInk,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp,
                )
            }
        }
    }
}

@Composable
private fun InterpretationBack(card: QuoteCard) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        InterpretationSection("启示", card.interpretation.inspiration, prominent = true)
        HorizontalDivider(color = DividerColor)
        InterpretationSection("解读", card.interpretation.explanation)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun InterpretationSection(title: String, body: String, prominent: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = SpiritRed,
            fontSize = if (prominent) 20.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        if (prominent) {
            Box(
                Modifier
                    .fillMaxWidth(0.42f)
                    .height(2.dp)
                    .background(SpiritRed),
            )
        }
        BodyText(body)
    }
}

@Composable
private fun BodyText(text: String) {
    Text(text, color = Ink, fontSize = 16.sp, lineHeight = 27.sp, letterSpacing = 0.sp)
}

private fun quoteFontSize(length: Int, compactHeight: Boolean) = when {
    compactHeight && length > 75 -> 20.sp
    compactHeight && length > 60 -> 22.sp
    compactHeight && length > 32 -> 25.sp
    length <= 32 -> 34.sp
    length <= 60 -> 31.sp
    else -> 28.sp
}

private fun quoteLineHeight(length: Int, compactHeight: Boolean) = when {
    compactHeight && length > 75 -> 28.sp
    compactHeight && length > 60 -> 30.sp
    compactHeight && length > 32 -> 34.sp
    length <= 32 -> 49.sp
    length <= 60 -> 45.sp
    else -> 41.sp
}

private fun isBackFace(rotation: Float): Boolean {
    val normalized = ((rotation % 360f) + 360f) % 360f
    return normalized >= 90f && normalized < 270f
}

private fun stableRotationIsBack(rotation: Float): Boolean =
    abs((rotation / DegreesPerFlip).roundToInt()) % 2 == 1

private fun nearestStableRotation(rotation: Float, flipped: Boolean): Float {
    val centerStep = (rotation / DegreesPerFlip).roundToInt()
    return (centerStep - 2..centerStep + 2)
        .filter { abs(it) % 2 == if (flipped) 1 else 0 }
        .map { it * DegreesPerFlip }
        .sortedWith(compareBy<Float> { abs(it - rotation) }.thenByDescending { it })
        .first()
}

private fun normalizedStableRotation(rotation: Float, flipped: Boolean): Float = when {
    !flipped -> 0f
    rotation < 0f -> -DegreesPerFlip
    else -> DegreesPerFlip
}
