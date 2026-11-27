package com.example.obdlab.ui.chart

import org.junit.Assert.*
import org.junit.Test

class TimeSeriesModelTest {
    @Test fun windowUsesSharedTimeCoordinates() {
        val window = ChartWindow.trailing(25_000, 20_000)
        assertEquals(5_000L, window.startMs)
        assertEquals(25_000L, window.endMs)
        assertEquals(0.5f, window.fraction(15_000), 0.0001f)
        assertEquals(0L, ChartWindow.trailing(0, 20_000).startMs)
    }

    @Test fun statisticsIgnoreInvalidValuesButCountRequestsForRate() {
        val stats = SeriesStats.from(listOf(ChartPoint(0, 20.0), ChartPoint(100, null), ChartPoint(200, 80.0)))
        assertEquals(20.0, stats.min!!, 0.0001)
        assertEquals(80.0, stats.max!!, 0.0001)
        assertEquals(10.0, stats.rateHz!!, 0.0001)
        assertEquals(2, stats.validCount)
    }

    @Test fun emptyAndSinglePointHaveNoRate() {
        assertNull(SeriesStats.from(emptyList()).min)
        assertNull(SeriesStats.from(listOf(ChartPoint(0, 0.0))).rateHz)
    }
}
