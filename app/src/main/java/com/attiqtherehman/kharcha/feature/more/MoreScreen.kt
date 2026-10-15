package com.attiqtherehman.kharcha.feature.more

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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onSavings: () -> Unit,
    onReminders: () -> Unit,
    onRecurring: () -> Unit,
    onMissedDays: () -> Unit,
    onReports: () -> Unit,
    onCategories: () -> Unit,
    onSettings: () -> Unit,
    onWidgets: () -> Unit,
    onBackup: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onAbout: () -> Unit,
) {
    Scaffold(topBar = { KharchaTopBar(title = stringResource(R.string.nav_more)) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            MoreItem(Icons.Filled.Savings, stringResource(R.string.nav_savings), onSavings)
            MoreItem(Icons.Filled.NotificationsActive, stringResource(R.string.nav_reminders), onReminders)
            MoreItem(Icons.Filled.Repeat, stringResource(R.string.nav_recurring), onRecurring)
            MoreItem(Icons.Filled.EventAvailable, stringResource(R.string.missed_days_title), onMissedDays)
            MoreItem(Icons.Filled.BarChart, stringResource(R.string.nav_reports), onReports)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            MoreItem(Icons.AutoMirrored.Filled.Label, stringResource(R.string.nav_categories), onCategories)
            MoreItem(Icons.Filled.Widgets, stringResource(R.string.settings_widgets), onWidgets)
            MoreItem(Icons.Filled.Settings, stringResource(R.string.nav_settings), onSettings)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            MoreItem(Icons.Filled.Backup, stringResource(R.string.nav_backup), onBackup)
            MoreItem(Icons.Filled.FileDownload, stringResource(R.string.nav_export), onExport)
            MoreItem(Icons.Filled.FileUpload, stringResource(R.string.nav_import), onImport)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            MoreItem(Icons.Filled.Info, stringResource(R.string.nav_about), onAbout)
        }
    }
}

@Composable
private fun MoreItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
