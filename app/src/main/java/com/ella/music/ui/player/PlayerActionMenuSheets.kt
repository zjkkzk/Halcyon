package com.ella.music.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.round

@Composable
internal fun HalfSheetTitle(title: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "‹",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 2.dp)
        )
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
internal fun HalfSheetPill(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun DottedValueSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val safeValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val fraction = ((safeValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val activeDotColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.72f)
    val inactiveDotColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.28f)
    val activeLineColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.88f)
    val activeKnobColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.92f)

    fun update(width: Float, x: Float) {
        val raw = valueRange.start + (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f) *
            (valueRange.endInclusive - valueRange.start)
        val stepped = if (steps > 0) {
            val stepSize = (valueRange.endInclusive - valueRange.start) / steps
            (round(raw / stepSize) * stepSize).coerceIn(valueRange.start, valueRange.endInclusive)
        } else {
            raw
        }
        onValueChange(stepped)
    }

    BoxWithConstraints(modifier = modifier) {
        val knobOffset = (maxWidth - 46.dp) * fraction
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(valueRange, steps) {
                    detectTapGestures { offset -> update(size.width.toFloat(), offset.x) }
                }
                .pointerInput(valueRange, steps) {
                    detectDragGestures { change, _ -> update(size.width.toFloat(), change.position.x) }
                }
        ) {
            val centerY = size.height * 0.60f
            val dotCount = 44
            val gap = size.width / (dotCount - 1).coerceAtLeast(1)
            for (index in 0 until dotCount) {
                val dotFraction = index.toFloat() / (dotCount - 1).coerceAtLeast(1)
                drawCircle(
                    color = if (dotFraction <= fraction) activeDotColor else inactiveDotColor,
                    radius = if (index % 5 == 0) 4.2f else 3.2f,
                    center = Offset(x = gap * index, y = centerY)
                )
            }
            val knobX = size.width * fraction
            drawLine(
                color = activeLineColor,
                start = Offset(knobX, centerY - 36f),
                end = Offset(knobX, centerY + 36f),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = activeKnobColor,
                radius = 24f,
                center = Offset(knobX, centerY - 54f)
            )
        }
        label?.let {
            Text(
                text = it,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = knobOffset)
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MiuixTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
internal fun PlayerActionMenuItem(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = if (danger) Color(0xFFE5484D) else MiuixTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 13.dp)
    )
}
