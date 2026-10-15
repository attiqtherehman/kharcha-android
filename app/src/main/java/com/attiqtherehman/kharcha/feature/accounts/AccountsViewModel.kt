package com.attiqtherehman.kharcha.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.domain.ledger.FinanceCalculator
import com.attiqtherehman.kharcha.domain.ledger.NetWorthSummary
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class AccountsUiState(
    val loading: Boolean = true,
    val active: List<AccountBalance> = emptyList(),
    val archived: List<AccountBalance> = emptyList(),
    val netWorth: NetWorthSummary = NetWorthSummary.EMPTY,
    val baseCurrency: String = "PKR",
    val showArchived: Boolean = false,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    accounts: AccountRepository,
    settings: SettingsDataStore,
) : ViewModel() {

    private val showArchived = MutableStateFlow(false)

    val uiState: StateFlow<AccountsUiState> = combine(
        accounts.observeBalances(),
        settings.settings,
        showArchived,
    ) { balances, prefs, archivedVisible ->
        AccountsUiState(
            loading = false,
            active = balances.filter { !it.account.isArchived },
            archived = balances.filter { it.account.isArchived },
            netWorth = FinanceCalculator.netWorth(balances, prefs.baseCurrency),
            baseCurrency = prefs.baseCurrency,
            showArchived = archivedVisible,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun toggleArchived() = showArchived.update { !it }
}
