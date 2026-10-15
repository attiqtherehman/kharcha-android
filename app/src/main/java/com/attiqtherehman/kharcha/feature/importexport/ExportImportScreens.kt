package com.attiqtherehman.kharcha.feature.importexport

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.core.datetime.DateRange
import com.attiqtherehman.kharcha.core.datetime.PeriodPreset
import com.attiqtherehman.kharcha.core.datetime.toRange
import com.attiqtherehman.kharcha.data.export.ExportBuilder
import com.attiqtherehman.kharcha.data.export.ExportDataset
import com.attiqtherehman.kharcha.data.export.TransactionCsvImporter
import com.attiqtherehman.kharcha.data.export.XlsxExporterSelector
import com.attiqtherehman.kharcha.di.IoDispatcher
import com.attiqtherehman.kharcha.feature.transactions.periodLabel
import com.attiqtherehman.kharcha.ui.common.KharchaDatePickerDialog
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import com.attiqtherehman.kharcha.ui.common.SectionHeader
import com.attiqtherehman.kharcha.ui.common.moneyFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import javax.inject.Inject

enum class ExportFormat { CSV, XLSX }

data class ExportState(
    val datasets: Set<ExportDataset> = setOf(ExportDataset.ALL_TRANSACTIONS),
    val format: ExportFormat = ExportFormat.CSV,
    val preset: PeriodPreset? = null,
    val custom: DateRange? = null,
    val working: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val builder: ExportBuilder,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(ExportState())
    val state = _state.asStateFlow()
    private val xlsx = XlsxExporterSelector()

    fun toggleDataset(d: ExportDataset) = _state.update { s ->
        val next = if (s.format == ExportFormat.CSV) setOf(d) else if (d in s.datasets) s.datasets - d else s.datasets + d
        s.copy(datasets = next.ifEmpty { setOf(d) })
    }
    fun setFormat(f: ExportFormat) = _state.update { it.copy(format = f, datasets = if (f == ExportFormat.CSV) setOf(it.datasets.first()) else it.datasets) }
    fun setPreset(p: PeriodPreset?) = _state.update { it.copy(preset = p) }
    fun setCustom(r: DateRange) = _state.update { it.copy(preset = PeriodPreset.CUSTOM, custom = r) }

    fun suggestedName(): String {
        val s = _state.value
        val base = if (s.datasets.size == 1) s.datasets.first().name.lowercase().replace('_', '-') else "kharcha-export"
        return "$base-${clock.today()}." + if (s.format == ExportFormat.CSV) "csv" else "xlsx"
    }

    fun mime(): String = if (_state.value.format == ExportFormat.CSV) "text/csv" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun export(uri: Uri) {
        val s = _state.value
        val range = when (s.preset) { null -> null; PeriodPreset.CUSTOM -> s.custom; else -> s.preset.toRange(clock.today()) }
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch(io) {
            try {
                val sheets = s.datasets.sortedBy { it.ordinal }.map { builder.build(it, range) }
                val out = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull() ?: context.contentResolver.openOutputStream(uri) ?: error("cannot write")
                out.use { if (s.format == ExportFormat.CSV) builder.writeCsv(it, sheets.first()) else xlsx.write(it, sheets) }
                _state.update { it.copy(working = false, message = context.getString(R.string.export_done)) }
            } catch (e: Exception) {
                _state.update { it.copy(working = false, message = e.message ?: e.toString()) }
            }
        }
    }
}

@Composable
fun datasetLabel(d: ExportDataset): String = stringResource(
    when (d) {
        ExportDataset.ALL_TRANSACTIONS -> R.string.export_all_transactions
        ExportDataset.INCOME -> R.string.export_income
        ExportDataset.EXPENSES -> R.string.export_expenses
        ExportDataset.TRANSFERS -> R.string.export_transfers
        ExportDataset.ACCOUNTS -> R.string.export_accounts
        ExportDataset.BUDGETS -> R.string.export_budgets
        ExportDataset.GOALS -> R.string.export_goals
        ExportDataset.REMINDERS -> R.string.export_reminders
        ExportDataset.PAYMENTS -> R.string.export_payments
    }
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(onBack: () -> Unit, viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(viewModel.mime())) { uri -> uri?.let(viewModel::export) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf(java.time.LocalDate.now().withDayOfMonth(1)) }
    Scaffold(
        topBar = { KharchaTopBar(title = stringResource(R.string.export_title), onBack = onBack) },
        bottomBar = {
            Button(onClick = { launcher.launch(viewModel.suggestedName()) }, enabled = !state.working, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) { Text(stringResource(R.string.nav_export)) }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(stringResource(R.string.export_format))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.format == ExportFormat.CSV, onClick = { viewModel.setFormat(ExportFormat.CSV) }, label = { Text("CSV") })
                FilterChip(selected = state.format == ExportFormat.XLSX, onClick = { viewModel.setFormat(ExportFormat.XLSX) }, label = { Text("Excel (.xlsx)") })
            }
            SectionHeader(stringResource(R.string.export_what))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportDataset.entries.forEach { d -> FilterChip(selected = d in state.datasets, onClick = { viewModel.toggleDataset(d) }, label = { Text(datasetLabel(d)) }) }
            }
            if (state.format == ExportFormat.XLSX) Text(stringResource(R.string.export_everything_xlsx), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionHeader(stringResource(R.string.export_range))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null, PeriodPreset.THIS_MONTH, PeriodPreset.LAST_MONTH, PeriodPreset.THIS_YEAR, PeriodPreset.CUSTOM).forEach { p ->
                    val label = if (p == PeriodPreset.CUSTOM && state.custom != null && state.preset == PeriodPreset.CUSTOM) "${DateFormats.medium(state.custom!!.start)} – ${DateFormats.medium(state.custom!!.endInclusive)}" else periodLabel(p)
                    FilterChip(selected = state.preset == p, onClick = { if (p == PeriodPreset.CUSTOM) pickFrom = true else viewModel.setPreset(p) }, label = { Text(label) })
                }
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text(stringResource(R.string.reports_transfers_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (pickFrom) KharchaDatePickerDialog(initial = from, onPick = { from = it; pickTo = true }, onDismiss = { pickFrom = false })
    if (pickTo) KharchaDatePickerDialog(initial = maxOf(from, java.time.LocalDate.now()), onPick = { to -> viewModel.setCustom(DateRange(from, if (to.isBefore(from)) from else to)) }, onDismiss = { pickTo = false })
}

data class ImportState(
    val fileName: String? = null,
    val uri: Uri? = null,
    val createMissing: Boolean = true,
    val preview: TransactionCsvImporter.Preview? = null,
    val working: Boolean = false,
    val result: TransactionCsvImporter.Result? = null,
    val error: String? = null,
)

@HiltViewModel
class ImportCsvViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: TransactionCsvImporter,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(ImportState())
    val state = _state.asStateFlow()

    fun pick(uri: Uri) {
        _state.update { it.copy(uri = uri, fileName = uri.lastPathSegment, result = null, error = null) }
        parse()
    }

    fun setCreateMissing(v: Boolean) { _state.update { it.copy(createMissing = v) }; parse() }

    private fun parse() {
        val s = _state.value
        val uri = s.uri ?: return
        _state.update { it.copy(working = true, preview = null, error = null) }
        viewModelScope.launch(io) {
            try {
                val preview = context.contentResolver.openInputStream(uri)?.use { importer.preview(InputStreamReader(it, Charsets.UTF_8), s.createMissing) } ?: error("cannot open file")
                _state.update { it.copy(working = false, preview = preview, error = preview.headerErrors.firstOrNull()) }
            } catch (e: Exception) {
                _state.update { it.copy(working = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun import() {
        val preview = _state.value.preview ?: return
        _state.update { it.copy(working = true) }
        viewModelScope.launch(io) {
            try {
                val result = importer.import(preview)
                _state.update { it.copy(working = false, result = result, preview = null, uri = null) }
            } catch (e: Exception) {
                _state.update { it.copy(working = false, error = e.message ?: e.toString()) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ImportCsvScreen(onBack: () -> Unit, viewModel: ImportCsvViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::pick) }
    val fmt = moneyFormat()
    Scaffold(
        topBar = { KharchaTopBar(title = stringResource(R.string.import_title), onBack = onBack) },
        bottomBar = {
            val preview = state.preview
            if (preview != null && preview.validCount > 0) {
                Button(onClick = viewModel::import, enabled = !state.working, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) { Text(stringResource(R.string.import_confirm, preview.validCount)) }
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(stringResource(R.string.import_format_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { launcher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv", "*/*")) }, enabled = !state.working) { Text(stringResource(R.string.import_pick_file)) }
                state.fileName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Switch(checked = state.createMissing, onCheckedChange = viewModel::setCreateMissing)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_create_missing), style = MaterialTheme.typography.bodyMedium)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.result?.let { Text(stringResource(R.string.import_done, it.imported) + " · skipped ${it.skippedExisting} · new accounts ${it.createdAccounts} · new categories ${it.createdCategories}", color = MaterialTheme.colorScheme.primary) }
            }
            state.preview?.let { p ->
                item { SectionHeader(stringResource(R.string.import_preview)); Text(stringResource(R.string.import_rows_valid, p.validCount, p.errorCount), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp)) }
                items(p.rows.take(200)) { row ->
                    val t = row.transaction
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        if (t != null) {
                            Text("${t.date} · ${t.type.name.lowercase()} · ${fmt.format(t.amount)} · ${t.payee ?: t.description ?: ""}", style = MaterialTheme.typography.bodyMedium)
                            listOfNotNull(row.newAccount?.let { "new account: $it" }, row.newToAccount?.let { "new account: $it" }, row.newCategory?.let { "new category: $it" }).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                        } else {
                            Text("Line ${row.line}: ${row.errors.joinToString("; ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
