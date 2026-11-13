package com.shimpyo.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shimpyo.app.R
import com.shimpyo.app.core.designsystem.Chip
import com.shimpyo.app.core.designsystem.CompactWidth
import com.shimpyo.app.core.designsystem.Dimens
import com.shimpyo.app.core.designsystem.Eyebrow
import com.shimpyo.app.core.designsystem.Fill
import com.shimpyo.app.core.designsystem.FitText
import com.shimpyo.app.core.designsystem.HairlineDivider
import com.shimpyo.app.core.designsystem.LocalCompact
import com.shimpyo.app.core.designsystem.PrimaryButton
import com.shimpyo.app.core.designsystem.ShimpyoSurface
import com.shimpyo.app.core.designsystem.ShimpyoText
import com.shimpyo.app.core.designsystem.ShimpyoTheme
import com.shimpyo.app.core.designsystem.screenGutter
import com.shimpyo.app.service.ShimpyoMonitorService

@Composable
fun OnboardingScreen(onFinished: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingPermission by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPermission?.let { vm.dispatch(OnboardingIntent.PermissionResult(it, granted)) }
        pendingPermission = null
    }
    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is OnboardingEffect.RequestPermission -> {
                    pendingPermission = effect.permission
                    launcher.launch(effect.permission)
                }
                OnboardingEffect.Finished -> {
                    ShimpyoMonitorService.start(context)
                    onFinished()
                }
            }
        }
    }
    OnboardingContent(state = state, onIntent = vm::dispatch)
}

/** Wide: the promise on the left, the few settings on the right. Split window: one scrolling column. */
@Composable
private fun OnboardingContent(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(ShimpyoTheme.colors.background)) {
        CompositionLocalProvider(LocalCompact provides (maxWidth < CompactWidth)) {
            val start = @Composable { modifier: Modifier ->
                PrimaryButton(
                    text = stringResource(R.string.onboarding_start),
                    onClick = { onIntent(OnboardingIntent.Finish) },
                    modifier = modifier,
                    enabled = !state.saving,
                )
            }
            if (ShimpyoTheme.compact) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(screenGutter()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.GapLarge),
                ) {
                    Intro()
                    Settings(state, onIntent)
                    start(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize().padding(screenGutter()),
                    horizontalArrangement = Arrangement.spacedBy(72.dp),
                ) {
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Intro()
                        Fill()
                        start(Modifier.width(Dimens.ActionWidth))
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Dimens.GapLarge),
                    ) {
                        Settings(state, onIntent)
                    }
                }
            }
        }
    }
}

@Composable
private fun Intro() {
    val c = ShimpyoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
        FitText(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineLarge, color = c.textPrimary)
        ShimpyoText(stringResource(R.string.onboarding_body), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
    }
}

@Composable
private fun Settings(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    val c = ShimpyoTheme.colors
    Group(stringResource(R.string.onboarding_consent_title), note = stringResource(R.string.onboarding_consent_note)) {
        ConsentItem.entries.forEachIndexed { i, item ->
            if (i > 0) HairlineDivider()
            SettingRow(stringResource(item.labelRes()), onClick = { onIntent(OnboardingIntent.ToggleConsent(item)) }) {
                Switch(
                    checked = state.consent.isOn(item),
                    onCheckedChange = { onIntent(OnboardingIntent.ToggleConsent(item)) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = c.brand,
                        checkedThumbColor = c.onBrand,
                        uncheckedTrackColor = c.surfaceMuted,
                        uncheckedBorderColor = c.outline,
                        uncheckedThumbColor = c.textTertiary,
                    ),
                )
            }
        }
    }
    Group(stringResource(R.string.onboarding_permissions_title)) {
        PermissionRow(stringResource(R.string.permission_location), state.locationGranted) { onIntent(OnboardingIntent.RequestLocation) }
        HairlineDivider()
        PermissionRow(stringResource(R.string.permission_notifications), state.notificationsGranted) {
            onIntent(OnboardingIntent.RequestNotifications)
        }
    }
    Group(stringResource(R.string.nap_length_title)) {
        Row(modifier = Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
            listOf(10, 15, 20).forEach { minutes ->
                Chip(
                    text = stringResource(R.string.duration_m, minutes),
                    onClick = { onIntent(OnboardingIntent.SetNapMinutes(minutes)) },
                    modifier = Modifier.weight(1f),
                    selected = state.napMinutes == minutes,
                )
            }
        }
    }
}

/** A settings group: quiet label, white surface, optional fine print under it. */
@Composable
private fun Group(label: String, note: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
        Eyebrow(label)
        ShimpyoSurface(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
            Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp)) { content() }
        }
        note?.let {
            ShimpyoText(it, style = MaterialTheme.typography.labelSmall, color = ShimpyoTheme.colors.textTertiary)
        }
    }
}

/** 88dp row; the whole row is the touch target when it has an action (Android for Cars: ≥ 76dp). */
@Composable
private fun SettingRow(label: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .let { if (onClick != null) it.clickable(role = Role.Switch, onClick = onClick) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimpyoText(label, style = MaterialTheme.typography.bodyLarge, color = ShimpyoTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Dimens.Gap))
        trailing()
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onRequest: () -> Unit) {
    SettingRow(label, onClick = onRequest.takeUnless { granted }) {
        if (granted) {
            FitText(stringResource(R.string.permission_granted), style = MaterialTheme.typography.labelMedium, color = ShimpyoTheme.colors.textTertiary)
        } else {
            Chip(text = stringResource(R.string.permission_grant), onClick = onRequest, height = 64.dp)
        }
    }
}

private fun ConsentItem.labelRes(): Int = when (this) {
    ConsentItem.LIGHTS -> R.string.consent_lights
    ConsentItem.CLIMATE -> R.string.consent_climate
    ConsentItem.SEAT -> R.string.consent_seat
    ConsentItem.WINDOW -> R.string.consent_window
    ConsentItem.DOOR -> R.string.consent_door
}
