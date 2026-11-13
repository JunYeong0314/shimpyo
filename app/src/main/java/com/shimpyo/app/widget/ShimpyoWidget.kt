package com.shimpyo.app.widget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.shimpyo.app.MainActivity
import com.shimpyo.app.R
import com.shimpyo.app.core.designsystem.DarkColors
import com.shimpyo.app.core.designsystem.LightColors
import com.shimpyo.app.core.designsystem.ShimpyoColors
import com.shimpyo.app.core.designsystem.keepAll
import com.shimpyo.app.engine.EngineState
import com.shimpyo.app.engine.Phase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Home widget following the Pleos widget anatomy (title/action area + information area):
 * continuous driving time, what Shimpyo is doing, and a one-tap "Take a break".
 *
 * TODO(spec): Third-party AppWidget hosting on the Pleos Connect home is undocumented; verify on a
 *  Pleos launcher. The widget is harmless where hosting is not available.
 */
class ShimpyoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore.read(context)
        provideContent { WidgetContent(context, snapshot) }
    }
}

class ShimpyoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShimpyoWidget()
}

@Composable
private fun WidgetContent(context: Context, s: WidgetSnapshot) {
    val restNow = Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_REST_NOW)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val status = when (s.status) {
        WidgetStatus.RESTING -> context.getString(R.string.widget_status_resting, minutes(context, s.restRemainingMin))
        WidgetStatus.HEADING -> context.getString(R.string.widget_status_heading, s.headingName.orEmpty())
        WidgetStatus.MONITORING -> context.getString(R.string.widget_status_monitoring, minutes(context, s.minutesToCheckIn))
    }
    Column(
        modifier = GlanceModifier.fillMaxSize().background(color(Role.SURFACE)).cornerRadius(16.dp).padding(20.dp),
    ) {
        Text(
            text = context.getString(R.string.app_name),
            style = TextStyle(color = color(Role.TEXT_SECONDARY), fontSize = 16.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        Text(
            text = minutes(context, s.continuousMinutes),
            style = TextStyle(color = color(Role.TEXT_PRIMARY), fontSize = 34.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Text(text = status.keepAll(), style = TextStyle(color = color(Role.TEXT_SECONDARY), fontSize = 16.sp), maxLines = 2)
        Spacer(GlanceModifier.height(12.dp))
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(56.dp)
                .background(color(Role.BRAND))
                .cornerRadius(12.dp)
                .clickable(actionStartActivity(restNow)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = context.getString(R.string.widget_rest_now),
                style = TextStyle(color = color(Role.ON_BRAND), fontSize = 18.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

private enum class Role { SURFACE, TEXT_PRIMARY, TEXT_SECONDARY, BRAND, ON_BRAND }

/** Same roles as the app theme, switching with the car's day/night mode. */
private fun color(role: Role): ColorProvider {
    fun pick(c: ShimpyoColors) = when (role) {
        Role.SURFACE -> c.surface
        Role.TEXT_PRIMARY -> c.textPrimary
        Role.TEXT_SECONDARY -> c.textSecondary
        Role.BRAND -> c.brand
        Role.ON_BRAND -> c.onBrand
    }
    return ColorProvider(day = pick(LightColors), night = pick(DarkColors))
}

private fun minutes(context: Context, total: Int): String = when {
    total >= 60 && total % 60 == 0 -> context.getString(R.string.duration_h, total / 60)
    total >= 60 -> context.getString(R.string.duration_hm, total / 60, total % 60)
    else -> context.getString(R.string.duration_m, total)
}

enum class WidgetStatus { MONITORING, HEADING, RESTING }

data class WidgetSnapshot(
    val continuousMinutes: Int = 0,
    val minutesToCheckIn: Int = 0,
    val status: WidgetStatus = WidgetStatus.MONITORING,
    val restRemainingMin: Int = 0,
    val headingName: String? = null,
)

object WidgetSnapshotStore {
    private const val PREFS = "shimpyo_widget"

    fun write(context: Context, s: WidgetSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("continuous", s.continuousMinutes)
            .putInt("toCheckIn", s.minutesToCheckIn)
            .putString("status", s.status.name)
            .putInt("restRemaining", s.restRemainingMin)
            .putString("heading", s.headingName)
            .apply()
    }

    fun read(context: Context): WidgetSnapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WidgetSnapshot(
            continuousMinutes = p.getInt("continuous", 0),
            minutesToCheckIn = p.getInt("toCheckIn", 0),
            status = runCatching { WidgetStatus.valueOf(p.getString("status", null) ?: "") }.getOrDefault(WidgetStatus.MONITORING),
            restRemainingMin = p.getInt("restRemaining", 0),
            headingName = p.getString("heading", null),
        )
    }
}

/** Pushes engine state to the widget, at most every 30 s unless what it shows changes. */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastKey: WidgetSnapshot? = null
    private var lastAt = 0L

    fun publish(state: EngineState) {
        val korean = context.resources.configuration.locales[0]?.language == "ko"
        val phase = state.phase
        val snapshot = WidgetSnapshot(
            continuousMinutes = state.continuousDrivingSec / 60,
            minutesToCheckIn = ((state.nextCheckInAtSec - state.continuousDrivingSec) / 60).coerceAtLeast(0),
            status = when (phase) {
                is Phase.Resting -> WidgetStatus.RESTING
                is Phase.HeadingToRest -> WidgetStatus.HEADING
                else -> WidgetStatus.MONITORING
            },
            restRemainingMin = (phase as? Phase.Resting)?.remainingSec?.div(60) ?: 0,
            headingName = (phase as? Phase.HeadingToRest)?.area?.displayName(korean),
        )
        val now = SystemClock.elapsedRealtime()
        if (snapshot == lastKey && now - lastAt < MIN_INTERVAL_MS) return
        lastKey = snapshot
        lastAt = now
        WidgetSnapshotStore.write(context, snapshot)
        scope.launch { runCatching { ShimpyoWidget().updateAll(context) } }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 30_000L
    }
}
