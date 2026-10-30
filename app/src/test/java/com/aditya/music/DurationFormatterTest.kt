package com.aditya.music

import com.aditya.music.data.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatterTest {
    @Test fun formatsMinutesAndSeconds() {
        assertEquals("3:07", formatDuration(187_000L))
    }

    @Test fun formatsLongTracksWithHours() {
        assertEquals("1:02:03", formatDuration(3_723_000L))
    }

    @Test fun clampsNegativeDurationsToZero() {
        assertEquals("0:00", formatDuration(-1L))
    }
}
