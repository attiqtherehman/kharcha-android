package com.attiqtherehman.kharcha.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.attiqtherehman.kharcha.core.common.Ids
import com.attiqtherehman.kharcha.core.common.LedgerViolationException
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.MoneyFormatter
import com.attiqtherehman.kharcha.core.money.MoneyParseResult
import com.attiqtherehman.kharcha.core.money.MoneyParser
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.domain.ledger.LedgerRules
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.model.CategoryKind
import com.attiqtherehman.kharcha.domain.model.Transaction
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.repository.CategoryRepository
import com.attiqtherehman.kharcha.domain.repository.TransactionRepository
import com.attiqtherehman.kharcha.domain.usecase.RecordTransactionUseCase
import com.attiqtherehman.kharcha.domain.usecase.RecordTransferUseCase
import com.attiqtherehman.kharcha.domain.usecase.RefundExceedsOriginalException
import com.attiqtherehman.kharcha.domain.usecase.UpdateTransactionUseCase
import com.attiqtherehman.kharcha.ui.navigation.TransactionFormRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class TransactionFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val accounts: List<AccountBalance> = emptyList(),
    val account: AccountBalance? = null,
    val toAccount: AccountBalance? = null,
    val categories: List<Category> = emptyList(),
    val category: Category? = null,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now(),
    val payee: String = "",
    val description: String = "",
    val notes: String = "",
    val tags: String = "",
    val feeText: String = "",
    val payeeSuggestions: List<String> = emptyList(),
    val amountError: String? = null,
    val feeError: String? = null,
    val formError: String? = null,
    val saving: Boolean = false,
) {
    val currency: CurrencyInfo get() = CurrencyInfo.of(account?.account?.currencyCode ?: "PKR")
}

sealed interface TransactionFormEvent {
    data class Saved(val id: String) : TransactionFormEvent
    data class Error(val message: String) : TransactionFormEvent
}

@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val settings: SettingsDataStore,
    private val recordTransaction: RecordTransactionUseCase,
    private val recordTransfer: RecordTransferUseCase,
    private val updateTransaction: UpdateTransactionUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<TransactionFormRoute>()
    private var existing: Transaction? = null

    private val _state = MutableStateFlow(TransactionFormState(type = runCatching { TransactionType.valueOf(route.type) }.getOrDefault(TransactionType.EXPENSE), date = clock.today(), time = clock.nowTime()))
    val state: StateFlow<TransactionFormState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TransactionFormEvent>()
    val events: SharedFlow<TransactionFormEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val prefs = settings.current()
        val balances = accounts.getBalances().filter { !it.account.isArchived }
        val source = route.transactionId ?: route.duplicateOf
        val sourceTx = source?.let { transactions.get(it) }
        if (route.transactionId != null && sourceTx == null) {
            _events.emit(TransactionFormEvent.Error("Transaction not found"))
            return
        }
        existing = if (route.transactionId != null) sourceTx else null
        val type = sourceTx?.type ?: _state.value.type
        val catList = loadCategoriesFor(type)
        val defaultAccountId = sourceTx?.accountId ?: prefs.lastUsedAccountId ?: prefs.defaultAccountId
        val account = balances.firstOrNull { it.account.id == defaultAccountId } ?: balances.firstOrNull()
        val currency = CurrencyInfo.of(account?.account?.currencyCode ?: prefs.baseCurrency)
        _state.update {
            it.copy(
                loading = false,
                isEdit = existing != null,
                type = type,
                accounts = balances,
                account = account,
                toAccount = sourceTx?.toAccountId?.let { id -> balances.firstOrNull { b -> b.account.id == id } },
                categories = catList,
                category = sourceTx?.categoryId?.let { id -> catList.firstOrNull { c -> c.id == id } },
                amountText = sourceTx?.let { s -> MoneyFormatter.toPlainDecimal(if (s.type == TransactionType.ADJUSTMENT) s.amount else s.amount.abs(), currency.minorDigits) } ?: "",
                date = if (existing != null) sourceTx!!.date else clock.today(),
                time = if (existing != null) sourceTx!!.time else clock.nowTime(),
                payee = sourceTx?.payee ?: "",
                description = sourceTx?.description ?: "",
                notes = sourceTx?.notes ?: "",
                tags = sourceTx?.tags?.joinToString(", ") ?: "",
                payeeSuggestions = transactions.recentPayees(type.takeIf { t -> t != TransactionType.TRANSFER }, 8),
            )
        }
    }

    private suspend fun loadCategoriesFor(type: TransactionType): List<Category> = when (type) {
        TransactionType.INCOME -> categories.observeActive(CategoryKind.INCOME).first()
        TransactionType.EXPENSE, TransactionType.REFUND -> categories.observeActive(CategoryKind.EXPENSE).first()
        else -> emptyList()
    }

    fun setType(type: TransactionType) {
        if (_state.value.isEdit || type == _state.value.type) return
        _state.update { it.copy(type = type, category = null, toAccount = null, feeText = "", formError = null) }
        viewModelScope.launch { _state.update { it.copy(categories = loadCategoriesFor(type)) } }
    }

    fun setAmount(v: String) = _state.update { it.copy(amountText = v, amountError = null) }
    fun setFee(v: String) = _state.update { it.copy(feeText = v, feeError = null) }
    fun selectAccount(a: AccountBalance) = _state.update { it.copy(account = a, formError = null) }
    fun selectToAccount(a: AccountBalance) = _state.update { it.copy(toAccount = a, formError = null) }
    fun selectCategory(c: Category?) = _state.update { it.copy(category = c) }
    fun setDate(d: LocalDate) = _state.update { it.copy(date = d) }
    fun setTime(t: LocalTime) = _state.update { it.copy(time = t) }
    fun setPayee(v: String) = _state.update { it.copy(payee = v) }
    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }
    fun setTags(v: String) = _state.update { it.copy(tags = v) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val currency = s.currency
        val amount = when (val r = MoneyParser.parse(s.amountText, currency)) {
            is MoneyParseResult.Success -> r.money
            is MoneyParseResult.Failure -> { _state.update { it.copy(amountError = parseError(r.reason)) }; return }
        }
        if (s.type != TransactionType.ADJUSTMENT && !amount.isPositive) {
            _state.update { it.copy(amountError = "Enter an amount greater than zero") }; return
        }
        if (s.type == TransactionType.ADJUSTMENT && amount.isZero) {
            _state.update { it.copy(amountError = "Adjustment cannot be zero") }; return
        }
        val account = s.account ?: run { _state.update { it.copy(formError = "Choose an account") }; return }
        val fee: Money? = if (s.type == TransactionType.TRANSFER && s.feeText.isNotBlank()) {
            when (val r = MoneyParser.parse(s.feeText, currency)) {
                is MoneyParseResult.Success -> r.money
                is MoneyParseResult.Failure -> { _state.update { it.copy(feeError = parseError(r.reason)) }; return }
            }
        } else null

        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            try {
                val now = clock.nowMillis()
                val base = existing
                val tx = Transaction(
                    id = base?.id ?: Ids.newId(),
                    type = s.type,
                    amount = amount,
                    accountId = account.account.id,
                    toAccountId = if (s.type == TransactionType.TRANSFER) s.toAccount?.account?.id else null,
                    categoryId = if (s.type.usesCategory) s.category?.id else null,
                    date = s.date,
                    time = s.time,
                    payee = s.payee.trim().ifEmpty { null },
                    description = s.description.trim().ifEmpty { null },
                    notes = s.notes.trim().ifEmpty { null },
                    tags = s.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    linkedTransactionId = base?.linkedTransactionId,
                    recurringTransactionId = base?.recurringTransactionId,
                    occurrenceKey = base?.occurrenceKey,
                    attachmentFileName = base?.attachmentFileName,
                    createdAt = base?.createdAt ?: now,
                    updatedAt = now,
                )
                if (base != null) {
                    updateTransaction(tx)
                } else {
                    when (s.type) {
                        TransactionType.TRANSFER -> recordTransfer(tx, fee)
                        TransactionType.INCOME, TransactionType.EXPENSE -> recordTransaction(tx)
                        else -> transactions.insertAll(listOf(tx))
                    }
                }
                _events.emit(TransactionFormEvent.Saved(tx.id))
            } catch (e: LedgerViolationException) {
                _state.update { it.copy(saving = false, formError = violationMessage(e)) }
            } catch (e: RefundExceedsOriginalException) {
                _state.update { it.copy(saving = false, amountError = "Refund cannot exceed the remaining ${MoneyFormatter.format(e.remaining, currency)}") }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _events.emit(TransactionFormEvent.Error(e.message ?: e.toString()))
            }
        }
    }

    private fun violationMessage(e: LedgerViolationException): String {
        val first = e.violations.firstOrNull() as? LedgerRules.Violation
        return when (first) {
            LedgerRules.Violation.TO_ACCOUNT_REQUIRED -> "Choose the destination account"
            LedgerRules.Violation.SAME_ACCOUNT -> "Choose two different accounts"
            LedgerRules.Violation.CURRENCY_MISMATCH -> "Both accounts must use the same currency"
            LedgerRules.Violation.ARCHIVED_ACCOUNT -> "That account is archived"
            LedgerRules.Violation.AMOUNT_NOT_POSITIVE -> "Enter an amount greater than zero"
            else -> "Invalid transaction: ${first ?: e.violations}"
        }
    }

    private fun parseError(reason: MoneyParseResult.Reason): String = when (reason) {
        MoneyParseResult.Reason.EMPTY -> "Enter an amount"
        MoneyParseResult.Reason.INVALID -> "Not a valid amount"
        MoneyParseResult.Reason.TOO_MANY_DECIMALS -> "Too many decimal places"
        MoneyParseResult.Reason.TOO_LARGE -> "Amount is too large"
    }
}
