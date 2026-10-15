package com.attiqtherehman.kharcha.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attiqtherehman.kharcha.core.common.DataChangeHub
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.money.NumberGrouping
import com.attiqtherehman.kharcha.data.prefs.AppSettings
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.domain.model.Account
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.notifications.ReminderNotifier
import com.attiqtherehman.kharcha.notifications.ReminderScheduler
import com.attiqtherehman.kharcha.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

data class ReminderStatus(
    val notificationsAllowed: Boolean = true,
    val exactAlarmsAllowed: Boolean = true,
    val ignoringBatteryOptimizations: Boolean = true,
    val nextWake: Instant? = null,
)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val accounts: List<Account> = emptyList(),
    val reminderStatus: ReminderStatus = ReminderStatus(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsDataStore,
    accounts: AccountRepository,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val hub: DataChangeHub,
    private val clock: Clock,
) : ViewModel() {

    private val status = MutableStateFlow(ReminderStatus())

    val state: StateFlow<SettingsUiState> = combine(store.settings, accounts.observeAccounts(), status, scheduler.nextWake) { s, accs, st, next ->
        SettingsUiState(s, accs.filter { !it.isArchived }, st.copy(nextWake = next))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init { refreshStatus() }

    /** Re-reads permission state; call from onResume so returning from system settings updates the UI. */
    fun refreshStatus() {
        status.update {
            it.copy(
                notificationsAllowed = notifier.notificationsAllowed(),
                exactAlarmsAllowed = scheduler.canScheduleExact(),
                ignoringBatteryOptimizations = scheduler.isIgnoringBatteryOptimizations(),
            )
        }
        viewModelScope.launch { scheduler.rescheduleAll() }
    }

    fun setDefaultAccount(id: String?) = viewModelScope.launch { store.setDefaultAccountId(id) }
    fun setBaseCurrency(code: String) = viewModelScope.launch { store.setBaseCurrency(code) }
    fun setNumberGrouping(g: NumberGrouping) = viewModelScope.launch { store.setNumberGrouping(g) }
    fun setWeekStart(d: DayOfWeek) = viewModelScope.launch { store.setWeekStart(d) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { store.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { store.setDynamicColor(enabled) }
    fun setBudgetWarnings(at75: Boolean, at90: Boolean, at100: Boolean) = viewModelScope.launch { store.setBudgetWarnings(at75, at90, at100) }

    fun setDailyReminderEnabled(enabled: Boolean) = viewModelScope.launch {
        store.setDailyReminderEnabled(enabled)
        hub.remindersChanged()
    }

    fun setDailyReminderTime(time: LocalTime) = viewModelScope.launch {
        store.setDailyReminderTime(time)
        hub.remindersChanged()
    }

    fun notificationPermissionAsked() = viewModelScope.launch { store.setNotificationPermissionAsked(true) }
}
