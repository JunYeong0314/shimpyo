package com.shimpyo.app.ui.main

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shimpyo.app.R
import com.shimpyo.app.core.designsystem.ActionRow
import com.shimpyo.app.core.designsystem.Dimens
import com.shimpyo.app.core.designsystem.Eyebrow
import com.shimpyo.app.core.designsystem.Fill
import com.shimpyo.app.core.designsystem.FitText
import com.shimpyo.app.core.designsystem.Hero
import com.shimpyo.app.core.designsystem.headlineStyle
import com.shimpyo.app.core.designsystem.PrimaryButton
import com.shimpyo.app.core.designsystem.ScreenColumn
import com.shimpyo.app.core.designsystem.SecondaryButton
import com.shimpyo.app.core.designsystem.ShimpyoText
import com.shimpyo.app.core.designsystem.ShimpyoTheme
import com.shimpyo.app.core.designsystem.VoiceCaption
import com.shimpyo.app.domain.model.RestArea
import com.shimpyo.app.engine.Phase

/**
 * Distraction-optimized driving surface: one number or one place, at most two large buttons.
 * No animation, no scrolling, no text input. The conversation itself happens by voice.
 *
 * TODO(policy): Drive this surface from CarUxRestrictionsManager (official restriction state per region)
 *  instead of the gear position alone.
 */
@Composable
fun DrivingContent(state: MainState, onIntent: (MainIntent) -> Unit) {
    ScreenColumn {
        when (val phase = state.phase) {
            is Phase.Proposing -> Proposal(state, phase, onIntent)
            is Phase.HeadingToRest -> Heading(state, phase)
            is Phase.WaitingForPark -> Arrived(state, phase)
            else -> Monitoring(state, onIntent)
        }
    }
}

@Composable
private fun ColumnScope.Monitoring(state: MainState, onIntent: (MainIntent) -> Unit) {
    HeaderRow(stringResource(R.string.home_continuous), state.listening)
    Hero(durationText(state.continuousMinutes))
    state.suggestedStop?.let { stop ->
        Spacer(Modifier.height(Dimens.Gap))
        Eyebrow(stringResource(R.string.driving_suggested))
        FitText(
            listOf(stop.area.localizedName(), distanceText(stop.distanceKm)).filter { it.isNotEmpty() }.joinToString(" · "),
            style = MaterialTheme.typography.titleLarge,
            color = ShimpyoTheme.colors.textPrimary,
        )
    }
    Fill()
    VoiceCaption(state.caption)
    ActionRow { item ->
        PrimaryButton(stringResource(R.string.driving_rest_now), { onIntent(MainIntent.RestNow) }, item)
    }
}

@Composable
private fun ColumnScope.Proposal(state: MainState, phase: Phase.Proposing, onIntent: (MainIntent) -> Unit) {
    HeaderRow(stringResource(R.string.driving_suggested), state.listening)
    Hero(phase.area.localizedName(), style = headlineStyle())
    ShimpyoText(
        stopDetails(phase.area, phase.distanceKm),
        style = MaterialTheme.typography.titleLarge,
        color = ShimpyoTheme.colors.textSecondary,
    )
    Fill()
    VoiceCaption(state.caption)
    ActionRow { item ->
        PrimaryButton(stringResource(R.string.proposal_yes), { onIntent(MainIntent.AnswerProposal(true)) }, item)
        SecondaryButton(stringResource(R.string.proposal_no), { onIntent(MainIntent.AnswerProposal(false)) }, item)
    }
}

@Composable
private fun ColumnScope.Heading(state: MainState, phase: Phase.HeadingToRest) {
    HeaderRow(stringResource(R.string.driving_heading_label), state.listening)
    Hero(phase.area.localizedName(), style = headlineStyle())
    Fill()
    VoiceCaption(state.caption)
}

/** At the stop: no distance any more, only the one thing left to do (shift to P). */
@Composable
private fun ColumnScope.Arrived(state: MainState, phase: Phase.WaitingForPark) {
    HeaderRow(stringResource(R.string.driving_arrived_label), state.listening)
    Hero(phase.area.localizedName(), style = headlineStyle())
    ShimpyoText(
        stringResource(R.string.driving_arrived_hint),
        style = MaterialTheme.typography.titleLarge,
        color = ShimpyoTheme.colors.textSecondary,
    )
    Fill()
    VoiceCaption(state.caption)
}

/** "14.0 km · Café" */
@Composable
private fun stopDetails(area: RestArea, km: Double?): String {
    val cafe = stringResource(R.string.proposal_cafe)
    return listOfNotNull(km?.let { distanceText(it) }, cafe.takeIf { area.hasCafe }).joinToString(" · ")
}
