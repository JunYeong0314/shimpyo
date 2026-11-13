package com.shimpyo.app.domain.logic

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.shimpyo.app.core.designsystem.SceneColor
import com.shimpyo.app.data.vehicle.VehicleGateway
import com.shimpyo.app.domain.model.CabinSnapshot
import com.shimpyo.app.domain.model.ControlConsent
import com.shimpyo.app.domain.model.FanDirection
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the cabin into a nap space and back. Every change is gated by the driver's consent, runs only
 * while parked (the engine guarantees gear P), and is undone from the snapshot taken first.
 *
 * Safety choices:
 *  - Fresh air is forced (recirculation off) so CO₂ does not build up while sleeping.
 *  - The driver window opens slightly only when it is not raining.
 *  - With passengers on board only the driver zone changes.
 *
 * TODO(safety): Engine-on naps in enclosed spaces are dangerous for combustion engines (CO). Detect
 *  underground parking and the powertrain (EV/HEV/ICE) before enabling climate control.
 * TODO(policy): Local idling limits apply to combustion engines; surface them to the driver.
 * TODO(spec): The snapshot reads only the driver zone and lives in memory. Persist it so settings can be
 *  restored after process death, and capture every ambient zone.
 */
@Singleton
class CabinOrchestrator @Inject constructor(
    private val vehicle: VehicleGateway,
) {
    private var snapshot: CabinSnapshot? = null
    private var allZones = true

    val hasSnapshot: Boolean get() = snapshot != null

    suspend fun capture(companionsOnBoard: Boolean) {
        allZones = !companionsOnBoard
        if (snapshot == null) snapshot = vehicle.readCabin()
    }

    /** Ambient light follows the breathing guide. */
    suspend fun breathe(inhale: Boolean, consent: ControlConsent) {
        if (!consent.lights) return
        vehicle.setAmbient(brightness = if (inhale) 45 else 12, argb = SceneColor.Dusk.toArgb(), allZones = allZones)
    }

    suspend fun enterRest(consent: ControlConsent, raining: Boolean) {
        if (consent.lights) {
            vehicle.setAmbient(brightness = 5, argb = SceneColor.Dusk.toArgb(), allZones = allZones)
            vehicle.setInteriorLightsOff()
        }
        if (consent.climate) {
            vehicle.setClimate(power = true, temperatureC = SLEEP_TEMP_C, recirculation = false, fanSpeed = 2, fanDirection = FanDirection.FLOOR)
        }
        if (consent.seat) {
            when (season()) {
                Season.WINTER -> vehicle.setDriverSeatTemperature(1)
                Season.SUMMER -> vehicle.setDriverSeatVentilation(1)
                Season.MILD -> Unit
            }
        }
        if (consent.window && !raining) vehicle.setDriverWindow(VENT_OPEN_PERCENT)
        if (consent.door) vehicle.setDoorsLocked(true)
    }

    /** Wake-up ramp; [progress] goes 0 → 1 over the last two minutes of the nap. */
    suspend fun wakeStep(progress: Float, consent: ControlConsent, raining: Boolean) {
        if (consent.lights) {
            val color = if (progress < 0.5f) {
                lerp(SceneColor.Dusk, SceneColor.Sunrise, progress * 2)
            } else {
                lerp(SceneColor.Sunrise, SceneColor.Daylight, (progress - 0.5f) * 2)
            }
            vehicle.setAmbient(brightness = (10 + 90 * progress).toInt(), argb = color.toArgb(), allZones = allZones)
        }
        if (progress >= 0.25f && consent.climate) {
            vehicle.setClimate(temperatureC = WAKE_TEMP_C, fanSpeed = 4, fanDirection = FanDirection.FACE)
        }
        if (progress >= 0.5f && consent.seat) {
            vehicle.setDriverSeatTemperature(0)
            vehicle.setDriverSeatVentilation(2)
        }
        if (progress >= 0.75f && consent.window && !raining) vehicle.setDriverWindow(WAKE_OPEN_PERCENT)
    }

    /** Restores everything captured in [capture]; safe to call more than once. */
    suspend fun restore() {
        val s = snapshot ?: return
        snapshot = null
        if (s.ambientBrightness != null || s.ambientColor != null) {
            vehicle.setAmbient(brightness = s.ambientBrightness, argb = s.ambientColor, allZones = allZones)
        }
        vehicle.setClimate(
            power = s.hvacPower,
            temperatureC = s.hvacTemperatureC,
            recirculation = s.recirculation,
            fanSpeed = s.fanSpeed,
            fanDirection = s.fanDirection,
        )
        s.seatTemperatureLevel?.let { vehicle.setDriverSeatTemperature(it) }
        s.seatVentilationLevel?.let { vehicle.setDriverSeatVentilation(it) }
        s.driverWindowPercent?.let { vehicle.setDriverWindow(it) }
        s.doorsLocked?.let { vehicle.setDoorsLocked(it) }
    }

    private enum class Season { WINTER, SUMMER, MILD }

    private fun season(): Season = when (LocalDate.now().monthValue) {
        12, 1, 2 -> Season.WINTER
        6, 7, 8 -> Season.SUMMER
        else -> Season.MILD
    }

    private companion object {
        const val SLEEP_TEMP_C = 23f
        const val WAKE_TEMP_C = 21f
        const val VENT_OPEN_PERCENT = 5
        const val WAKE_OPEN_PERCENT = 15
    }
}
