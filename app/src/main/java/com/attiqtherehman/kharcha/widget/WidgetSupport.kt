package com.attiqtherehman.kharcha.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.updateAll
import com.attiqtherehman.kharcha.AppStartHook
import com.attiqtherehman.kharcha.core.common.DataChangeHub
import com.attiqtherehman.kharcha.core.common.Ids
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.MoneyFormatter
import com.attiqtherehman.kharcha.core.money.NumberGrouping
import com.attiqtherehman.kharcha.core.money.sumOfMoney
import com.attiqtherehman.kharcha.data.prefs.QuickPreset
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.di.ApplicationScope
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.model.CategoryKind
import com.attiqtherehman.kharcha.domain.model.Transaction
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.repository.CategoryRepository
import com.attiqtherehman.kharcha.domain.usecase.RecordTransactionUseCase
import com.attiqtherehman.kharcha.widget.balance.AccountBalanceWidget
import com.attiqtherehman.kharcha.widget.balance.AccountBalanceWidgetReceiver
import com.attiqtherehman.kharcha.widget.quick.QuickExpenseWidget
import com.attiqtherehman.kharcha.widget.quick.QuickExpenseWidgetReceiver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Glance widgets are not Hilt-injectable; they reach the app graph through this entry point. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun snapshots(): WidgetSnapshotRepository
    fun savePreset(): SavePresetExpenseUseCase
    fun widgetConfig(): WidgetConfigStore
}

/** Read-only queries shaped for widget composition (snapshots, not flows). */
@Singleton
class WidgetSnapshotRepository @Inject constructor(
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val settings: SettingsDataStore,
    private val appLock: com.attiqtherehman.kharcha.applock.AppLockStore,
    private val clock: Clock,
) {
    data class PresetView(val preset: QuickPreset, val label: String)

    data class QuickExpenseSnapshot(
        val favorites: List<Category>,
        val presets: List<PresetView>,
        val defaultAccountName: String?,
        /** App lock is on and one-tap saving while locked is not allowed: presets open quick entry instead. */
        val presetsLocked: Boolean,
    )

    data class BalanceLine(val name: String, val type: AccountType, val balance: Money)

    data class BalanceSnapshot(
        val total: Money,
        val lines: List<BalanceLine>,
        val currency: CurrencyInfo,
        val grouping: NumberGrouping,
        val hideAmounts: Boolean,
        val updatedAt: LocalTime,
        val configured: Boolean,
    )

    suspend fun quickExpense(): QuickExpenseSnapshot {
        val prefs = settings.current()
        val currency = CurrencyInfo.of(prefs.baseCurrency)
        val favorites = categories.favorites(CategoryKind.EXPENSE, 4)
        val catById = categories.getAll().associateBy { it.id }
        val presets = prefs.quickPresets.mapNotNull { p ->
            val cat = catById[p.categoryId] ?: return@mapNotNull null
            PresetView(p, p.label ?: "${MoneyFormatter.format(Money(p.amountMinor), currency, prefs.numberGrouping, hideZeroFraction = true)} · ${cat.name}")
        }.take(3)
        val accountId = prefs.lastUsedAccountId ?: prefs.defaultAccountId
        val accountName = accountId?.let { accounts.getAccount(it)?.takeIf { a -> !a.isArchived }?.name }
        val lock = appLock.current()
        return QuickExpenseSnapshot(favorites, presets, accountName, presetsLocked = lock.enabled && !lock.allowWidgetQuickSaveWhileLocked)
    }

    suspend fun balances(config: BalanceWidgetConfig?): BalanceSnapshot {
        val prefs = settings.current()
        val all = accounts.getBalances().filter { !it.account.isArchived && it.account.currencyCode == prefs.baseCurrency }
        val selected = if (config?.accountIds.isNullOrEmpty()) all else all.filter { it.account.id in config!!.accountIds!! }
        val lines = selected.map { BalanceLine(it.account.name, it.account.type, it.balance) }
        val total = selected.filter { it.account.type.isSpendable }.sumOfMoney { it.balance }
        return BalanceSnapshot(
            total = total, lines = lines, currency = CurrencyInfo.of(prefs.baseCurrency), grouping = prefs.numberGrouping,
            hideAmounts = config?.hideAmounts ?: false, updatedAt = clock.nowTime(), configured = config != null,
        )
    }
}

/** Saves a one-tap preset as a real expense through the same use case as the app. */
@Singleton
class SavePresetExpenseUseCase @Inject constructor(
    private val settings: SettingsDataStore,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val record: RecordTransactionUseCase,
    private val clock: Clock,
) {
    private val lastSaved = HashMap<String, Long>()

    /** Returns the label to show in the widget, or null if ignored as a double tap. */
    suspend operator fun invoke(presetId: String): String? {
        val now = clock.nowMillis()
        synchronized(lastSaved) {
            val last = lastSaved[presetId] ?: 0L
            if (now - last < 1_500L) return null
            lastSaved[presetId] = now
        }
        val prefs = settings.current()
        val preset = prefs.quickPresets.firstOrNull { it.id == presetId } ?: return null
        val category = categories.get(preset.categoryId) ?: return null
        val accountId = listOfNotNull(preset.accountId, prefs.lastUsedAccountId, prefs.defaultAccountId)
            .firstOrNull { id -> accounts.getAccount(id)?.isArchived == false }
            ?: accounts.getAccounts().firstOrNull { !it.isArchived }?.id
            ?: return null
        val tx = Transaction(
            id = Ids.newId(), type = TransactionType.EXPENSE, amount = Money(preset.amountMinor), accountId = accountId,
            categoryId = category.id, date = clock.today(), time = clock.nowTime(), description = preset.label,
            createdAt = now, updatedAt = now,
        )
        record(tx)
        val currency = CurrencyInfo.of(accounts.getAccount(accountId)?.currencyCode ?: prefs.baseCurrency)
        return "${MoneyFormatter.format(tx.amount, currency, prefs.numberGrouping, hideZeroFraction = true)} · ${category.name}"
    }
}

data class BalanceWidgetConfig(val accountIds: Set<String>?, val hideAmounts: Boolean)

private val Context.widgetConfigStore: DataStore<Preferences> by preferencesDataStore(name = "widget_config")

/** Per-appWidgetId configuration; device specific, never part of a backup. */
@Singleton
class WidgetConfigStore @Inject constructor(@ApplicationContext private val context: Context) {
    private fun accountsKey(id: Int) = stringSetPreferencesKey("w$id.accounts")
    private fun hideKey(id: Int) = booleanPreferencesKey("w$id.hide")

    suspend fun get(appWidgetId: Int): BalanceWidgetConfig? {
        val prefs = context.widgetConfigStore.data.first()
        val accounts = prefs[accountsKey(appWidgetId)]
        val hide = prefs[hideKey(appWidgetId)]
        if (accounts == null && hide == null) return null
        return BalanceWidgetConfig(accounts?.takeIf { it.isNotEmpty() }, hide ?: false)
    }

    suspend fun save(appWidgetId: Int, config: BalanceWidgetConfig) {
        context.widgetConfigStore.edit {
            it[accountsKey(appWidgetId)] = config.accountIds ?: emptySet()
            it[hideKey(appWidgetId)] = config.hideAmounts
        }
    }

    suspend fun delete(appWidgetIds: IntArray) {
        context.widgetConfigStore.edit { prefs -> appWidgetIds.forEach { prefs.remove(accountsKey(it)); prefs.remove(hideKey(it)) } }
    }
}

/** Pushes widget refreshes after data changes (debounced) and on app start. No polling. */
@OptIn(FlowPreview::class)
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
    hub: DataChangeHub,
) : AppStartHook {

    init {
        scope.launch { hub.financialEvents.debounce(400).collect { runCatching { refreshNow() } } }
    }

    override suspend fun onAppStart() = refreshNow()

    suspend fun refreshNow() {
        QuickExpenseWidget().updateAll(context)
        AccountBalanceWidget().updateAll(context)
    }
}

/** Lets the Settings screen ask the launcher to place a widget (API 26+). */
object WidgetPinner {
    fun isSupported(context: Context): Boolean = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun pinQuickExpense(context: Context): Boolean =
        AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, QuickExpenseWidgetReceiver::class.java), null, null)

    fun pinBalances(context: Context): Boolean =
        AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, AccountBalanceWidgetReceiver::class.java), null, null)
}
