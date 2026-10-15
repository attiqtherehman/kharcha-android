package com.attiqtherehman.kharcha.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.attiqtherehman.kharcha.core.money.NumberGrouping
import com.attiqtherehman.kharcha.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** A one-tap widget/quick-entry preset such as "Rs 200 · Transport". */
@Serializable
data class QuickPreset(
    val id: String,
    val categoryId: String,
    val amountMinor: Long,
    val accountId: String? = null,
    val label: String? = null,
)

data class AppSettings(
    val baseCurrency: String = "PKR",
    val defaultAccountId: String? = null,
    val lastUsedAccountId: String? = null,
    val numberGrouping: NumberGrouping = NumberGrouping.STANDARD,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val dailyReminderEnabled: Boolean = true,
    val dailyReminderTime: LocalTime = LocalTime.of(22, 30),
    val budgetWarn75: Boolean = true,
    val budgetWarn90: Boolean = true,
    val budgetWarn100: Boolean = true,
    val onboardingDone: Boolean = false,
    val notificationPermissionAsked: Boolean = false,
    val lastBackupAt: Long? = null,
    val quickPresets: List<QuickPreset> = emptyList(),
    val hideAmountsOnHome: Boolean = false,
    /** Watermark for the reminder evaluator: notifications due at or before this instant were handled. */
    val lastReminderEvaluationAt: Long = 0L,
    /** "yyyy-MM|categoryId|75" keys already notified, pruned to the last two months. */
    val budgetAlertsNotified: Set<String> = emptySet(),
    /** Last versionCode for which widget previews were published (API 35+). */
    val widgetPreviewsVersion: Int = 0,
)

/** Portable subset written into backups. Device-specific values (lock, widgets) are excluded. */
@Serializable
data class PortableSettings(
    val baseCurrency: String,
    val defaultAccountId: String?,
    val numberGrouping: String,
    val themeMode: String,
    val dynamicColor: Boolean,
    val weekStart: String,
    val dailyReminderEnabled: Boolean,
    val dailyReminderTime: String,
    val budgetWarn75: Boolean,
    val budgetWarn90: Boolean,
    val budgetWarn100: Boolean,
    val quickPresets: List<QuickPreset>,
    val hideAmountsOnHome: Boolean,
)

@Singleton
class SettingsDataStore(private val store: DataStore<Preferences>) {

    @Inject constructor(@ApplicationContext context: Context) : this(context.settingsStore)

    private object Keys {
        val baseCurrency = stringPreferencesKey("base_currency")
        val defaultAccountId = stringPreferencesKey("default_account_id")
        val lastUsedAccountId = stringPreferencesKey("last_used_account_id")
        val numberGrouping = stringPreferencesKey("number_grouping")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val weekStart = stringPreferencesKey("week_start")
        val dailyReminderEnabled = booleanPreferencesKey("daily_reminder_enabled")
        val dailyReminderTime = stringPreferencesKey("daily_reminder_time")
        val budgetWarn75 = booleanPreferencesKey("budget_warn_75")
        val budgetWarn90 = booleanPreferencesKey("budget_warn_90")
        val budgetWarn100 = booleanPreferencesKey("budget_warn_100")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val notificationPermissionAsked = booleanPreferencesKey("notification_permission_asked")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val quickPresets = stringPreferencesKey("quick_presets")
        val hideAmountsOnHome = booleanPreferencesKey("hide_amounts_on_home")
        val lastReminderEvaluationAt = longPreferencesKey("last_reminder_evaluation_at")
        val widgetPreviewsVersion = intPreferencesKey("widget_previews_version")
        val budgetAlertsNotified = androidx.datastore.preferences.core.stringSetPreferencesKey("budget_alerts_notified")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val defaults = AppSettings()

    val settings: Flow<AppSettings> = store.data.map { p -> p.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        baseCurrency = this[Keys.baseCurrency] ?: defaults.baseCurrency,
        defaultAccountId = this[Keys.defaultAccountId],
        lastUsedAccountId = this[Keys.lastUsedAccountId],
        numberGrouping = this[Keys.numberGrouping]?.let { runCatching { NumberGrouping.valueOf(it) }.getOrNull() } ?: defaults.numberGrouping,
        themeMode = this[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: defaults.themeMode,
        dynamicColor = this[Keys.dynamicColor] ?: defaults.dynamicColor,
        weekStart = this[Keys.weekStart]?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() } ?: defaults.weekStart,
        dailyReminderEnabled = this[Keys.dailyReminderEnabled] ?: defaults.dailyReminderEnabled,
        dailyReminderTime = this[Keys.dailyReminderTime]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: defaults.dailyReminderTime,
        budgetWarn75 = this[Keys.budgetWarn75] ?: defaults.budgetWarn75,
        budgetWarn90 = this[Keys.budgetWarn90] ?: defaults.budgetWarn90,
        budgetWarn100 = this[Keys.budgetWarn100] ?: defaults.budgetWarn100,
        onboardingDone = this[Keys.onboardingDone] ?: false,
        notificationPermissionAsked = this[Keys.notificationPermissionAsked] ?: false,
        lastBackupAt = this[Keys.lastBackupAt],
        quickPresets = this[Keys.quickPresets]?.let { runCatching { json.decodeFromString<List<QuickPreset>>(it) }.getOrNull() } ?: emptyList(),
        hideAmountsOnHome = this[Keys.hideAmountsOnHome] ?: false,
        lastReminderEvaluationAt = this[Keys.lastReminderEvaluationAt] ?: 0L,
        widgetPreviewsVersion = this[Keys.widgetPreviewsVersion] ?: 0,
        budgetAlertsNotified = this[Keys.budgetAlertsNotified] ?: emptySet(),
    )

    private suspend fun edit(block: MutablePreferences.() -> Unit) {
        store.edit { it.block() }
    }

    suspend fun setBaseCurrency(code: String) = edit { this[Keys.baseCurrency] = code }
    suspend fun setDefaultAccountId(id: String?) = edit { if (id == null) remove(Keys.defaultAccountId) else this[Keys.defaultAccountId] = id }
    suspend fun setLastUsedAccountId(id: String?) = edit { if (id == null) remove(Keys.lastUsedAccountId) else this[Keys.lastUsedAccountId] = id }
    suspend fun setNumberGrouping(g: NumberGrouping) = edit { this[Keys.numberGrouping] = g.name }
    suspend fun setThemeMode(mode: ThemeMode) = edit { this[Keys.themeMode] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = edit { this[Keys.dynamicColor] = enabled }
    suspend fun setWeekStart(day: DayOfWeek) = edit { this[Keys.weekStart] = day.name }
    suspend fun setDailyReminderEnabled(enabled: Boolean) = edit { this[Keys.dailyReminderEnabled] = enabled }
    suspend fun setDailyReminderTime(time: LocalTime) = edit { this[Keys.dailyReminderTime] = time.toString().take(5) }
    suspend fun setBudgetWarnings(at75: Boolean, at90: Boolean, at100: Boolean) = edit {
        this[Keys.budgetWarn75] = at75; this[Keys.budgetWarn90] = at90; this[Keys.budgetWarn100] = at100
    }
    suspend fun setOnboardingDone(done: Boolean) = edit { this[Keys.onboardingDone] = done }
    suspend fun setNotificationPermissionAsked(asked: Boolean) = edit { this[Keys.notificationPermissionAsked] = asked }
    suspend fun setLastBackupAt(at: Long) = edit { this[Keys.lastBackupAt] = at }
    suspend fun setQuickPresets(presets: List<QuickPreset>) = edit { this[Keys.quickPresets] = json.encodeToString(presets) }
    suspend fun setHideAmountsOnHome(hide: Boolean) = edit { this[Keys.hideAmountsOnHome] = hide }
    suspend fun setLastReminderEvaluationAt(at: Long) = edit { this[Keys.lastReminderEvaluationAt] = at }
    suspend fun setWidgetPreviewsVersion(version: Int) = edit { this[Keys.widgetPreviewsVersion] = version }
    suspend fun setBudgetAlertsNotified(keys: Set<String>) = edit { this[Keys.budgetAlertsNotified] = keys }

    fun toPortable(s: AppSettings) = PortableSettings(
        baseCurrency = s.baseCurrency, defaultAccountId = s.defaultAccountId, numberGrouping = s.numberGrouping.name,
        themeMode = s.themeMode.name, dynamicColor = s.dynamicColor, weekStart = s.weekStart.name,
        dailyReminderEnabled = s.dailyReminderEnabled, dailyReminderTime = s.dailyReminderTime.toString().take(5),
        budgetWarn75 = s.budgetWarn75, budgetWarn90 = s.budgetWarn90, budgetWarn100 = s.budgetWarn100,
        quickPresets = s.quickPresets, hideAmountsOnHome = s.hideAmountsOnHome,
    )

    suspend fun applyPortable(p: PortableSettings) = edit {
        this[Keys.baseCurrency] = p.baseCurrency
        if (p.defaultAccountId == null) remove(Keys.defaultAccountId) else this[Keys.defaultAccountId] = p.defaultAccountId
        this[Keys.numberGrouping] = p.numberGrouping
        this[Keys.themeMode] = p.themeMode
        this[Keys.dynamicColor] = p.dynamicColor
        this[Keys.weekStart] = p.weekStart
        this[Keys.dailyReminderEnabled] = p.dailyReminderEnabled
        this[Keys.dailyReminderTime] = p.dailyReminderTime
        this[Keys.budgetWarn75] = p.budgetWarn75
        this[Keys.budgetWarn90] = p.budgetWarn90
        this[Keys.budgetWarn100] = p.budgetWarn100
        this[Keys.quickPresets] = json.encodeToString(p.quickPresets)
        this[Keys.hideAmountsOnHome] = p.hideAmountsOnHome
    }
}
