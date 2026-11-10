package com.kuyermqi.quotawidget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind

@Composable
fun ColumnScope.OpenCodeGoConfigContent(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    isConfigured: Boolean,
    isBusy: Boolean,
    isDirty: Boolean,
    errorMessage: String?,
    windows: List<QuotaWindow>,
    widgetWindowKind: UsageWindowKind,
    onWidgetWindowKindChange: (UsageWindowKind) -> Unit,
    usageDisplayMode: UsageDisplayMode,
    onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.opencode_api_key_label)) },
        placeholder = { Text(stringResource(R.string.opencode_api_key_hint)) },
        supportingText = { Text(stringResource(R.string.opencode_api_key_help)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        enabled = !isBusy,
    )
    if (!errorMessage.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (isConfigured) {
        Spacer(modifier = Modifier.height(16.dp))
        UsageDisplayPrefsSection(
            windows = windows,
            widgetWindowKind = widgetWindowKind,
            onWidgetWindowKindChange = onWidgetWindowKindChange,
            usageDisplayMode = usageDisplayMode,
            onUsageDisplayModeChange = onUsageDisplayModeChange,
            usageProgressStyle = usageProgressStyle,
            onUsageProgressStyleChange = onUsageProgressStyleChange,
            enabled = !isBusy,
            windowKindChoices = listOf(
                UsageWindowKind.ROLLING,
                UsageWindowKind.WEEKLY,
                UsageWindowKind.MONTHLY,
            ),
            showWindowKindPicker = true,
            overviewKinds = listOf(
                QuotaWindowKind.FIVE_HOUR,
                QuotaWindowKind.WEEKLY,
                QuotaWindowKind.MONTHLY,
            ),
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        if (isConfigured) {
            TextButton(
                onClick = onClear,
                enabled = !isBusy,
            ) {
                Text(stringResource(R.string.opencode_clear))
            }
        }
        Button(
            onClick = onSave,
            enabled = isDirty && !isBusy,
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}
