package com.attiqtherehman.kharcha.feature.quickentry

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attiqtherehman.kharcha.applock.LockScreen
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.ui.AppViewModel
import com.attiqtherehman.kharcha.ui.common.LocalMoneyFormat
import com.attiqtherehman.kharcha.ui.common.MoneyFormat
import com.attiqtherehman.kharcha.ui.theme.KharchaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Lightweight entry point used by the home-screen widget and the daily reminder notification.
 * It hosts only the quick-entry screen and finishes on save, so the user never has to navigate
 * through the whole app to record an expense. Honours the app lock.
 */
@AndroidEntryPoint
class QuickEntryActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            val locked by appViewModel.locked.collectAsStateWithLifecycle()
            KharchaTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(
                    LocalMoneyFormat provides MoneyFormat(CurrencyInfo.of(settings.baseCurrency), settings.numberGrouping),
                ) {
                    if (locked) LockScreen(manager = appViewModel.appLockManager) else QuickEntryScreen(onClose = { finish() })
                }
            }
        }
    }

    companion object {
        fun intent(context: Context, categoryId: String? = null, amountMinor: Long? = null, accountId: String? = null): Intent =
            Intent(context, QuickEntryActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                categoryId?.let { putExtra(QuickEntryViewModel.ARG_CATEGORY_ID, it) }
                amountMinor?.let { putExtra(QuickEntryViewModel.ARG_AMOUNT_MINOR, it) }
                accountId?.let { putExtra(QuickEntryViewModel.ARG_ACCOUNT_ID, it) }
            }
    }
}
