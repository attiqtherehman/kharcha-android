package com.attiqtherehman.kharcha.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.domain.model.TransactionDetails
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.domain.repository.TransactionRepository
import com.attiqtherehman.kharcha.domain.usecase.DeleteTransactionUseCase
import com.attiqtherehman.kharcha.ui.navigation.TransactionDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionDetailState(
    val loading: Boolean = true,
    val details: TransactionDetails? = null,
    /** Rows that link to this one: an ATM fee for a transfer, refunds for an expense. */
    val linked: List<TransactionDetails> = emptyList(),
    /** The row this one links to: the transfer for a fee, the original expense for a refund. */
    val parent: TransactionDetails? = null,
    val refundedSoFar: Money = Money.ZERO,
) {
    val canRefund: Boolean
        get() = details?.transaction?.type == TransactionType.EXPENSE && (details.transaction.amount - refundedSoFar).isPositive
}

sealed interface TransactionDetailEvent {
    data object Deleted : TransactionDetailEvent
    data class Error(val message: String) : TransactionDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val deleteTransaction: DeleteTransactionUseCase,
) : ViewModel() {

    val transactionId: String = savedStateHandle.toRoute<TransactionDetailRoute>().transactionId

    private val _events = MutableSharedFlow<TransactionDetailEvent>()
    val events: SharedFlow<TransactionDetailEvent> = _events.asSharedFlow()

    private val details = transactions.observeDetails(transactionId)
    private val linked = transactions.observeLinkedTo(transactionId)
    private val parent = details.flatMapLatest { d ->
        val parentId = d?.transaction?.linkedTransactionId
        if (parentId == null) flowOf(null) else transactions.observeDetails(parentId)
    }

    val state: StateFlow<TransactionDetailState> = combine(details, linked, parent) { d, l, p ->
        TransactionDetailState(
            loading = false,
            details = d,
            linked = l,
            parent = p,
            refundedSoFar = l.filter { it.transaction.type == TransactionType.REFUND }.fold(Money.ZERO) { acc, r -> acc + r.transaction.amount },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionDetailState())

    fun delete(includeLinked: Boolean) = viewModelScope.launch {
        try {
            deleteTransaction(transactionId, includeLinked)
            _events.emit(TransactionDetailEvent.Deleted)
        } catch (e: Exception) {
            _events.emit(TransactionDetailEvent.Error(e.message ?: e.toString()))
        }
    }
}
