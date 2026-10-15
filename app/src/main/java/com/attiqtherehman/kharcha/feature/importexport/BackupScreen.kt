package com.attiqtherehman.kharcha.feature.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.data.backup.MergePolicy
import com.attiqtherehman.kharcha.data.backup.RestoreMode
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.lastBackupAt.collectAsStateWithLifecycle(initialValue = null)
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(viewModel::createBackup) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::validate) }
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }
    var policy by remember { mutableStateOf(MergePolicy.SKIP) }
    var confirmText by remember { mutableStateOf("") }

    Scaffold(topBar = { KharchaTopBar(title = stringResource(R.string.backup_title), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.backup_sensitive_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.backup_create), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.backup_create_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        settings?.lastBackupAt?.let { stringResource(R.string.settings_last_backup, DateFormats.medium(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())) } ?: stringResource(R.string.settings_no_backup_yet),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { create.launch(viewModel.suggestedFileName()) }, enabled = state !is BackupUiState.Working) { Text(stringResource(R.string.backup_create)) }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.backup_restore), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.backup_restore_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = state !is BackupUiState.Working) { Text(stringResource(R.string.backup_restore)) }
                }
            }
            when (val s = state) {
                is BackupUiState.Working -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(when (s.label) { "validating" -> R.string.backup_validating; "restoring" -> R.string.backup_restoring; else -> R.string.backup_writing }))
                    }
                    if (s.total > 0) LinearProgressIndicator(progress = { s.done.toFloat() / s.total }, modifier = Modifier.fillMaxWidth())
                }
                is BackupUiState.Done -> Text(stringResource(R.string.backup_done), color = MaterialTheme.colorScheme.primary)
                is BackupUiState.Failed -> Text(if (s.newerVersion) stringResource(R.string.backup_newer_version) else stringResource(R.string.backup_invalid, s.message), color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
        }
    }

    (state as? BackupUiState.Validated)?.let { v ->
        val c = v.contents
        AlertDialog(
            onDismissRequest = viewModel::reset,
            title = { Text(stringResource(R.string.backup_summary_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.backup_created_at, DateFormats.medium(Instant.ofEpochMilli(c.manifest.createdAt).atZone(ZoneId.systemDefault()).toLocalDate())) + " · v${c.manifest.appVersionName}", style = MaterialTheme.typography.bodySmall)
                    c.counts().filter { it.value > 0 }.forEach { (table, n) -> Text("${table.replace('_', ' ')}: $n", style = MaterialTheme.typography.bodyMedium) }
                    if (c.attachmentNames.isNotEmpty()) Text("attachments: ${c.attachmentNames.size}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    ModeOption(stringResource(R.string.backup_mode_merge), stringResource(R.string.backup_mode_merge_summary), mode == RestoreMode.MERGE) { mode = RestoreMode.MERGE }
                    if (mode == RestoreMode.MERGE) {
                        Text(stringResource(R.string.backup_merge_policy), style = MaterialTheme.typography.labelLarge)
                        ModeOption(stringResource(R.string.backup_merge_skip), null, policy == MergePolicy.SKIP) { policy = MergePolicy.SKIP }
                        ModeOption(stringResource(R.string.backup_merge_newer), null, policy == MergePolicy.UPDATE_IF_NEWER) { policy = MergePolicy.UPDATE_IF_NEWER }
                        ModeOption(stringResource(R.string.backup_merge_overwrite), null, policy == MergePolicy.OVERWRITE) { policy = MergePolicy.OVERWRITE }
                    }
                    ModeOption(stringResource(R.string.backup_mode_replace), stringResource(R.string.backup_mode_replace_summary), mode == RestoreMode.REPLACE) { mode = RestoreMode.REPLACE }
                    if (mode == RestoreMode.REPLACE) {
                        Text(stringResource(R.string.backup_replace_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        OutlinedTextField(value = confirmText, onValueChange = { confirmText = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.restore(mode, policy) }, enabled = mode == RestoreMode.MERGE || confirmText.trim() == "REPLACE") {
                    Text(stringResource(R.string.backup_restore), color = if (mode == RestoreMode.REPLACE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::reset) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    (state as? BackupUiState.Restored)?.let { r ->
        AlertDialog(
            onDismissRequest = viewModel::reset,
            title = { Text(stringResource(R.string.backup_result_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    r.report.tables.filter { (_, t) -> t.inserted + t.updated + t.skipped + t.invalid.size > 0 }.forEach { (name, t) ->
                        Text(stringResource(R.string.backup_result_line, name.replace('_', ' '), t.inserted, t.updated, t.skipped, t.invalid.size), style = MaterialTheme.typography.bodyMedium)
                    }
                    val invalid = r.report.tables.values.flatMap { it.invalid }
                    if (invalid.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.backup_invalid_rows), style = MaterialTheme.typography.labelLarge)
                        invalid.take(20).forEach { Text("${it.table} ${it.id.take(8)}…: ${it.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::reset) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun ModeOption(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
