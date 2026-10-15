package com.attiqtherehman.kharcha.widget.balance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.attiqtherehman.kharcha.MainActivity
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.MoneyFormatter
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.widget.KharchaGlanceColors
import com.attiqtherehman.kharcha.widget.WidgetEntryPoint
import com.attiqtherehman.kharcha.widget.WidgetSnapshotRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Optional widget showing total available, cash and bank balances for the accounts chosen in its configuration. */
class AccountBalanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val snapshot = withContext(Dispatchers.IO) {
            val config = entryPoint.widgetConfig().get(appWidgetId)
            entryPoint.snapshots().balances(config)
        }
        provideContent {
            GlanceTheme(colors = KharchaGlanceColors.providers) {
                BalanceContent(snapshot, appWidgetId)
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 60.dp)
        val WIDE = DpSize(250.dp, 110.dp)
    }
}

class AccountBalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AccountBalanceWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        runBlocking { entryPoint.widgetConfig().delete(appWidgetIds) }
    }
}

@Composable
private fun BalanceContent(snapshot: WidgetSnapshotRepository.BalanceSnapshot, appWidgetId: Int) {
    val context = LocalContext.current
    val size = LocalSize.current
    val colors = GlanceTheme.colors
    fun fmt(m: Money): String = if (snapshot.hideAmounts) "••••" else MoneyFormatter.format(m, snapshot.currency, snapshot.grouping, hideZeroFraction = true)

    val openApp = actionStartActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
    val configure = actionStartActivity(
        Intent(context, AccountBalanceConfigActivity::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
    )

    Column(
        modifier = GlanceModifier.fillMaxSize().background(colors.widgetBackground).cornerRadius(16.dp).padding(12.dp).clickable(openApp),
    ) {
        Text(text = context.getString(R.string.widget_total_available), style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp))
        Text(text = fmt(snapshot.total), style = TextStyle(color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp), maxLines = 1)
        if (size.width >= AccountBalanceWidget.WIDE.width) {
            Spacer(GlanceModifier.height(6.dp))
            val cash = snapshot.lines.filter { it.type == AccountType.CASH }
            val banks = snapshot.lines.filter { it.type == AccountType.BANK }
            (cash + banks).take(3).forEach { line ->
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = line.name, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    Text(text = fmt(line.balance), style = TextStyle(color = colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = context.getString(R.string.widget_updated_at, DateFormats.time(snapshot.updatedAt)), style = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp), modifier = GlanceModifier.defaultWeight())
                Text(
                    text = if (snapshot.configured) "⚙" else context.getString(R.string.widget_tap_to_set_up),
                    style = TextStyle(color = colors.primary, fontSize = 11.sp),
                    modifier = GlanceModifier.clickable(configure).padding(start = 8.dp),
                )
            }
        }
    }
}
