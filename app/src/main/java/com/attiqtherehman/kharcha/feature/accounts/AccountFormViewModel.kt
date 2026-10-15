package com.attiqtherehman.kharcha.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.attiqtherehman.kharcha.core.common.Ids
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.MoneyFormatter
import com.attiqtherehman.kharcha.core.money.MoneyParseResult
import com.attiqtherehman.kharcha.core.money.MoneyParser
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.data.seed.DefaultData
import com.attiqtherehman.kharcha.domain.model.Account
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.usecase.FixOpeningBalanceUseCase
import com.attiqtherehman.kharcha.ui.navigation.AccountFormRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    val currencyCode: String = "PKR",
    val currencyLocked: Boolean = false,
    val openingText: String = "",
    val openingLocked: Boolean = false,
    val creditLimitText: String = "",
    val color: Long = DefaultData.palette.first(),
    val icon: String = "bank",
    val notes: String = "",
    val nameError: Boolean = false,
    val openingError: String? = null,
    val creditLimitError: String? = null,
    val saving: Boolean = false,
) {
    val currency: CurrencyInfo get() = CurrencyInfo.of(currencyCode)
}

sealed interface AccountFormEvent {
    data class Saved(val accountId: String) : AccountFormEvent
    data class Error(val message: String) : AccountFormEvent
}

@HiltViewModel
class AccountFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accounts: AccountRepository,
    private val settings: SettingsDataStore,
    private val fixOpeningBalance: FixOpeningBalanceUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<AccountFormRoute>()
    private val editingId: String? = route.accountId
    private var existing: Account? = null

    private val _state = MutableStateFlow(AccountFormState())
    val state: StateFlow<AccountFormState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AccountFormEvent>()
    val events: SharedFlow<AccountFormEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val base = settings.current().baseCurrency
            if (editingId == null) {
                _state.update { it.copy(loading = false, currencyCode = base) }
            } else {
                val account = accounts.getAccount(editingId)
                if (account == null) {
                    _events.emit(AccountFormEvent.Error("Account not found"))
                    return@launch
                }
                existing = account
                val txCount = accounts.transactionCount(account.id)
                val currency = CurrencyInfo.of(account.currencyCode)
                val openingShown = if (account.type == AccountType.CREDIT_CARD) -account.openingBalance else account.openingBalance
                _state.update {
                    it.copy(
                        loading = false, isEdit = true, name = account.name, type = account.type,
                        currencyCode = account.currencyCode, currencyLocked = txCount > 0,
                        openingText = MoneyFormatter.toPlainDecimal(openingShown, currency.minorDigits),
                        openingLocked = txCount > 0,
                        creditLimitText = account.creditLimit?.let { l -> MoneyFormatter.toPlainDecimal(l, currency.minorDigits) } ?: "",
                        color = account.color.toLong() and 0xFFFFFFFFL, icon = account.icon, notes = account.notes ?: "",
                    )
                }
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v, nameError = false) }
    fun setType(v: AccountType) = _state.update { s ->
        s.copy(type = v, icon = if (s.isEdit) s.icon else defaultIcon(v))
    }
    fun setCurrency(code: String) = _state.update { it.copy(currencyCode = code) }
    fun setOpening(v: String) = _state.update { it.copy(openingText = v, openingError = null) }
    fun setCreditLimit(v: String) = _state.update { it.copy(creditLimitText = v, creditLimitError = null) }
    fun setColor(v: Long) = _state.update { it.copy(color = v) }
    fun setIcon(v: String) = _state.update { it.copy(icon = v) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }

    private fun defaultIcon(type: AccountType) = when (type) {
        AccountType.CASH -> "cash"
        AccountType.BANK -> "bank"
        AccountType.SAVINGS -> "savings"
        AccountType.DIGITAL_WALLET -> "phone"
        AccountType.CREDIT_CARD -> "card"
        AccountType.OTHER -> "wallet"
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val name = s.name.trim()
        if (name.isEmpty()) { _state.update { it.copy(nameError = true) }; return }
        val currency = s.currency
        val openingParsed = if (s.openingText.isBlank()) MoneyParseResult.Success(Money.ZERO) else MoneyParser.parse(s.openingText, currency)
        val opening = when (openingParsed) {
            is MoneyParseResult.Success -> openingParsed.money
            is MoneyParseResult.Failure -> { _state.update { it.copy(openingError = parseError(openingParsed.reason)) }; return }
        }
        val creditLimit: Money? = if (s.type == AccountType.CREDIT_CARD && s.creditLimitText.isNotBlank()) {
            when (val r = MoneyParser.parse(s.creditLimitText, currency)) {
                is MoneyParseResult.Success -> r.money
                is MoneyParseResult.Failure -> { _state.update { it.copy(creditLimitError = parseError(r.reason)) }; return }
            }
        } else null
        // Cards store "outstanding owed" as a negative opening balance.
        val storedOpening = if (s.type == AccountType.CREDIT_CARD) -opening else opening

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val now = clock.nowMillis()
                val current = existing
                if (current == null) {
                    val account = Account(
                        id = Ids.newId(), name = name, type = s.type, currencyCode = s.currencyCode,
                        openingBalance = storedOpening, creditLimit = creditLimit, color = s.color.toInt(), icon = s.icon,
                        notes = s.notes.trim().ifEmpty { null }, createdAt = now, updatedAt = now,
                    )
                    accounts.create(account)
                    if (settings.current().defaultAccountId == null) settings.setDefaultAccountId(account.id)
                    _events.emit(AccountFormEvent.Saved(account.id))
                } else {
                    val updated = current.copy(
                        name = name, type = s.type,
                        currencyCode = if (s.currencyLocked) current.currencyCode else s.currencyCode,
                        creditLimit = if (s.type == AccountType.CREDIT_CARD) creditLimit else null,
                        color = s.color.toInt(), icon = s.icon, notes = s.notes.trim().ifEmpty { null },
                    )
                    accounts.update(updated)
                    if (!s.openingLocked && storedOpening != current.openingBalance) {
                        fixOpeningBalance(current.id, storedOpening)
                    }
                    _events.emit(AccountFormEvent.Saved(current.id))
                }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _events.emit(AccountFormEvent.Error(e.message ?: e.toString()))
            }
        }
    }

    private fun parseError(reason: MoneyParseResult.Reason): String = when (reason) {
        MoneyParseResult.Reason.EMPTY -> "Enter an amount"
        MoneyParseResult.Reason.INVALID -> "Not a valid amount"
        MoneyParseResult.Reason.TOO_MANY_DECIMALS -> "Too many decimal places"
        MoneyParseResult.Reason.TOO_LARGE -> "Amount is too large"
    }
}
