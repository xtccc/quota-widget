package com.kuyermqi.quotawidget.opencode

import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenCodeGoClientTest {
    @Test
    fun usageUrl_pointsToZenGoEndpoint() {
        assertEquals(
            "https://opencode.ai/zen/go/v1/usage",
            OpenCodeGoClient.USAGE_URL,
        )
    }

    @Test
    fun toWindows_mapsRollingWeeklyMonthly() {
        val usage = ZenGoUsage(
            rolling = ZenGoWindow(status = "ok", percent = 1.0, resetsAt = "2026-09-30T17:15:45.084Z"),
            weekly = ZenGoWindow(status = "ok", percent = 25.0, resetsAt = "2026-10-05T00:00:00.000Z"),
            monthly = ZenGoWindow(status = "ok", percent = 29.5, resetsAt = "2026-10-21T07:45:46.000Z"),
        )
        // now = 2026-09-30T17:00:00Z
        val nowMs = 1_790_787_600_000L
        val windows = usage.toWindows(nowMs)
        assertEquals(3, windows.size)
        assertEquals(QuotaWindowKind.FIVE_HOUR, windows[0].kind)
        assertEquals(1.0, windows[0].usedPercent)
        assertEquals(QuotaWindowKind.WEEKLY, windows[1].kind)
        assertEquals(25.0, windows[1].usedPercent)
        assertEquals(QuotaWindowKind.MONTHLY, windows[2].kind)
        assertEquals(29.5, windows[2].usedPercent)
        assertTrue((windows[0].resetInSec ?: -1L) > 0L)
    }

    @Test
    fun toWindows_skipsMissingWindows() {
        val usage = ZenGoUsage(
            weekly = ZenGoWindow(status = "ok", percent = 10.0, resetsAt = null),
        )
        val windows = usage.toWindows(1_790_787_600_000L)
        assertEquals(1, windows.size)
        assertEquals(QuotaWindowKind.WEEKLY, windows.single().kind)
        assertNull(windows.single().resetInSec)
    }

    @Test
    fun parseResetsAtUtc_parsesMillisecondTimestamp() {
        // 2026-09-30T17:15:45.084Z
        assertEquals(1_790_788_545_084L, OpenCodeGoClient.parseResetsAtUtc("2026-09-30T17:15:45.084Z"))
    }

    @Test
    fun parseResetsAtUtc_parsesSecondPrecisionTimestamp() {
        // 2026-10-05T00:00:00Z
        assertNotNull(OpenCodeGoClient.parseResetsAtUtc("2026-10-05T00:00:00Z"))
    }

    @Test
    fun parseResetsAtUtc_rejectsGarbage() {
        assertNull(OpenCodeGoClient.parseResetsAtUtc(null))
        assertNull(OpenCodeGoClient.parseResetsAtUtc(""))
        assertNull(OpenCodeGoClient.parseResetsAtUtc("not-a-date"))
        assertNull(OpenCodeGoClient.parseResetsAtUtc("2026-13-01T00:00:00Z"))
    }

    @Test
    fun resetInSec_countsDownToReset() {
        val resetsAt = "2026-09-30T17:15:45Z"
        val resetsMs = OpenCodeGoClient.parseResetsAtUtc(resetsAt)
        assertNotNull(resetsMs)
        assertEquals(945L, OpenCodeGoClient.resetInSec(resetsAt, resetsMs - 945_000L))
        assertEquals(0L, OpenCodeGoClient.resetInSec(resetsAt, resetsMs + 60_000L))
    }
}
