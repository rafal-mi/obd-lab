package com.example.obdlab.ui.chart

data class ChartPoint(val elapsedMs: Long, val value: Double?)

data class ChartWindow(val startMs: Long, val endMs: Long) {
    init { require(endMs > startMs) }
    fun fraction(time: Long): Float = ((time - startMs).toDouble() / (endMs - startMs)).toFloat()
    companion object {
        fun trailing(latestMs: Long, durationMs: Long): ChartWindow {
            require(latestMs >= 0 && durationMs > 0)
            val end = maxOf(latestMs, durationMs)
            return ChartWindow(end - durationMs, end)
        }
    }
}

data class SeriesStats(val min: Double?, val max: Double?, val rateHz: Double?, val validCount: Int) {
    companion object {
        fun from(points: List<ChartPoint>): SeriesStats {
            val values = points.mapNotNull { it.value?.takeIf(Double::isFinite) }
            val span = if (points.size > 1) points.last().elapsedMs - points.first().elapsedMs else 0
            return SeriesStats(values.minOrNull(), values.maxOrNull(),
                if (span > 0) (points.size - 1) * 1000.0 / span else null, values.size)
        }
    }
}
