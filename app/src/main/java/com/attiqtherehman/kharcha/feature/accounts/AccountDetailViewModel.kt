package com.attiqtherehman.kharcha.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.attiqtherehman.kharcha.core.common.HasReferencesException
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.TransactionDetails
import com.attiqtherehman.kharcha.domain.model.TransactionFilter
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.repository.TransactionRepository
import com.attiqtherehman.kharcha.domain.usecase.AdjustBalanceUseCase
import com.attiqtherehman.kharcha.domain.usecase.FixOpeningBalanceUseCase
import com.attiqtherehman.kharcha.ui.navigation.AccountDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AccountDetailState(
    val loading: Boolean = true,
    val balance: AccountBalance? = null,
    val transactions: List<TransactionDetails> = emptyList(),
    val today: LocalDate = LocalDate.now(),
)

sealed interface AccountDetailEvent {
    data object Deleted : AccountDetailEvent
    data class DeleteBlocked(val count: Int) : AccountDetailEvent
    data class Message(val text: String) : AccountDetailEvent
}

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accounts: AccountRepository,
    transactions: TransactionRepository,
    private val adjustBalance: AdjustBalanceUseCase,
    private val fixOpeningBalance: FixOpeningBalanceUseCase,
    private val clock: Clock,
) : ViewModel() {

    val accountId: String = savedStateHandle.toRoute<AccountDetailRoute>().accountId

    private val _events = MutableSharedFlow<AccountDetailEvent>()
    val events: SharedFlow<AccountDetailEvent> = _events.asSharedFlow()

    val state: StateFlow<AccountDetailState> = combine(
        accounts.observeBalance(accountId),
        transactions.observe(TransactionFilter(accountIds = setOf(accountId))),
    ) { balance, list ->
        AccountDetailState(loading = false, balance = balance, transactions = list, today = clock.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailState())

    fun setArchived(archived: Boolean) = viewModelScope.launch { accounts.setArchived(accountId, archived) }

    fun delete() = viewModelScope.launch {
        try {
            accounts.delete(accountId)
            _events.emit(AccountDetailEvent.Deleted)
        } catch (e: HasReferencesException) {
            _events.emit(AccountDetailEvent.DeleteBlocked(e.referenceCount))
        }
    }

    fun adjustTo(actual: Money, description: String?) = viewModelScope.launch {
        try {
            adjustBalance(accountId, actual, clock.today(), description?.ifBlank { null })
        } catch (e: Exception) {
            _events.emit(AccountDetailEvent.Message(e.message ?: e.toString()))
        }
    }

    fun fixOpening(newOpening: Money) = viewModelScope.launch {
        try {
            fixOpeningBalance(accountId, newOpening)
        } catch (e: Exception) {
            _events.emit(AccountDetailEvent.Message(e.message ?: e.toString()))
        }
    }
}
