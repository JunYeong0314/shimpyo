package com.shimpyo.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.shimpyo.app.core.mvi.MviViewModel
import com.shimpyo.app.data.location.LocationGateway
import com.shimpyo.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val location: LocationGateway,
) : MviViewModel<OnboardingState, OnboardingIntent, OnboardingEffect>(OnboardingState()) {

    init {
        setState { copy(locationGranted = location.hasPermission(), notificationsGranted = notificationsGranted()) }
    }

    override suspend fun handleIntent(intent: OnboardingIntent) {
        when (intent) {
            is OnboardingIntent.ToggleConsent -> setState { copy(consent = consent.toggle(intent.item)) }
            is OnboardingIntent.SetNapMinutes -> setState { copy(napMinutes = intent.minutes) }
            // TODO(spec): FUSED_LOCATION has no localized label on the emulator, so the system dialog reads
            //  "unknown action". Explain the purpose on our own screen before the system dialog appears.
            OnboardingIntent.RequestLocation -> sendEffect(OnboardingEffect.RequestPermission(LocationGateway.PERMISSION))
            OnboardingIntent.RequestNotifications ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    sendEffect(OnboardingEffect.RequestPermission(Manifest.permission.POST_NOTIFICATIONS))
                } else {
                    setState { copy(notificationsGranted = true) }
                }
            is OnboardingIntent.PermissionResult -> {
                if (intent.permission == LocationGateway.PERMISSION) {
                    setState { copy(locationGranted = intent.granted) }
                    if (intent.granted) location.start()
                } else {
                    setState { copy(notificationsGranted = intent.granted) }
                }
            }
            OnboardingIntent.Finish -> {
                setState { copy(saving = true) }
                settings.completeOnboarding(currentState.napMinutes, currentState.consent)
                sendEffect(OnboardingEffect.Finished)
            }
        }
    }

    private fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
