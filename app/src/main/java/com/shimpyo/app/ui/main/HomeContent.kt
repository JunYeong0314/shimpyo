package com.shimpyo.app.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shimpyo.app.R
import com.shimpyo.app.core.designsystem.ActionRow
import com.shimpyo.app.core.designsystem.Dimens
import com.shimpyo.app.core.designsystem.Dot
import com.shimpyo.app.core.designsystem.Eyebrow
import com.shimpyo.app.core.designsystem.Fill
import com.shimpyo.app.core.designsystem.Hero
import com.shimpyo.app.core.designsystem.heroStyle
import com.shimpyo.app.core.designsystem.PrimaryButton
import com.shimpyo.app.core.designsystem.ScreenColumn
import com.shimpyo.app.core.designsystem.ShimpyoText
import com.shimpyo.app.core.designsystem.ShimpyoTheme
import com.shimpyo.app.core.designsystem.VoiceCaption
import com.shimpyo.app.engine.Connections

/** Parked home: time since the last break, when Shimpyo asks next, and one action. */
@Composable
fun HomeContent(state: MainState, onIntent: (MainIntent) -> Unit) {
    ScreenColumn {
        val driving = stringResource(R.string.home_continuous)
        val nextCheck = stringResource(R.string.home_next_check_label)
        if (ShimpyoTheme.compact) {
            Metric(driving, durationText(state.continuousMinutes), primary = true)
            Spacer(Modifier.height(Dimens.GapSmall))
            Metric(nextCheck, durationText(state.minutesToCheckIn), primary = false)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.GapLarge)) {
                Metric(driving, durationText(state.continuousMinutes), primary = true, modifier = Modifier.weight(1.4f))
                Metric(nextCheck, durationText(state.minutesToCheckIn), primary = false, modifier = Modifier.weight(1f))
            }
        }
        ConnectionNotice(state.connections)
        Fill()
        VoiceCaption(state.caption)
        ActionRow { item ->
            PrimaryButton(stringResource(R.string.home_start_rest), { onIntent(MainIntent.RestNow) }, item)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, primary: Boolean, modifier: Modifier = Modifier) {
    val c = ShimpyoTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Eyebrow(label)
        Hero(
            value,
            style = if (primary) heroStyle() else MaterialTheme.typography.displayMedium,
            color = if (primary) c.textPrimary else c.textSecondary,
        )
    }
}

/** Says something only when a car service is missing; nothing is shown while everything works. */
@Composable
internal fun ConnectionNotice(connections: Connections) {
    val missing = buildList {
        if (!connections.vehicle) add(R.string.sdk_vehicle)
        if (!connections.navi) add(R.string.sdk_navi)
        if (!connections.voice) add(R.string.sdk_voice)
        if (!connections.location) add(R.string.sdk_location)
    }.map { stringResource(it) }
    if (missing.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(ShimpyoTheme.colors.negative, 12.dp)
        Spacer(Modifier.width(Dimens.GapSmall))
        ShimpyoText(
            stringResource(R.string.connection_lost, missing.joinToString(" · ")),
            style = MaterialTheme.typography.bodyMedium,
            color = ShimpyoTheme.colors.textSecondary,
        )
    }
}
