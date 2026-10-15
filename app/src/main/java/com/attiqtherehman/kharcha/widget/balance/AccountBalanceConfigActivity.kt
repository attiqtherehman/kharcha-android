package com.attiqtherehman.kharcha.widget.balance

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.domain.model.Account
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import com.attiqtherehman.kharcha.ui.common.SectionHeader
import com.attiqtherehman.kharcha.ui.theme.KharchaTheme
import com.attiqtherehman.kharcha.widget.BalanceWidgetConfig
import com.attiqtherehman.kharcha.widget.WidgetConfigStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Standard APPWIDGET_CONFIGURE flow: choose accounts and whether to hide amounts. */
@AndroidEntryPoint
class AccountBalanceConfigActivity : FragmentActivity() {

    @Inject lateinit var accounts: AccountRepository
    @Inject lateinit var configStore: WidgetConfigStore

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        // Until the user saves, treat the placement as cancelled.
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        setContent {
            KharchaTheme {
                var all by remember { mutableStateOf<List<Account>>(emptyList()) }
                var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
                var hide by remember { mutableStateOf(false) }
                var loaded by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    all = accounts.getAccounts().filter { !it.isArchived }
                    configStore.get(appWidgetId)?.let { cfg ->
                        selected = cfg.accountIds ?: all.map { it.id }.toSet()
                        hide = cfg.hideAmounts
                    } ?: run { selected = all.map { it.id }.toSet() }
                    loaded = true
                }
                Scaffold(
                    topBar = { KharchaTopBar(title = stringResource(R.string.widget_config_title), onBack = { finish() }) },
                    bottomBar = {
                        Button(
                            onClick = { save(BalanceWidgetConfig(selected.takeIf { it.size != all.size }, hide)) },
                            enabled = loaded,
                            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                        ) { Text(stringResource(R.string.action_save)) }
                    },
                ) { padding ->
                    Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                        SectionHeader(stringResource(R.string.widget_config_accounts))
                        all.forEach { account ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { selected = if (account.id in selected) selected - account.id else selected + account.id }.padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = account.id in selected, onCheckedChange = { checked -> selected = if (checked) selected + account.id else selected - account.id })
                                Spacer(Modifier.width(8.dp))
                                Text(account.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.widget_config_hide_amounts), modifier = Modifier.weight(1f))
                            Switch(checked = hide, onCheckedChange = { hide = it })
                        }
                    }
                }
            }
        }
    }

    private fun save(config: BalanceWidgetConfig) {
        lifecycleScope.launch {
            configStore.save(appWidgetId, config)
            val glanceId = GlanceAppWidgetManager(this@AccountBalanceConfigActivity).getGlanceIdBy(appWidgetId)
            AccountBalanceWidget().update(this@AccountBalanceConfigActivity, glanceId)
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}
