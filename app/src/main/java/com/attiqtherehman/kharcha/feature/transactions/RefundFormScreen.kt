package com.attiqtherehman.kharcha.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.common.Ids
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.MoneyFormatter
import com.attiqtherehman.kharcha.core.money.MoneyParseResult
import com.attiqtherehman.kharcha.core.money.MoneyParser
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.Transaction
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.repository.TransactionRepository
import com.attiqtherehman.kharcha.domain.usecase.RecordRefundUseCase
import com.attiqtherehman.kharcha.domain.usecase.RefundExceedsOriginalException
import com.attiqtherehman.kharcha.ui.common.AccountPickerSheet
import com.attiqtherehman.kharcha.ui.common.KharchaDatePickerDialog
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import com.attiqtherehman.kharcha.ui.common.LoadingState
import com.attiqtherehman.kharcha.ui.navigation.RefundFormRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class RefundFormState(
    val loading: Boolean = true,
    val original: Transaction? = null,
    val remaining: Money = Money.ZERO,
    val amountText: String = "",
    val accounts: List<AccountBalance> = emptyList(),
    val account: AccountBalance? = null,
    val date: LocalDate = LocalDate.now(),
    val description: String = "",
    val amountError: String? = null,
    val saving: Boolean = false,
) {
    val currency: CurrencyInfo get() = CurrencyInfo.of(account?.account?.currencyCode ?: "PKR")
}

@HiltViewModel
class RefundFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val recordRefund: RecordRefundUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val originalId = savedStateHandle.toRoute<RefundFormRoute>().originalTransactionId
    private val _state = MutableStateFlow(RefundFormState(date = clock.today()))
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<TransactionFormEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val original = transactions.get(originalId)
            if (original == null || original.type != TransactionType.EXPENSE) {
                _events.emit(TransactionFormEvent.Error("Original expense not found")); return@launch
            }
            val balances = accounts.getBalances().filter { !it.account.isArchived }
            val account = balances.firstOrNull { it.account.id == original.accountId } ?: balances.firstOrNull()
            val remaining = original.amount - transactions.sumRefundsLinkedTo(originalId)
            val currency = CurrencyInfo.of(account?.account?.currencyCode ?: "PKR")
            _state.update {
                it.copy(
                    loading = false, original = original, remaining = remaining, accounts = balances, account = account,
                    amountText = MoneyFormatter.toPlainDecimal(remaining, currency.minorDigits),
                    description = original.payee?.let { p -> "Refund: $p" } ?: "Refund",
                )
            }
        }
    }

    fun setAmount(v: String) = _state.update { it.copy(amountText = v, amountError = null) }
    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setDate(d: LocalDate) = _state.update { it.copy(date = d) }
    fun selectAccount(a: AccountBalance) = _state.update { it.copy(account = a) }

    fun save() {
        val s = _state.value
        val original = s.original ?: return
        val account = s.account ?: return
        val amount = when (val r = MoneyParser.parse(s.amountText, s.currency)) {
            is MoneyParseResult.Success -> r.money
            is MoneyParseResult.Failure -> { _state.update { it.copy(amountError = "Enter a valid amount") }; return }
        }
        if (!amount.isPositive) { _state.update { it.copy(amountError = "Enter an amount greater than zero") }; return }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val now = clock.nowMillis()
                val refund = Transaction(
                    id = Ids.newId(), type = TransactionType.REFUND, amount = amount, accountId = account.account.id,
                    categoryId = original.categoryId, date = s.date, time = clock.nowTime(), payee = original.payee,
                    description = s.description.trim().ifEmpty { null }, linkedTransactionId = original.id,
                    createdAt = now, updatedAt = now,
                )
                recordRefund(refund)
                _events.emit(TransactionFormEvent.Saved(refund.id))
            } catch (e: RefundExceedsOriginalException) {
                _state.update { it.copy(saving = false, amountError = "Refund cannot exceed the remaining ${MoneyFormatter.format(e.remaining, s.currency)}") }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _events.emit(TransactionFormEvent.Error(e.message ?: e.toString()))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefundFormScreen(onBack: () -> Unit, onSaved: (String) -> Unit, viewModel: RefundFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val genericError = stringResource(R.string.error_generic)
    var pickAccount by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is TransactionFormEvent.Saved -> onSaved(e.id)
                is TransactionFormEvent.Error -> snackbar.showSnackbar(genericError.format(e.message))
            }
        }
    }

    Scaffold(
        topBar = { KharchaTopBar(title = stringResource(R.string.transaction_record_refund), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Button(onClick = viewModel::save, enabled = !state.loading && !state.saving, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) {
                Text(stringResource(R.string.action_save))
            }
        },
    ) { padding ->
        if (state.loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.transaction_refund_of, MoneyFormatter.format(state.original!!.amount, state.currency)) +
                    " · " + "remaining " + MoneyFormatter.format(state.remaining, state.currency),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.amountText, onValueChange = viewModel::setAmount,
                label = { Text(stringResource(R.string.transaction_amount)) }, prefix = { Text(state.currency.symbol + " ") },
                isError = state.amountError != null, supportingText = state.amountError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            PickerField(label = stringResource(R.string.transaction_account), value = state.account?.account?.name ?: "", onClick = { pickAccount = true })
            PickerField(label = stringResource(R.string.transaction_date), value = DateFormats.medium(state.date), onClick = { pickDate = true })
            OutlinedTextField(value = state.description, onValueChange = viewModel::setDescription, label = { Text(stringResource(R.string.transaction_description)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
    if (pickAccount) AccountPickerSheet(accounts = state.accounts, selectedId = state.account?.account?.id, onSelect = { viewModel.selectAccount(it); pickAccount = false }, onDismiss = { pickAccount = false })
    if (pickDate) KharchaDatePickerDialog(initial = state.date, onPick = viewModel::setDate, onDismiss = { pickDate = false })
}
