package com.attiqtherehman.kharcha.feature.quickentry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attiqtherehman.kharcha.core.common.Ids
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.NumberGrouping
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.model.CategoryKind
import com.attiqtherehman.kharcha.domain.model.Transaction
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.repository.CategoryRepository
import com.attiqtherehman.kharcha.domain.repository.TransactionRepository
import com.attiqtherehman.kharcha.domain.usecase.RecordTransactionUseCase
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

data class QuickEntryState(
    val loading: Boolean = true,
    val type: TransactionType = TransactionType.EXPENSE,
    val favorites: List<Category> = emptyList(),
    val recent: List<Category> = emptyList(),
    val allCategories: List<Category> = emptyList(),
    val accounts: List<AccountBalance> = emptyList(),
    val selectedCategory: Category? = null,
    val selectedAccount: AccountBalance? = null,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now(),
    val payee: String = "",
    val description: String = "",
    val notes: String = "",
    val tags: String = "",
    val showMore: Boolean = false,
    val payeeSuggestions: List<String> = emptyList(),
    val currency: CurrencyInfo = CurrencyInfo.PKR,
    val grouping: NumberGrouping = NumberGrouping.STANDARD,
    val initialAmount: Money? = null,
    val saving: Boolean = false,
    val amountError: Boolean = false,
) {
    /** Favourites first, then recent ones not already shown. */
    val quickCategories: List<Category>
        get() = (favorites + recent.filter { r -> favorites.none { it.id == r.id } }).distinctBy { it.id }
}

sealed interface QuickEntryEvent {
    data class Saved(val addAnother: Boolean) : QuickEntryEvent
    data class Error(val message: String) : QuickEntryEvent
}

/**
 * Backs the quick-entry screen in both hosts (nav destination and [QuickEntryActivity]).
 * Preselection arrives through SavedStateHandle: nav route args or Intent extras with the same keys.
 */
@HiltViewModel
class QuickEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val settings: SettingsDataStore,
    private val record: RecordTransactionUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val presetCategoryId: String? = savedStateHandle.get<String>(ARG_CATEGORY_ID)
    private val presetAmountMinor: Long? = savedStateHandle.get<Long>(ARG_AMOUNT_MINOR)?.takeIf { it > 0 }
    private val presetAccountId: String? = savedStateHandle.get<String>(ARG_ACCOUNT_ID)
    private val presetDate: LocalDate? = savedStateHandle.get<String>(ARG_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private val _state = MutableStateFlow(QuickEntryState(date = presetDate ?: clock.today(), time = if (presetDate != null && presetDate != clock.today()) LocalTime.of(12, 0) else clock.nowTime()))
    val state: StateFlow<QuickEntryState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<QuickEntryEvent>()
    val events: SharedFlow<QuickEntryEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val prefs = settings.current()
        val balances = accounts.getBalances().filter { !it.account.isArchived }
        val preferredId = presetAccountId ?: prefs.lastUsedAccountId ?: prefs.defaultAccountId
        val account = balances.firstOrNull { it.account.id == preferredId }
            ?: balances.firstOrNull { it.account.type.isSpendable }
            ?: balances.firstOrNull()
        val currency = CurrencyInfo.of(account?.account?.currencyCode ?: prefs.baseCurrency)
        _state.update {
            it.copy(
                accounts = balances, selectedAccount = account, currency = currency, grouping = prefs.numberGrouping,
                initialAmount = presetAmountMinor?.let(::Money),
            )
        }
        loadCategories(_state.value.type, presetCategoryId)
        _state.update { it.copy(loading = false, payeeSuggestions = transactions.recentPayees(TransactionType.EXPENSE, 8)) }
    }

    private suspend fun loadCategories(type: TransactionType, preselectId: String?) {
        val kind = if (type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        val favorites = categories.favorites(kind, 8)
        val recent = categories.recentlyUsed(type, 6)
        val all = categories.observeActive(kind).first()
        val preselected = preselectId?.let { id -> all.firstOrNull { it.id == id } }
        _state.update { it.copy(favorites = favorites, recent = recent, allCategories = all, selectedCategory = preselected) }
    }

    fun setType(type: TransactionType) {
        if (type == _state.value.type) return
        _state.update { it.copy(type = type, selectedCategory = null) }
        viewModelScope.launch { loadCategories(type, null) }
    }

    fun selectCategory(category: Category?) = _state.update { it.copy(selectedCategory = category) }

    fun selectAccount(account: AccountBalance) = _state.update {
        it.copy(selectedAccount = account, currency = CurrencyInfo.of(account.account.currencyCode))
    }

    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }
    fun setTime(time: LocalTime) = _state.update { it.copy(time = time) }
    fun setPayee(v: String) = _state.update { it.copy(payee = v) }
    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }
    fun setTags(v: String) = _state.update { it.copy(tags = v) }
    fun toggleMore() = _state.update { it.copy(showMore = !it.showMore) }
    fun clearAmountError() = _state.update { it.copy(amountError = false) }

    fun save(amount: Money?, addAnother: Boolean) {
        val s = _state.value
        if (s.saving) return
        if (amount == null || !amount.isPositive) {
            _state.update { it.copy(amountError = true) }
            return
        }
        val account = s.selectedAccount ?: run {
            viewModelScope.launch { _events.emit(QuickEntryEvent.Error("Choose an account")) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val now = clock.nowMillis()
                val tx = Transaction(
                    id = Ids.newId(), type = s.type, amount = amount, accountId = account.account.id,
                    categoryId = s.selectedCategory?.id, date = s.date, time = s.time,
                    payee = s.payee.trim().ifEmpty { null }, description = s.description.trim().ifEmpty { null },
                    notes = s.notes.trim().ifEmpty { null },
                    tags = s.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    createdAt = now, updatedAt = now,
                )
                record(tx)
                if (addAnother) {
                    _state.update {
                        it.copy(saving = false, selectedCategory = null, payee = "", description = "", notes = "", tags = "", initialAmount = null, amountError = false)
                    }
                    // Freshly used category moves into "recent".
                    loadCategories(s.type, null)
                } else {
                    _state.update { it.copy(saving = false) }
                }
                _events.emit(QuickEntryEvent.Saved(addAnother))
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _events.emit(QuickEntryEvent.Error(e.message ?: e.toString()))
            }
        }
    }

    companion object {
        const val ARG_CATEGORY_ID = "categoryId"
        const val ARG_AMOUNT_MINOR = "amountMinor"
        const val ARG_ACCOUNT_ID = "accountId"
        const val ARG_DATE = "date"
    }
}
