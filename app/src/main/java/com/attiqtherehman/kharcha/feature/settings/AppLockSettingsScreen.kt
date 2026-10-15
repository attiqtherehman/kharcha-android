package com.attiqtherehman.kharcha.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.applock.AppLockManager
import com.attiqtherehman.kharcha.applock.AppLockSettings
import com.attiqtherehman.kharcha.applock.AppLockStore
import com.attiqtherehman.kharcha.applock.PinVerifier
import com.attiqtherehman.kharcha.applock.biometricsAvailable
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockSettingsViewModel @Inject constructor(
    private val store: AppLockStore,
    private val pinVerifier: PinVerifier,
    private val manager: AppLockManager,
) : ViewModel() {
    val settings = store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLockSettings())

    fun setEnabled(v: Boolean) = viewModelScope.launch {
        if (v && !store.current().hasPin) return@launch
        store.setEnabled(v)
        if (!v) manager.unlock()
    }
    fun setTimeout(seconds: Int) = viewModelScope.launch { store.setTimeout(seconds) }
    fun setBiometric(v: Boolean) = viewModelScope.launch { store.setBiometric(v) }
    fun setWidgetAllowed(v: Boolean) = viewModelScope.launch { store.setWidgetAllowed(v) }
    fun setPin(pin: String, onDone: (String?) -> Unit) = viewModelScope.launch {
        try { pinVerifier.setPin(pin); onDone(null) } catch (e: Exception) { onDone(e.message ?: e.toString()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSettingsScreen(onBack: () -> Unit, viewModel: AppLockSettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPinDialog by remember { mutableStateOf(false) }
    var showTimeout by remember { mutableStateOf(false) }
    Scaffold(topBar = { KharchaTopBar(title = stringResource(R.string.settings_app_lock), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.settings_app_lock_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            SettingRow(stringResource(if (s.hasPin) R.string.settings_change_pin else R.string.settings_set_pin), if (s.hasPin) "••••" else stringResource(R.string.lock_set_pin_title)) { showPinDialog = true }
            SwitchRow(stringResource(R.string.settings_app_lock), s.enabled && s.hasPin) { if (s.hasPin) viewModel.setEnabled(it) else showPinDialog = true }
            if (biometricsAvailable(context)) SwitchRow(stringResource(R.string.settings_use_biometrics), s.biometricEnabled) { viewModel.setBiometric(it) }
            SettingRow(stringResource(R.string.settings_app_lock_timeout), timeoutLabel(s.timeoutSeconds)) { showTimeout = true }
            SwitchRow(stringResource(R.string.widget_quick_expense_name) + ": " + stringResource(R.string.widget_saved).trim('✓', ' '), s.allowWidgetQuickSaveWhileLocked) { viewModel.setWidgetAllowed(it) }
            Text(stringResource(R.string.lock_recovery_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
    }
    if (showPinDialog) {
        SetPinDialog(onDone = { pin -> viewModel.setPin(pin) { }; showPinDialog = false }, onDismiss = { showPinDialog = false })
    }
    if (showTimeout) {
        ChoiceDialog(
            title = stringResource(R.string.settings_app_lock_timeout),
            options = listOf(0, 30, 60, 300).map { it.toString() to timeoutLabel(it) },
            selected = s.timeoutSeconds.toString(),
            onSelect = { viewModel.setTimeout(it.toInt()); showTimeout = false },
            onDismiss = { showTimeout = false },
        )
    }
}

@Composable
private fun timeoutLabel(seconds: Int): String = when {
    seconds <= 0 -> stringResource(R.string.settings_lock_immediately)
    seconds < 60 -> stringResource(R.string.settings_lock_after_seconds, seconds)
    else -> stringResource(R.string.settings_lock_after_minutes, seconds / 60)
}

@Composable
private fun SetPinDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val mismatch = stringResource(R.string.lock_pin_mismatch)
    val tooShort = stringResource(R.string.lock_pin_too_short)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_set_pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = pin, onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pin = it }, label = { Text(stringResource(R.string.lock_enter_pin)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = confirm, onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirm = it }, label = { Text(stringResource(R.string.lock_confirm_pin)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length < 4 -> error = tooShort
                    pin != confirm -> error = mismatch
                    else -> onDone(pin)
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
