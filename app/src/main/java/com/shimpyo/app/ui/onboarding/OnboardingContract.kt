package com.shimpyo.app.ui.onboarding

import com.shimpyo.app.core.mvi.UiEffect
import com.shimpyo.app.core.mvi.UiIntent
import com.shimpyo.app.core.mvi.UiState
import com.shimpyo.app.domain.model.ControlConsent

enum class ConsentItem { LIGHTS, CLIMATE, SEAT, WINDOW, DOOR }

data class OnboardingState(
    val napMinutes: Int = 15,
    val consent: ControlConsent = ControlConsent(),
    val locationGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val saving: Boolean = false,
) : UiState

sealed interface OnboardingIntent : UiIntent {
    data class ToggleConsent(val item: ConsentItem) : OnboardingIntent
    data class SetNapMinutes(val minutes: Int) : OnboardingIntent
    data object RequestLocation : OnboardingIntent
    data object RequestNotifications : OnboardingIntent
    data class PermissionResult(val permission: String, val granted: Boolean) : OnboardingIntent
    data object Finish : OnboardingIntent
}

sealed interface OnboardingEffect : UiEffect {
    data class RequestPermission(val permission: String) : OnboardingEffect
    data object Finished : OnboardingEffect
}

fun ControlConsent.toggle(item: ConsentItem): ControlConsent = when (item) {
    ConsentItem.LIGHTS -> copy(lights = !lights)
    ConsentItem.CLIMATE -> copy(climate = !climate)
    ConsentItem.SEAT -> copy(seat = !seat)
    ConsentItem.WINDOW -> copy(window = !window)
    ConsentItem.DOOR -> copy(door = !door)
}

fun ControlConsent.isOn(item: ConsentItem): Boolean = when (item) {
    ConsentItem.LIGHTS -> lights
    ConsentItem.CLIMATE -> climate
    ConsentItem.SEAT -> seat
    ConsentItem.WINDOW -> window
    ConsentItem.DOOR -> door
}
