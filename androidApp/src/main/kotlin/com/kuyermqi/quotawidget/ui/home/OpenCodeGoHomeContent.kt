package com.kuyermqi.quotawidget.ui.home

import android.content.res.Resources
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.OpenCodeGoSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.components.OpenCodeGoConfigContent
import com.kuyermqi.quotawidget.ui.usage.formatUsageWindowSummary
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import kotlinx.coroutines.launch

class OpenCodeGoHomeState internal constructor(
    private val settingsRepository: PlatformSettingsRepository,
) {
    var settings by mutableStateOf(OpenCodeGoSettings())
        internal set
    var draftApiKey by mutableStateOf("")
        internal set
    var draftWindowKind by mutableStateOf(UsageWindowKind.ROLLING)
        internal set
    var draftUsageDisplayMode by mutableStateOf(UsageDisplayMode.USED)
        internal set
    var draftUsageProgressStyle by mutableStateOf(UsageProgressStyle.BAR)
        internal set
    var lastDisplay by mutableStateOf<String?>(null)
        internal set
    var lastWindows by mutableStateOf<List<QuotaWindow>>(emptyList())
        internal set
    var isBusy by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    var loaded by mutableStateOf(false)
        internal set

    val isConfigured: Boolean
        get() = settings.isConfigured

    val isDirty: Boolean
        get() = loaded && (
            draftApiKey != settings.apiKey ||
                draftWindowKind != settings.widgetWindowKind ||
                draftUsageDisplayMode != settings.usageDisplayMode ||
                draftUsageProgressStyle != settings.usageProgressStyle
            )

    fun applyDraft(next: OpenCodeGoSettings = settings) {
        draftApiKey = next.apiKey
        draftWindowKind = next.widgetWindowKind
        draftUsageDisplayMode = next.usageDisplayMode
        draftUsageProgressStyle = next.usageProgressStyle
    }

    fun applyLoaded(next: OpenCodeGoSettings) {
        settings = next
        applyDraft(next)
        loaded = true
    }

    fun summaryLabel(
        resources: Resources,
        widgetState: WidgetDisplayState,
        loadingMsg: String,
        reauthMsg: String,
    ): String? {
        if (!isConfigured) return null
        return when (widgetState) {
            is WidgetDisplayState.Success -> formatUsageWindowSummary(
                resources = resources,
                windows = widgetState.snapshot.windows,
                windowKind = settings.widgetWindowKind,
                usageDisplayMode = settings.usageDisplayMode,
                fallback = widgetState.snapshot.primaryDisplay,
            )
            WidgetDisplayState.Loading -> lastDisplay ?: loadingMsg
            is WidgetDisplayState.Error -> lastDisplay
            WidgetDisplayState.NeedsReauth -> reauthMsg
            WidgetDisplayState.NotConfigured -> null
        }
    }

    internal fun repository(): PlatformSettingsRepository = settingsRepository
}

data class OpenCodeGoHomeBindings(
    val widgetState: WidgetDisplayState,
    val windows: List<QuotaWindow>,
)

@Composable
fun rememberOpenCodeGoHomeState(
    settingsRepository: PlatformSettingsRepository,
): OpenCodeGoHomeState = remember(settingsRepository) {
    OpenCodeGoHomeState(settingsRepository)
}

/**
 * Widget observation and summary sync.
 * Must be called from HomeScreen (not only when the platform row is expanded).
 */
@Composable
fun OpenCodeGoHomeEffects(
    state: OpenCodeGoHomeState,
): OpenCodeGoHomeBindings {
    val resources = LocalResources.current
    val observed by state.repository().observeWidgetState(PlatformIds.OPENCODE_GO)
        .collectAsStateWithLifecycle(initialValue = WidgetDisplayState.NotConfigured)
    val windows = (observed as? WidgetDisplayState.Success)?.snapshot?.windows.orEmpty()

    LaunchedEffect(
        observed,
        state.settings.widgetWindowKind,
        state.settings.usageDisplayMode,
        resources,
    ) {
        val success = observed as? WidgetDisplayState.Success ?: return@LaunchedEffect
        state.lastDisplay = formatUsageWindowSummary(
            resources = resources,
            windows = success.snapshot.windows,
            windowKind = state.settings.widgetWindowKind,
            usageDisplayMode = state.settings.usageDisplayMode,
            fallback = success.snapshot.primaryDisplay,
        )
        state.lastWindows = success.snapshot.windows
    }

    return OpenCodeGoHomeBindings(
        widgetState = observed,
        windows = windows,
    )
}

@Composable
fun ColumnScope.OpenCodeGoHomeContent(
    state: OpenCodeGoHomeState,
    bindings: OpenCodeGoHomeBindings,
    onRefreshPlatform: suspend (String) -> WidgetDisplayState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val msgNeedsReauth = LocalResources.current.getString(R.string.widget_needs_reauth)

    fun saveAndRefresh(next: OpenCodeGoSettings, logTag: String) {
        scope.launch {
            state.isBusy = true
            state.error = null
            try {
                state.repository().saveOpenCodeGoSettings(next)
                state.settings = next
                state.applyDraft(next)
                if (!next.isConfigured) {
                    state.lastDisplay = null
                    state.lastWindows = emptyList()
                }
                when (val result = onRefreshPlatform(PlatformIds.OPENCODE_GO)) {
                    is WidgetDisplayState.Error -> {
                        state.error = result.message
                    }
                    WidgetDisplayState.NeedsReauth -> {
                        state.error = msgNeedsReauth
                    }
                    else -> {
                        state.error = null
                    }
                }
                WidgetGlanceState.syncAndUpdate(context, logTag)
            } finally {
                state.isBusy = false
            }
        }
    }

    OpenCodeGoConfigContent(
        apiKey = state.draftApiKey,
        onApiKeyChange = {
            state.draftApiKey = it
            state.error = null
        },
        isConfigured = state.settings.isConfigured,
        isBusy = state.isBusy,
        isDirty = state.isDirty,
        errorMessage = state.error,
        windows = bindings.windows,
        widgetWindowKind = state.draftWindowKind,
        onWidgetWindowKindChange = { kind ->
            state.draftWindowKind = kind
            state.error = null
        },
        usageDisplayMode = state.draftUsageDisplayMode,
        onUsageDisplayModeChange = { mode ->
            state.draftUsageDisplayMode = mode
            state.error = null
        },
        usageProgressStyle = state.draftUsageProgressStyle,
        onUsageProgressStyleChange = { style ->
            state.draftUsageProgressStyle = style
            state.error = null
        },
        onSave = {
            saveAndRefresh(
                OpenCodeGoSettings(
                    apiKey = state.draftApiKey.trim(),
                    widgetWindowKind = state.draftWindowKind,
                    usageDisplayMode = state.draftUsageDisplayMode,
                    usageProgressStyle = state.draftUsageProgressStyle,
                ),
                "opencode_save",
            )
        },
        onClear = {
            state.draftApiKey = ""
            saveAndRefresh(
                OpenCodeGoSettings(
                    widgetWindowKind = state.draftWindowKind,
                    usageDisplayMode = state.draftUsageDisplayMode,
                    usageProgressStyle = state.draftUsageProgressStyle,
                ),
                "opencode_clear",
            )
        },
    )
}
