package com.shimpyo.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.shimpyo.app.domain.model.ControlConsent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class UserSettings(
    val onboardingDone: Boolean = false,
    val napMinutes: Int = 15,
    val consent: ControlConsent = ControlConsent(),
    /** Typical time to start answering by voice, measured during onboarding (ms). */
    val baselineLatencyMs: Long = 1_500,
    val lastRestEndedAtMillis: Long? = null,
)

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<UserSettings> = dataStore.data.map { p ->
        UserSettings(
            onboardingDone = p[ONBOARDING_DONE] ?: false,
            napMinutes = p[NAP_MINUTES] ?: 15,
            consent = ControlConsent(
                lights = p[CONSENT_LIGHTS] ?: true,
                climate = p[CONSENT_CLIMATE] ?: true,
                seat = p[CONSENT_SEAT] ?: true,
                window = p[CONSENT_WINDOW] ?: true,
                door = p[CONSENT_DOOR] ?: true,
            ),
            baselineLatencyMs = p[BASELINE_LATENCY] ?: 1_500,
            lastRestEndedAtMillis = p[LAST_REST_ENDED],
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun completeOnboarding(napMinutes: Int, consent: ControlConsent) {
        dataStore.edit { p ->
            p[ONBOARDING_DONE] = true
            p[NAP_MINUTES] = napMinutes
            p[CONSENT_LIGHTS] = consent.lights
            p[CONSENT_CLIMATE] = consent.climate
            p[CONSENT_SEAT] = consent.seat
            p[CONSENT_WINDOW] = consent.window
            p[CONSENT_DOOR] = consent.door
        }
    }

    suspend fun setLastRestEnded(millis: Long) {
        dataStore.edit { it[LAST_REST_ENDED] = millis }
    }

    private companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val NAP_MINUTES = intPreferencesKey("nap_minutes")
        val CONSENT_LIGHTS = booleanPreferencesKey("consent_lights")
        val CONSENT_CLIMATE = booleanPreferencesKey("consent_climate")
        val CONSENT_SEAT = booleanPreferencesKey("consent_seat")
        val CONSENT_WINDOW = booleanPreferencesKey("consent_window")
        val CONSENT_DOOR = booleanPreferencesKey("consent_door")
        val BASELINE_LATENCY = longPreferencesKey("baseline_latency_ms")
        val LAST_REST_ENDED = longPreferencesKey("last_rest_ended_at")
    }
}
