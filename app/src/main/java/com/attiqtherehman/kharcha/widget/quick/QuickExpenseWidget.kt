package com.attiqtherehman.kharcha.widget.quick

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.feature.quickentry.QuickEntryActivity
import com.attiqtherehman.kharcha.feature.quickentry.QuickEntryViewModel
import com.attiqtherehman.kharcha.widget.KharchaGlanceColors
import com.attiqtherehman.kharcha.widget.WidgetEntryPoint
import com.attiqtherehman.kharcha.widget.WidgetSnapshotRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Home-screen widget: one tap opens quick entry (optionally with a category preselected); preset
 * rows save a real expense immediately through the same use case as the app and show "Saved ✓".
 */
class QuickExpenseWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val snapshot = withContext(Dispatchers.IO) { entryPoint.snapshots().quickExpense() }
        provideContent {
            GlanceTheme(colors = KharchaGlanceColors.providers) {
                QuickExpenseContent(snapshot, currentState())
            }
        }
    }

    companion object {
        val COMPACT = DpSize(110.dp, 40.dp)
        val MEDIUM = DpSize(180.dp, 110.dp)
        val LARGE = DpSize(250.dp, 180.dp)
        val SAVED_AT = longPreferencesKey("savedAt")
        val SAVED_LABEL = stringPreferencesKey("savedLabel")
        val PRESET_ID = ActionParameters.Key<String>("presetId")
        val CATEGORY_ID = ActionParameters.Key<String>(QuickEntryViewModel.ARG_CATEGORY_ID)
        val AMOUNT_MINOR = ActionParameters.Key<Long>(QuickEntryViewModel.ARG_AMOUNT_MINOR)
        const val SAVED_VISIBLE_MS = 5_000L
    }
}

class QuickExpenseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickExpenseWidget()
}

/** One-tap preset: insert, flash "Saved ✓", revert. Runs under the receiver's goAsync budget. */
class SavePresetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val presetId = parameters[QuickExpenseWidget.PRESET_ID] ?: return
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val label = withContext(Dispatchers.IO) { entryPoint.savePreset()(presetId) } ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[QuickExpenseWidget.SAVED_AT] = System.currentTimeMillis()
            prefs[QuickExpenseWidget.SAVED_LABEL] = label
        }
        QuickExpenseWidget().update(context, glanceId)
        delay(1_800)
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.remove(QuickExpenseWidget.SAVED_AT)
            prefs.remove(QuickExpenseWidget.SAVED_LABEL)
        }
        QuickExpenseWidget().update(context, glanceId)
    }
}

@Composable
private fun QuickExpenseContent(snapshot: WidgetSnapshotRepository.QuickExpenseSnapshot, state: Preferences) {
    val context = LocalContext.current
    val size = LocalSize.current
    val savedAt = state[QuickExpenseWidget.SAVED_AT] ?: 0L
    val savedLabel = state[QuickExpenseWidget.SAVED_LABEL]
    val showSaved = savedLabel != null && System.currentTimeMillis() - savedAt < QuickExpenseWidget.SAVED_VISIBLE_MS
    val colors = GlanceTheme.colors

    Column(
        modifier = GlanceModifier.fillMaxSize().background(colors.widgetBackground).cornerRadius(16.dp).padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = context.getString(R.string.app_name),
                style = TextStyle(color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp),
            )
            if (size.width >= QuickExpenseWidget.MEDIUM.width && snapshot.defaultAccountName != null) {
                Spacer(GlanceModifier.defaultWeight())
                Text(text = snapshot.defaultAccountName, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth().background(colors.primary).cornerRadius(12.dp)
                .clickable(actionStartActivity<QuickEntryActivity>()).padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "+ " + context.getString(R.string.widget_add_expense), style = TextStyle(color = colors.onPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp))
        }
        if (size.height >= QuickExpenseWidget.MEDIUM.height && snapshot.favorites.isNotEmpty()) {
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                snapshot.favorites.forEachIndexed { index, category ->
                    if (index > 0) Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = category.name,
                        style = TextStyle(color = colors.onSecondaryContainer, fontSize = 11.sp),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight().background(colors.secondaryContainer).cornerRadius(10.dp)
                            .clickable(actionStartActivity<QuickEntryActivity>(actionParametersOf(QuickExpenseWidget.CATEGORY_ID to category.id)))
                            .padding(vertical = 7.dp, horizontal = 6.dp),
                    )
                }
            }
        }
        if (size.height >= QuickExpenseWidget.LARGE.height) {
            Spacer(GlanceModifier.height(8.dp))
            if (showSaved) {
                Text(
                    text = context.getString(R.string.widget_saved) + "  " + savedLabel,
                    style = TextStyle(color = colors.onTertiaryContainer, fontWeight = FontWeight.Medium, fontSize = 12.sp),
                    modifier = GlanceModifier.fillMaxWidth().background(colors.tertiaryContainer).cornerRadius(10.dp).padding(8.dp),
                )
            } else if (snapshot.presets.isNotEmpty()) {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    snapshot.presets.forEachIndexed { index, preset ->
                        if (index > 0) Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = preset.label,
                            style = TextStyle(color = colors.onSurface, fontSize = 12.sp),
                            maxLines = 1,
                            modifier = GlanceModifier.fillMaxWidth().background(colors.surfaceVariant).cornerRadius(10.dp)
                                .clickable(
                                    if (snapshot.presetsLocked) {
                                        actionStartActivity<QuickEntryActivity>(actionParametersOf(QuickExpenseWidget.CATEGORY_ID to preset.preset.categoryId, QuickExpenseWidget.AMOUNT_MINOR to preset.preset.amountMinor))
                                    } else {
                                        actionRunCallback<SavePresetAction>(actionParametersOf(QuickExpenseWidget.PRESET_ID to preset.preset.id))
                                    },
                                )
                                .padding(vertical = 7.dp, horizontal = 10.dp),
                        )
                    }
                }
            } else {
                Text(text = context.getString(R.string.widget_presets_empty), style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 2)
            }
        }
    }
}
