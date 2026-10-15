package com.attiqtherehman.kharcha.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.attiqtherehman.kharcha.BuildConfig
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.core.datetime.DateRange
import com.attiqtherehman.kharcha.domain.repository.DayReviewRepository
import com.attiqtherehman.kharcha.domain.repository.TransactionRepository
import com.attiqtherehman.kharcha.ui.common.EmptyState
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import com.attiqtherehman.kharcha.ui.common.LoadingState
import com.attiqtherehman.kharcha.ui.common.SectionHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private const val LOOKBACK_DAYS = 30

@HiltViewModel
class MissedDaysViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val dayReviews: DayReviewRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _missed = MutableStateFlow<List<LocalDate>?>(null)
    val missed: StateFlow<List<LocalDate>?> = _missed.asStateFlow()
    val today: LocalDate get() = clock.today()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val range = DateRange.lastNDays(clock.today(), LOOKBACK_DAYS)
        val withTx = transactions.datesWithTransactions(range)
        val reviewed = dayReviews.reviewedDates(range)
        _missed.value = (0 until LOOKBACK_DAYS).map { range.endInclusive.minusDays(it.toLong()) }
            .filter { it !in withTx && it !in reviewed }
    }

    fun markReviewed(date: LocalDate) = viewModelScope.launch { dayReviews.markReviewed(date); refresh() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissedDaysScreen(onBack: () -> Unit, onAddExpense: (LocalDate) -> Unit, viewModel: MissedDaysViewModel = hiltViewModel()) {
    val missed by viewModel.missed.collectAsStateWithLifecycle()
    Scaffold(topBar = { KharchaTopBar(title = stringResource(R.string.missed_days_title), onBack = onBack) }) { padding ->
        val list = missed
        when {
            list == null -> LoadingState(Modifier.padding(padding))
            list.isEmpty() -> EmptyState(icon = Icons.Filled.EventAvailable, title = stringResource(R.string.missed_days_none), modifier = Modifier.padding(padding))
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
                item { Text(stringResource(R.string.missed_days_message, LOOKBACK_DAYS), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                items(list, key = { it.toString() }) { date ->
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(DateFormats.dayHeader(date, viewModel.today), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onAddExpense(date) }) { Text(stringResource(R.string.missed_days_add_expense)) }
                        TextButton(onClick = { viewModel.markReviewed(date) }) { Text(stringResource(R.string.missed_days_mark_reviewed)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(topBar = { KharchaTopBar(title = stringResource(R.string.about_title), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(R.string.about_privacy_title))
            Text(stringResource(R.string.settings_privacy_note), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(R.string.about_limitations_title))
            Text(stringResource(R.string.about_limitations), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.reports_transfers_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}
