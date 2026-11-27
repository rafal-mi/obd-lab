package com.example.obdlab.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import java.util.Locale

data class ChartSeries(val label: String, val color: Color, val points: List<ChartPoint>)

/** All series share the supplied time window and fixed numeric value range. */
@Composable
fun TimeSeriesChart(
    series: List<ChartSeries>,
    window: ChartWindow,
    yMin: Double,
    yMax: Double,
    unit: String,
    modifier: Modifier = Modifier
) {
    require(yMax > yMin)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(180.dp)) {
            Column(Modifier.width(58.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End) {
                for (i in 0..4) {
                    val value = yMax - (yMax - yMin) * i / 4
                    Text("%g%s".format(Locale.ROOT, value, unit).let {
                        if (value == value.toLong().toDouble()) "${value.toLong()}$unit" else "%.1f%s".format(Locale.ROOT, value, unit)
                    }, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(6.dp))
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val top = 7.dp.toPx()
                val bottom = size.height - top
                val left = 1.dp.toPx()
                val right = size.width - left
                if (bottom <= top || right <= left) return@Canvas
                val width = right - left
                val height = bottom - top
                for (i in 0..4) {
                    val y = top + height * i / 4
                    val x = left + width * i / 4
                    drawLine(grid, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                    drawLine(grid, Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
                }
                drawLine(axis, Offset(left, top), Offset(left, bottom), 1.dp.toPx())
                drawLine(axis, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())
                fun point(p: ChartPoint): Offset? {
                    val v = p.value?.takeIf { it.isFinite() } ?: return null
                    return Offset(left + width * window.fraction(p.elapsedMs),
                        bottom - height * ((v - yMin) / (yMax - yMin)).toFloat())
                }
                clipRect(left, top, right, bottom) {
                    series.forEachIndexed { index, channel ->
                        // Different dash patterns also distinguish overlapping channels.
                        val effect = when (index % 4) {
                            1 -> PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx()))
                            2 -> PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
                            3 -> PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(), 5.dp.toPx(), 2.dp.toPx(), 5.dp.toPx()))
                            else -> null
                        }
                        for ((a,b) in channel.points.zipWithNext()) {
                            if (b.elapsedMs < window.startMs || a.elapsedMs > window.endMs) continue
                            val pa = point(a) ?: continue
                            val pb = point(b) ?: continue
                            drawLine(channel.color, pa, pb, strokeWidth = 2.dp.toPx(), pathEffect = effect)
                        }
                        channel.points.lastOrNull()?.let { p ->
                            if (p.elapsedMs in window.startMs..window.endMs) point(p)?.let {
                                drawCircle(channel.color, 3.dp.toPx(), it)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 64.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 0..4) {
                val t = window.startMs + (window.endMs - window.startMs) * i / 4
                Text("%.1f".format(Locale.ROOT, t / 1000.0), style = MaterialTheme.typography.labelSmall)
            }
        }
        Text("Elapsed time (s)", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall)
        for (channel in series) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).background(channel.color))
                Text(channel.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
