package com.ella.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ella.music.data.BottomBarGlassEffect
import com.kyant.backdrop.Backdrop
import kotlin.math.abs
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LiquidGlassBottomBar(
    backdrop: Backdrop?,
    isBlurEnabled: Boolean = true,
    glassEffect: BottomBarGlassEffect = BottomBarGlassEffect.Blur,
    selectedIndex: Int? = null,
    itemCount: Int = 0,
    onSelected: ((Int) -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val density = LocalDensity.current
    val isLight = MiuixTheme.colorScheme.background.simpleLuminance() > 0.5f
    val selectedIndexState = rememberUpdatedState(selectedIndex)
    val onSelectedState = rememberUpdatedState(onSelected)
    var barWidthPx by remember { mutableIntStateOf(0) }
    var dragCenterX by remember { mutableFloatStateOf(0f) }
    var isPressed by remember { mutableStateOf(false) }
    val validItemCount = itemCount.coerceAtLeast(0)
    val enablePressBubble = glassEffect == BottomBarGlassEffect.LiquidGlass
    val sidePaddingPx = with(density) { 4.dp.toPx() }
    val bubbleHeight = 66.dp
    val bubbleHeightPx = with(density) { bubbleHeight.toPx() }
    val tabWidthPx = if (validItemCount > 0) {
        ((barWidthPx - sidePaddingPx * 2f) / validItemCount).coerceAtLeast(1f)
    } else {
        1f
    }
    val tabWidthState = rememberUpdatedState(tabWidthPx)
    val sidePaddingState = rememberUpdatedState(sidePaddingPx)
    val targetBubbleWidthPx = when {
        validItemCount <= 0 -> bubbleHeightPx
        validItemCount <= 2 -> (tabWidthPx * 0.82f).coerceIn(bubbleHeightPx * 1.32f, with(density) { 142.dp.toPx() })
        validItemCount == 3 -> (tabWidthPx * 0.82f).coerceIn(bubbleHeightPx * 1.08f, with(density) { 112.dp.toPx() })
        else -> (tabWidthPx * 0.86f).coerceIn(bubbleHeightPx, with(density) { 86.dp.toPx() })
    }
    val bubbleWidthPx by animateFloatAsState(
        targetValue = targetBubbleWidthPx,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 680f),
        label = "BottomBarBubbleWidth"
    )
    val bubbleX = if (isPressed && barWidthPx > 0) {
        (dragCenterX - targetBubbleWidthPx / 2f)
            .coerceIn(0f, (barWidthPx - targetBubbleWidthPx).coerceAtLeast(0f))
    } else {
        0f
    }
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (enablePressBubble && isPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 680f),
        label = "BottomBarBubbleAlpha"
    )
    val bubbleScale by animateFloatAsState(
        targetValue = if (isPressed) 1.16f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 760f),
        label = "BottomBarBubbleScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .onSizeChanged { barWidthPx = it.width }
            .pointerInput(validItemCount, barWidthPx) {
                if (validItemCount <= 0 || onSelectedState.value == null || barWidthPx <= 0) return@pointerInput

                fun hitIndexFor(x: Float): Int? {
                    val tabWidth = tabWidthState.value
                    val sidePadding = sidePaddingState.value
                    if (tabWidth <= 0f) return null
                    val rawIndex = (((x - sidePadding) / tabWidth).toInt()).coerceIn(0, validItemCount - 1)
                    val center = sidePadding + tabWidth * (rawIndex + 0.5f)
                    val activeRadius = tabWidth * 0.34f
                    return rawIndex.takeIf { abs(x - center) <= activeRadius }
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragCenterX = down.position.x
                    isPressed = true
                    var releaseIndex = hitIndexFor(down.position.x)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        dragCenterX = change.position.x
                        releaseIndex = hitIndexFor(change.position.x)
                        if (!change.pressed) break
                    }
                    releaseIndex
                        ?.takeIf { it != selectedIndexState.value }
                        ?.let { onSelectedState.value?.invoke(it) }
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        GlassPill(
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.Center),
            shape = RoundedCornerShape(32.dp),
            glassEffect = glassEffect
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }

        if (enablePressBubble) {
            GlassPill(
                backdrop = if (isBlurEnabled) backdrop else null,
                modifier = Modifier
                    .width(with(density) { bubbleWidthPx.toDp() })
                    .height(bubbleHeight)
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(bubbleX.roundToInt(), 0) }
                    .graphicsLayer {
                        alpha = bubbleAlpha
                        scaleX = bubbleScale
                        scaleY = bubbleScale
                    },
                shape = RoundedCornerShape(bubbleHeight / 2),
                blurRadius = 8f,
                liquidBlurRadius = 32f,
                glassEffect = BottomBarGlassEffect.LiquidGlass
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = if (isLight) {
                                Color.White.copy(alpha = if (isPressed) 0.22f else 0.10f)
                            } else {
                                Color.White.copy(alpha = if (isPressed) 0.10f else 0.06f)
                            },
                            shape = RoundedCornerShape(bubbleHeight / 2)
                        )
                        .liquidGlassDepthOverlay(
                            enabled = isPressed,
                            isLight = isLight,
                            cornerRadius = bubbleHeight / 2
                        )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.Center)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun RowScope.LiquidGlassBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop?,
    isBlurEnabled: Boolean = true,
    showSelectedIndicator: Boolean = true,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit
) {
    val isLight = MiuixTheme.colorScheme.background.simpleLuminance() > 0.5f
    val contentScale by animateFloatAsState(
        targetValue = if (selected) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 820f),
        label = "BottomBarItemContentScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.62f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "BottomBarItemContentAlpha"
    )
    val selectedIndicatorAlpha by animateFloatAsState(
        targetValue = if (selected && showSelectedIndicator) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 700f),
        label = "BottomBarItemSelectedIndicatorAlpha"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .height(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(88.dp)
                .height(56.dp)
                .graphicsLayer { alpha = selectedIndicatorAlpha }
                .background(
                    color = if (isLight) {
                        Color.Black.copy(alpha = 0.08f)
                    } else {
                        Color.White.copy(alpha = 0.08f)
                    },
                    shape = RoundedCornerShape(28.dp)
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    scaleX = contentScale
                    scaleY = contentScale
                }
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box {
                icon()
            }
            Box(modifier = Modifier.graphicsLayer { alpha = 0.88f }) {
                label()
            }
        }
    }
}
