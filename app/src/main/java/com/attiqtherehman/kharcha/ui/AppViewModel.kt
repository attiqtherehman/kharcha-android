package com.attiqtherehman.kharcha.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attiqtherehman.kharcha.applock.AppLockManager
import com.attiqtherehman.kharcha.data.prefs.AppSettings
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** App-wide settings (theme, currency display) and lock state for the root composable. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsStore: SettingsDataStore,
    val appLockManager: AppLockManager,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val locked: StateFlow<Boolean> = appLockManager.locked

    fun notificationPromptShown() = viewModelScope.launch { settingsStore.setNotificationPermissionAsked(true) }
}
