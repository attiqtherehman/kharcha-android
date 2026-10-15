package com.attiqtherehman.kharcha

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attiqtherehman.kharcha.applock.LockScreen
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.ui.AppViewModel
import com.attiqtherehman.kharcha.ui.KharchaApp
import com.attiqtherehman.kharcha.ui.common.LocalMoneyFormat
import com.attiqtherehman.kharcha.ui.common.MoneyFormat
import com.attiqtherehman.kharcha.ui.theme.KharchaTheme
import dagger.hilt.android.AndroidEntryPoint

/** A navigation request coming from a notification tap. */
data class ExternalNavRequest(val reminderId: String, val markPaid: Boolean, val nonce: Long)

/**
 * Single-activity Compose host. Extends [FragmentActivity] because androidx.biometric's
 * BiometricPrompt requires one for the optional app lock.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var navRequest by mutableStateOf<ExternalNavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        navRequest = intent?.toNavRequest()
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            val locked by appViewModel.locked.collectAsStateWithLifecycle()
            val moneyFormat = MoneyFormat(currency = CurrencyInfo.of(settings.baseCurrency), grouping = settings.numberGrouping)
            KharchaTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(LocalMoneyFormat provides moneyFormat) {
                    if (locked) {
                        LockScreen(manager = appViewModel.appLockManager)
                    } else {
                        KharchaApp(externalRequest = navRequest)
                        NotificationOnboarding(asked = settings.notificationPermissionAsked, onShown = appViewModel::notificationPromptShown)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toNavRequest()?.let { navRequest = it }
    }

    private fun Intent.toNavRequest(): ExternalNavRequest? {
        val id = getStringExtra(EXTRA_OPEN_REMINDER) ?: return null
        return ExternalNavRequest(id, getBooleanExtra(EXTRA_MARK_PAID, false), System.nanoTime())
    }

    companion object {
        const val EXTRA_OPEN_REMINDER = "openReminderId"
        const val EXTRA_MARK_PAID = "markPaid"
    }
}

/** One-time, contextual request for POST_NOTIFICATIONS (Android 13+): explains why before asking. */
@Composable
private fun NotificationOnboarding(asked: Boolean, onShown: () -> Unit) {
    if (Build.VERSION.SDK_INT < 33 || asked) return
    val context = androidx.compose.ui.platform.LocalContext.current
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        onShown(); return
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onShown() }
    AlertDialog(
        onDismissRequest = onShown,
        title = { Text(stringResource(R.string.onboarding_notifications_title)) },
        text = { Text(stringResource(R.string.onboarding_notifications_text)) },
        confirmButton = { TextButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.onboarding_allow)) } },
        dismissButton = { TextButton(onClick = onShown) { Text(stringResource(R.string.onboarding_not_now)) } },
    )
}
