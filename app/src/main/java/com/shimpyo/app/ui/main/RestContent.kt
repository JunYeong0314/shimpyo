package com.shimpyo.app.ui.main

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shimpyo.app.R
import com.shimpyo.app.core.designsystem.ActionButton
import com.shimpyo.app.core.designsystem.ActionRow
import com.shimpyo.app.core.designsystem.Dimens
import com.shimpyo.app.core.designsystem.Dot
import com.shimpyo.app.core.designsystem.Eyebrow
import com.shimpyo.app.core.designsystem.Fill
import com.shimpyo.app.core.designsystem.FitText
import com.shimpyo.app.core.designsystem.Hero
import com.shimpyo.app.core.designsystem.headlineStyle
import com.shimpyo.app.core.designsystem.NapScene
import com.shimpyo.app.core.designsystem.PrimaryButton
import com.shimpyo.app.core.designsystem.ScreenColumn
import com.shimpyo.app.core.designsystem.SecondaryButton
import com.shimpyo.app.core.designsystem.ShimpyoSurface
import com.shimpyo.app.core.designsystem.ShimpyoText
import com.shimpyo.app.core.designsystem.ShimpyoTheme
import com.shimpyo.app.core.designsystem.VoiceCaption
import com.shimpyo.app.core.designsystem.screenGutter
import com.shimpyo.app.domain.model.RestMode
import com.shimpyo.app.engine.ConditionResult
import com.shimpyo.app.engine.Phase

/** Parked-only rest flow: choose → rest → wake → condition check. */
@Composable
fun RestContent(state: MainState, onIntent: (MainIntent) -> Unit) {
    when (val phase = state.phase) {
        Phase.ChoosingMode -> ChooseMode(state, onIntent)
        is Phase.Resting -> Resting(state, phase, onIntent)
        is Phase.ConditionCheck -> ConditionCheck(state, phase, onIntent)
        else -> Unit
    }
}

// region choose

private data class RestOption(val mode: RestMode, val minutes: Int, @StringRes val name: Int, @StringRes val description: Int)

/** Matches ShimpyoEngine.STRETCH_SEC. */
private const val STRETCH_MINUTES = 5

@Composable
private fun ChooseMode(state: MainState, onIntent: (MainIntent) -> Unit) {
    val options = listOf(
        RestOption(RestMode.CAR_NAP, state.napMinutes, R.string.mode_name_car_nap, R.string.mode_car_nap_desc),
        RestOption(RestMode.COFFEE_NAP, state.napMinutes, R.string.mode_name_coffee_nap, R.string.mode_coffee_nap_desc),
        RestOption(RestMode.STRETCH, STRETCH_MINUTES, R.string.mode_name_stretch, R.string.mode_stretch_desc),
    )
    ScreenColumn {
        HeaderRow(stringResource(R.string.rest_choose_title), state.listening, title = true)
        if (ShimpyoTheme.compact) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                options.forEach { option ->
                    OptionRow(option, Modifier.weight(1f).fillMaxWidth()) { onIntent(MainIntent.ChooseMode(option.mode)) }
                }
            }
        } else {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                options.forEach { option ->
                    OptionTile(option, Modifier.weight(1f).fillMaxHeight()) { onIntent(MainIntent.ChooseMode(option.mode)) }
                }
            }
        }
        VoiceCaption(state.caption)
    }
}

/** Wide window: the duration is the headline of each option. */
@Composable
private fun OptionTile(option: RestOption, modifier: Modifier, onClick: () -> Unit) {
    val c = ShimpyoTheme.colors
    ShimpyoSurface(modifier = modifier, padding = 36.dp, onClick = onClick) {
        Hero(durationText(option.minutes), style = headlineStyle(), color = c.brandText)
        Fill()
        FitText(stringResource(option.name), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        ShimpyoText(
            stringResource(option.description),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            minLines = 2,
            maxLines = 2,
        )
    }
}

/** Split window: the same option as a row. */
@Composable
private fun OptionRow(option: RestOption, modifier: Modifier, onClick: () -> Unit) {
    val c = ShimpyoTheme.colors
    ShimpyoSurface(modifier = modifier, padding = 32.dp, onClick = onClick) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Hero(
                durationText(option.minutes),
                style = MaterialTheme.typography.displayMedium,
                color = c.brandText,
                modifier = Modifier.width(220.dp),
            )
            Spacer(Modifier.width(Dimens.Gap))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FitText(stringResource(option.name), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
                ShimpyoText(stringResource(option.description), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 2)
            }
        }
    }
}

// endregion

// region rest

/**
 * The screen follows the cabin: almost black while resting, then Light Sand like a sunrise while waking.
 * Parked only, so the slow color change is allowed here.
 */
@Composable
private fun Resting(state: MainState, phase: Phase.Resting, onIntent: (MainIntent) -> Unit) {
    val waking = phase.waking
    val background by animateColorAsState(if (waking) NapScene.Sunrise else NapScene.Night, tween(SCENE_MS), label = "napBackground")
    val text by animateColorAsState(if (waking) NapScene.SunriseText else NapScene.NightText, tween(SCENE_MS), label = "napText")
    val dim by animateColorAsState(if (waking) NapScene.SunriseTextDim else NapScene.NightTextDim, tween(SCENE_MS), label = "napDim")
    val button by animateColorAsState(if (waking) NapScene.SunriseButton else NapScene.NightButton, tween(SCENE_MS), label = "napButton")

    Column(
        modifier = Modifier.fillMaxSize().background(background).padding(screenGutter()),
        verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
    ) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(if (waking) R.string.waking_title else R.string.resting_title), Modifier.weight(1f), color = dim)
            if (state.companionsOnBoard) Eyebrow(stringResource(R.string.family_mode_badge), color = dim)
        }
        Fill()
        FitText(
            countdownText(phase.remainingSec),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 220.sp,
                lineHeight = 230.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-4).sp,
            ),
            color = text,
            textAlign = TextAlign.Center,
            minFontSize = 64.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Fill()
        VoiceCaption(state.caption, color = dim, accent = dim)
        ActionRow { item ->
            if (phase.mode != RestMode.STRETCH) {
                ActionButton(stringResource(R.string.resting_wake_now), { onIntent(MainIntent.WakeNow) }, item, container = button, content = text)
            }
            ActionButton(stringResource(R.string.resting_end), { onIntent(MainIntent.EndRest) }, item, container = Color.Transparent, content = dim)
        }
    }
}

private const val SCENE_MS = 2400

// endregion

// region check

@Composable
private fun ConditionCheck(state: MainState, phase: Phase.ConditionCheck, onIntent: (MainIntent) -> Unit) {
    val c = ShimpyoTheme.colors
    ScreenColumn {
        HeaderRow(stringResource(R.string.check_title), state.listening, title = true)
        Fill()
        when (phase.result) {
            null -> ShimpyoText(stringResource(R.string.check_in_progress), style = MaterialTheme.typography.headlineLarge, color = c.textSecondary)
            ConditionResult.GOOD -> CheckResult(stringResource(R.string.check_good), c.positive)
            ConditionResult.NEEDS_MORE -> CheckResult(stringResource(R.string.check_more), c.caution)
        }
        Fill()
        VoiceCaption(state.caption)
        if (phase.result != null) {
            ActionRow { item ->
                PrimaryButton(stringResource(R.string.check_go), { onIntent(MainIntent.Depart) }, item)
                SecondaryButton(stringResource(R.string.check_five_more), { onIntent(MainIntent.FiveMoreMinutes) }, item)
            }
        }
        ShimpyoText(stringResource(R.string.check_disclaimer), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
    }
}

/** The dot sits on the first line, also when a longer translation wraps. */
@Composable
private fun CheckResult(text: String, accent: Color) {
    val style = MaterialTheme.typography.displayMedium
    val dot = 20.dp
    val firstLine = with(LocalDensity.current) { style.lineHeight.toDp() }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = (firstLine - dot) / 2)) { Dot(accent, dot) }
        Spacer(Modifier.width(Dimens.Gap))
        ShimpyoText(text, style = style, color = ShimpyoTheme.colors.textPrimary)
    }
}

// endregion
