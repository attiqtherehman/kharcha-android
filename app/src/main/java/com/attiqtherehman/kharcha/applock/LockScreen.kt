package com.attiqtherehman.kharcha.applock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attiqtherehman.kharcha.R
import kotlinx.coroutines.launch

fun biometricsAvailable(context: android.content.Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

/** Launches the system prompt; [onSuccess] runs on the main thread. */
fun showBiometricPrompt(activity: FragmentActivity, title: String, subtitle: String, onSuccess: () -> Unit, onError: (String) -> Unit = {}) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errString.toString())
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        .build()
    prompt.authenticate(info)
}

/** Full-screen gate shown above the app while locked. */
@Composable
fun LockScreen(manager: AppLockManager, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = context as? FragmentActivity
    val settings by manager.settings.collectAsStateWithLifecycle(initialValue = AppLockSettings())
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var showPin by remember { mutableStateOf(false) }
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)
    val wrongPin = stringResource(R.string.lock_wrong_pin)
    val canBiometric = activity != null && settings.biometricEnabled && biometricsAvailable(context)

    fun biometric() {
        if (activity == null) return
        showBiometricPrompt(activity, title, subtitle, onSuccess = { manager.unlock() }, onError = { message = it })
    }

    LaunchedEffect(canBiometric) { if (canBiometric && !showPin) biometric() }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            if (showPin || !canBiometric) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> if (v.length <= 8 && v.all { it.isDigit() }) { pin = v; message = null } },
                    label = { Text(stringResource(R.string.lock_enter_pin)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = settings.hasPin,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            when (val r = manager.tryPin(pin)) {
                                AppLockManager.PinResult.Ok -> Unit
                                is AppLockManager.PinResult.Wrong -> { message = "$wrongPin (${r.attemptsLeft})"; pin = "" }
                                is AppLockManager.PinResult.LockedOut -> { message = resources.getString(R.string.lock_locked_out, r.secondsLeft); pin = "" }
                                AppLockManager.PinResult.KeyUnavailable -> message = resources.getString(R.string.lock_recovery_hint)
                            }
                        }
                    },
                    enabled = pin.length >= 4 && settings.hasPin,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.lock_unlock)) }
                if (!settings.hasPin) Text(stringResource(R.string.lock_recovery_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (canBiometric) TextButton(onClick = { showPin = false; biometric() }) { Icon(Icons.Filled.Fingerprint, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text(stringResource(R.string.lock_use_biometrics)) }
            } else {
                Button(onClick = { biometric() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Fingerprint, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text(stringResource(R.string.lock_unlock)) }
                if (settings.hasPin) TextButton(onClick = { showPin = true }) { Text(stringResource(R.string.lock_use_pin)) }
            }
            message?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
