package com.attiqtherehman.kharcha.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Minimal chart primitives drawn with Compose Canvas: no chart library, no decoration beyond what
 * the numbers need. Colours come from the theme or category colours passed in by the caller.
 */

data class DonutSlice(val value: Float, val color: Color, val label: String)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    thickness: Dp = 22.dp,
    centerContent: @Composable () -> Unit = {},
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = thickness.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokeWidth))
            if (total <= 0f) return@Canvas
            var start = -90f
            val gap = if (slices.size > 1) 2f else 0f
            slices.forEach { slice ->
                val sweep = slice.value / total * 360f
                if (sweep > 0f) {
                    drawArc(slice.color, start + gap / 2f, (sweep - gap).coerceAtLeast(0.5f), false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Butt))
                }
                start += sweep
            }
        }
        centerContent()
    }
}

data class BarValue(val label: String, val value: Float, val secondary: Float = 0f)

/**
 * Vertical bars; [secondary] draws a second series side by side (used for income vs expenses).
 */
@Composable
fun BarChart(
    values: List<BarValue>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.tertiary,
    showSecondary: Boolean = false,
    labelEvery: Int = 1,
    highlightIndex: Int? = null,
) {
    val maxValue = values.maxOfOrNull { maxOf(it.value, if (showSecondary) it.secondary else 0f) }?.takeIf { it > 0f } ?: 1f
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val n = values.size
            if (n == 0) return@Canvas
            val slot = size.width / n
            val barWidth = if (showSecondary) slot * 0.32f else slot * 0.55f
            // Baseline and a mid grid line
            drawLine(gridColor, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1f)
            values.forEachIndexed { index, bar ->
                val x = index * slot + (slot - (if (showSecondary) barWidth * 2 + 4f else barWidth)) / 2f
                val h = (bar.value / maxValue) * (size.height - 4f)
                val color = if (highlightIndex == index) primaryColor else primaryColor.copy(alpha = if (highlightIndex == null) 1f else 0.55f)
                drawRoundRect(color, Offset(x, size.height - h), Size(barWidth, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                if (showSecondary) {
                    val h2 = (bar.secondary / maxValue) * (size.height - 4f)
                    drawRoundRect(secondaryColor, Offset(x + barWidth + 4f, size.height - h2), Size(barWidth, h2), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            values.forEachIndexed { index, bar ->
                Text(
                    text = if (index % labelEvery == 0) bar.label else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Simple line chart with a filled area under the line. */
@Composable
fun LineChart(
    points: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    labelEvery: Int = 1,
) {
    val maxValue = points.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            if (points.isEmpty()) return@Canvas
            drawLine(gridColor, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 1f)
            val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width
            val path = Path()
            val fill = Path()
            points.forEachIndexed { i, v ->
                val x = if (points.size > 1) i * stepX else size.width / 2f
                val y = size.height - (v / maxValue) * (size.height - 6f) - 3f
                if (i == 0) { path.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) } else { path.lineTo(x, y); fill.lineTo(x, y) }
                drawCircle(color, radius = 4f, center = Offset(x, y))
            }
            fill.lineTo(if (points.size > 1) (points.size - 1) * stepX else size.width / 2f, size.height)
            fill.close()
            drawPath(fill, color.copy(alpha = 0.12f))
            drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = if (index % labelEvery == 0) label else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Horizontal progress bar with a colour that follows the fraction. */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 8.dp,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(track, Offset.Zero, size, r)
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(color, Offset.Zero, Size(w, size.height), r)
    }
}
