package com.shimpyo.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shimpyo.app.R
import com.shimpyo.app.core.designsystem.Chip
import com.shimpyo.app.core.designsystem.CompactWidth
import com.shimpyo.app.core.designsystem.Dimens
import com.shimpyo.app.core.designsystem.Dot
import com.shimpyo.app.core.designsystem.Eyebrow
import com.shimpyo.app.core.designsystem.FitText
import com.shimpyo.app.core.designsystem.HairlineDivider
import com.shimpyo.app.core.designsystem.LocalCompact
import com.shimpyo.app.core.designsystem.ShimpyoTheme
import com.shimpyo.app.domain.model.RestArea
import com.shimpyo.app.service.ShimpyoMonitorService

@Composable
fun MainScreen(vm: MainViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { ShimpyoMonitorService.start(context) }
    MainContent(state = state, onIntent = vm::dispatch)
}

/** The app above, the demo strip (debug builds) below. Screens adapt to a full or a split window. */
@Composable
fun MainContent(state: MainState, onIntent: (MainIntent) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(ShimpyoTheme.colors.background)) {
        CompositionLocalProvider(LocalCompact provides (maxWidth < CompactWidth)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (state.surface) {
                        Surface.HOME -> HomeContent(state, onIntent)
                        Surface.DRIVING -> DrivingContent(state, onIntent)
                        Surface.REST -> RestContent(state, onIntent)
                    }
                }
                if (state.demoControls) DemoStrip(state, onIntent)
            }
        }
    }
}

/**
 * Demo controls for the contest video and emulator testing (debug builds only): a strip under the app.
 * One row on a full window, two rows (controls, answers) in a split window.
 */
@Composable
private fun DemoStrip(state: MainState, onIntent: (MainIntent) -> Unit) {
    val controls = @Composable {
        StripLabel(stringResource(R.string.debug_title))
        listOf(1, 60, 300).forEach { scale ->
            DemoChip(stringResource(R.string.debug_time_scale, scale), selected = state.timeScale == scale) {
                onIntent(MainIntent.SetTimeScale(scale))
            }
        }
        StripGap()
        DemoChip(stringResource(R.string.debug_add_30)) { onIntent(MainIntent.AddDrivingMinutes(30)) }
        DemoChip(stringResource(R.string.debug_check_in)) { onIntent(MainIntent.TriggerCheckIn) }
        DemoChip(stringResource(R.string.debug_arrive)) { onIntent(MainIntent.SimulateArrival) }
    }
    val answers = @Composable {
        StripLabel(stringResource(R.string.debug_answers))
        listOf("7", "3", "응", "아니", "카냅", "커피냅", "스트레칭").forEach { answer ->
            DemoChip(answer) { onIntent(MainIntent.Answer(answer)) }
        }
        // The correct answer of the running condition check.
        state.expectedAnswer?.let { answer -> DemoChip(answer, selected = true) { onIntent(MainIntent.Answer(answer)) } }
    }
    Column(Modifier.fillMaxWidth().background(ShimpyoTheme.colors.surface).padding(bottom = 12.dp)) {
        HairlineDivider()
        if (ShimpyoTheme.compact) {
            StripRow { controls() }
            StripRow { answers() }
        } else {
            StripRow {
                controls()
                StripGap()
                answers()
            }
        }
    }
}

@Composable
private fun StripRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun DemoChip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Chip(text = text, onClick = onClick, selected = selected, height = 56.dp, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun StripLabel(text: String) {
    FitText(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = ShimpyoTheme.colors.textTertiary,
        modifier = Modifier.padding(end = 8.dp),
    )
}

@Composable
private fun StripGap() = Spacer(Modifier.width(16.dp))

// region shared pieces

/** A screen's eyebrow (or title) with the listening pill on the trailing edge. Fixed height: nothing jumps. */
@Composable
internal fun HeaderRow(text: String, listening: Boolean, title: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
        if (title) {
            FitText(
                text,
                style = MaterialTheme.typography.headlineLarge,
                color = ShimpyoTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        } else {
            Eyebrow(text, Modifier.weight(1f))
        }
        if (listening) {
            Spacer(Modifier.width(Dimens.Gap))
            ListeningPill()
        }
    }
}

/** Shown only while the microphone is open. Static (no animation), so it is safe on the driving surface. */
@Composable
private fun ListeningPill() {
    val c = ShimpyoTheme.colors
    Row(
        modifier = Modifier.clip(CircleShape).background(c.brandTint).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(c.brand, 12.dp)
        Spacer(Modifier.width(12.dp))
        FitText(stringResource(R.string.driving_listening), style = MaterialTheme.typography.labelMedium, color = c.brandText)
    }
}

@Composable
fun durationText(minutes: Int): String = when {
    minutes >= 60 && minutes % 60 == 0 -> stringResource(R.string.duration_h, minutes / 60)
    minutes >= 60 -> stringResource(R.string.duration_hm, minutes / 60, minutes % 60)
    else -> stringResource(R.string.duration_m, minutes)
}

@Composable
fun countdownText(seconds: Int): String = stringResource(R.string.countdown_mmss, seconds / 60, seconds % 60)

@Composable
fun distanceText(km: Double?): String = km?.let { stringResource(R.string.distance_km, it) } ?: ""

@Composable
fun RestArea.localizedName(): String = displayName(LocalConfiguration.current.locales[0]?.language == "ko")

// endregion
