package com.attiqtherehman.kharcha.feature.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.core.money.NumberGrouping
import com.attiqtherehman.kharcha.ui.common.KharchaTimePickerDialog
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import com.attiqtherehman.kharcha.ui.common.SectionHeader
import com.attiqtherehman.kharcha.ui.theme.KharchaTheme
import com.attiqtherehman.kharcha.ui.theme.ThemeMode
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAppLock: () -> Unit,
    onWidgets: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var pickTime by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    var pickCurrency by remember { mutableStateOf(false) }
    var pickWeekStart by remember { mutableStateOf(false) }
    val s = state.settings
    val st = state.reminderStatus

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshStatus() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshStatus() }

    fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && !st.notificationsAllowed && !s.notificationPermissionAsked) {
            // First time: the system permission dialog can still be shown.
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            viewModel.notificationPermissionAsked()
            return
        }
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Scaffold(topBar = { KharchaTopBar(title = stringResource(R.string.settings_title), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.settings_general))
            SettingRow(stringResource(R.string.settings_default_account), state.accounts.firstOrNull { it.id == s.defaultAccountId }?.name ?: "—") { pickAccount = true }
            SettingRow(stringResource(R.string.settings_base_currency), s.baseCurrency) { pickCurrency = true }
            SettingRow(stringResource(R.string.settings_number_grouping), stringResource(if (s.numberGrouping == NumberGrouping.SOUTH_ASIAN) R.string.settings_grouping_south_asian else R.string.settings_grouping_standard)) {
                viewModel.setNumberGrouping(if (s.numberGrouping == NumberGrouping.SOUTH_ASIAN) NumberGrouping.STANDARD else NumberGrouping.SOUTH_ASIAN)
            }
            SettingRow(stringResource(R.string.settings_week_start), s.weekStart.getDisplayName(TextStyle.FULL, Locale.getDefault())) { pickWeekStart = true }
            SettingRow(
                stringResource(R.string.settings_theme),
                stringResource(when (s.themeMode) { ThemeMode.SYSTEM -> R.string.settings_theme_system; ThemeMode.LIGHT -> R.string.settings_theme_light; ThemeMode.DARK -> R.string.settings_theme_dark }),
            ) { viewModel.setTheme(ThemeMode.entries[(s.themeMode.ordinal + 1) % ThemeMode.entries.size]) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(stringResource(R.string.settings_dynamic_color), s.dynamicColor) { viewModel.setDynamicColor(it) }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_reminders))
            SwitchRow(stringResource(R.string.settings_daily_reminder), s.dailyReminderEnabled) { viewModel.setDailyReminderEnabled(it) }
            SettingRow(stringResource(R.string.settings_daily_reminder_time), DateFormats.time(s.dailyReminderTime), enabled = s.dailyReminderEnabled) { pickTime = true }
            StatusRow(
                ok = st.notificationsAllowed,
                text = stringResource(if (st.notificationsAllowed) R.string.settings_notifications_allowed else R.string.settings_notifications_blocked),
                onClick = { openNotificationSettings() },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                StatusRow(
                    ok = st.exactAlarmsAllowed,
                    text = stringResource(if (st.exactAlarmsAllowed) R.string.settings_exact_alarms_allowed else R.string.settings_exact_alarms_denied),
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                )
            }
            StatusRow(
                ok = st.ignoringBatteryOptimizations,
                text = stringResource(if (st.ignoringBatteryOptimizations) R.string.settings_battery_unrestricted else R.string.settings_battery_optimised),
                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
            )
            Text(
                st.nextWake?.let { stringResource(R.string.settings_next_reminder, DateFormats.medium(it.atZone(ZoneId.systemDefault()).toLocalDate()) + " " + DateFormats.time(it.atZone(ZoneId.systemDefault()).toLocalTime())) }
                    ?: stringResource(R.string.settings_no_reminder_scheduled),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_budget_warnings))
            SwitchRow(stringResource(R.string.settings_budget_warn_at, 75), s.budgetWarn75) { viewModel.setBudgetWarnings(it, s.budgetWarn90, s.budgetWarn100) }
            SwitchRow(stringResource(R.string.settings_budget_warn_at, 90), s.budgetWarn90) { viewModel.setBudgetWarnings(s.budgetWarn75, it, s.budgetWarn100) }
            SwitchRow(stringResource(R.string.settings_budget_warn_at, 100), s.budgetWarn100) { viewModel.setBudgetWarnings(s.budgetWarn75, s.budgetWarn90, it) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_security))
            SettingRow(stringResource(R.string.settings_app_lock), stringResource(R.string.settings_app_lock_summary), onClick = onAppLock)
            SectionHeader(stringResource(R.string.settings_widgets))
            SettingRow(stringResource(R.string.settings_widget_presets), stringResource(R.string.settings_widget_presets_summary), onClick = onWidgets)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.settings_privacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
    }

    if (pickTime) KharchaTimePickerDialog(initial = s.dailyReminderTime, onPick = viewModel::setDailyReminderTime, onDismiss = { pickTime = false })
    if (pickAccount) {
        ChoiceDialog(title = stringResource(R.string.settings_default_account), options = state.accounts.map { it.id to it.name }, selected = s.defaultAccountId, onSelect = { viewModel.setDefaultAccount(it); pickAccount = false }, onDismiss = { pickAccount = false })
    }
    if (pickCurrency) {
        ChoiceDialog(title = stringResource(R.string.settings_base_currency), options = CurrencyInfo.all.map { it.code to "${it.code} · ${it.displayName}" }, selected = s.baseCurrency, onSelect = { viewModel.setBaseCurrency(it); pickCurrency = false }, onDismiss = { pickCurrency = false })
    }
    if (pickWeekStart) {
        ChoiceDialog(title = stringResource(R.string.settings_week_start), options = listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).map { it.name to it.getDisplayName(TextStyle.FULL, Locale.getDefault()) }, selected = s.weekStart.name, onSelect = { viewModel.setWeekStart(DayOfWeek.valueOf(it)); pickWeekStart = false }, onDismiss = { pickWeekStart = false })
    }
}

@Composable
fun SettingRow(title: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StatusRow(ok: Boolean, text: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = null, tint = if (ok) KharchaTheme.colors.income else KharchaTheme.colors.warning)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ChoiceDialog(title: String, options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (key, label) ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(key) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = key == selected, onClick = { onSelect(key) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
