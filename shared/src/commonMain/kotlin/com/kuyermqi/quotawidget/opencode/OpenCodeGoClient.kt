package com.kuyermqi.quotawidget.opencode

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.formatUsagePrimaryDisplay
import com.kuyermqi.quotawidget.domain.formatUsageRemainingRolling
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max

/**
 * OpenCode Go quota via the official API-key endpoint:
 *
 *   GET https://opencode.ai/zen/go/v1/usage
 *   Authorization: Bearer <api-key>
 *
 * Replaces the legacy WebView cookie login + HTML/`_server` scraping.
 */
class OpenCodeGoClient(
    private val httpClient: HttpClient = createHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    suspend fun fetchQuota(apiKey: String): QuotaSnapshot {
        val key = apiKey.trim()
        require(key.isNotBlank()) { "OpenCode Go API Key 未配置" }
        val body = try {
            httpClient.get(USAGE_URL) {
                header(HttpHeaders.Authorization, "Bearer $key")
                accept(ContentType.Application.Json)
                header(HttpHeaders.UserAgent, API_UA)
            }.bodyAsText()
        } catch (e: ClientRequestException) {
            throw toAuthOrNetworkError(e)
        }
        val response = try {
            json.decodeFromString(ZenGoUsageResponse.serializer(), body)
        } catch (e: Exception) {
            throw IllegalStateException(
                "无法解析 OpenCode Go 额度 (prefix=${body.take(120).replace('\n', ' ')})",
                e,
            )
        }
        val usage = response.usage
            ?: throw IllegalStateException("OpenCode Go 返回数据为空")
        val nowMs = currentTimeMillis()
        val windows = usage.toWindows(nowMs)
        if (windows.isEmpty()) {
            throw IllegalStateException("无法解析 OpenCode Go 额度窗口")
        }
        return QuotaSnapshot(
            platformId = PlatformIds.OPENCODE_GO,
            platformName = PlatformRegistry.displayName(PlatformIds.OPENCODE_GO),
            windows = windows,
            primaryDisplay = formatUsageRemainingRolling(windows)
                ?: formatUsagePrimaryDisplay(windows),
            updatedAtEpochMs = nowMs,
        )
    }

    fun close() {
        httpClient.close()
    }

    private fun toAuthOrNetworkError(e: ClientRequestException): Exception =
        when (e.response.status) {
            HttpStatusCode.Unauthorized,
            HttpStatusCode.Forbidden,
            -> SessionExpiredException("OpenCode API Key 无效或已失效，请检查配置")
            else -> IllegalStateException(
                "OpenCode 请求失败 HTTP ${e.response.status.value}: ${e.message}",
                e,
            )
        }

    companion object {
        const val BASE_URL = "https://opencode.ai"
        const val USAGE_URL = "$BASE_URL/zen/go/v1/usage"

        private const val API_UA = "quota-widget/1.0 (OpenCode Go API key)"

        fun toWindows(usage: ZenGoUsage, nowEpochMs: Long = 0L): List<QuotaWindow> =
            usage.toWindows(nowEpochMs)

        /**
         * Parses a `resetsAt` timestamp like `2026-09-30T17:15:45.084Z` (UTC, `Z` suffix)
         * into epoch millis. Returns null when the shape is unexpected.
         */
        fun parseResetsAtUtc(resetsAt: String?): Long? {
            if (resetsAt.isNullOrBlank()) return null
            val m = RESETS_AT_UTC.matchEntire(resetsAt.trim()) ?: return null
            val year = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val day = m.groupValues[3].toInt()
            val hour = m.groupValues[4].toInt()
            val minute = m.groupValues[5].toInt()
            val second = m.groupValues[6].toInt()
            val millis = m.groupValues[7].let {
                if (it.isEmpty()) 0 else it.take(3).padEnd(3, '0').toInt()
            }
            if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) {
                return null
            }
            val days = daysSinceEpoch(year, month, day) ?: return null
            return days * 86_400_000L + hour * 3_600_000L + minute * 60_000L + second * 1_000L + millis
        }

        fun resetInSec(resetsAt: String?, nowEpochMs: Long): Long? {
            if (nowEpochMs <= 0L) return null
            val resetsMs = parseResetsAtUtc(resetsAt) ?: return null
            return max(0L, (resetsMs - nowEpochMs) / 1000L)
        }

        private val RESETS_AT_UTC =
            Regex("""(\d{4})-(\d{2})-(\d{2})[Tt](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?[Zz]""")

        private fun isLeapYear(year: Int): Boolean =
            (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

        private fun daysSinceEpoch(year: Int, month: Int, day: Int): Long? {
            if (year < 1970 || year > 9999) return null
            val dim = intArrayOf(
                31, if (isLeapYear(year)) 29 else 28, 31, 30, 31, 30,
                31, 31, 30, 31, 30, 31,
            )
            if (day > dim[month - 1]) return null
            var days = 0L
            for (y in 1970 until year) {
                days += if (isLeapYear(y)) 366 else 365
            }
            for (m in 1 until month) {
                days += dim[m - 1]
            }
            days += (day - 1)
            return days
        }
    }
}

@Serializable
data class ZenGoUsageResponse(
    val usage: ZenGoUsage? = null,
)

@Serializable
data class ZenGoUsage(
    val rolling: ZenGoWindow? = null,
    val weekly: ZenGoWindow? = null,
    val monthly: ZenGoWindow? = null,
) {
    fun toWindows(nowEpochMs: Long): List<QuotaWindow> = buildList {
        rolling?.toWindow(QuotaWindowKind.FIVE_HOUR, nowEpochMs)?.let(::add)
        weekly?.toWindow(QuotaWindowKind.WEEKLY, nowEpochMs)?.let(::add)
        monthly?.toWindow(QuotaWindowKind.MONTHLY, nowEpochMs)?.let(::add)
    }
}

@Serializable
data class ZenGoWindow(
    val status: String? = null,
    val percent: Double? = null,
    @SerialName("resetsAt") val resetsAt: String? = null,
) {
    fun toWindow(kind: QuotaWindowKind, nowEpochMs: Long): QuotaWindow? {
        val pct = percent ?: return null
        return QuotaWindow(
            kind = kind,
            usedPercent = pct.coerceIn(0.0, 100.0),
            resetInSec = OpenCodeGoClient.resetInSec(resetsAt, nowEpochMs),
        )
    }
}
