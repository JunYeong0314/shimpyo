package com.shimpyo.app.data.vehicle

import com.shimpyo.app.domain.model.CabinSnapshot
import com.shimpyo.app.domain.model.FanDirection
import com.shimpyo.app.domain.model.GearPosition
import com.shimpyo.app.domain.model.SeatOccupancy
import kotlinx.coroutines.flow.StateFlow

/** Vehicle state and cabin control used by Shimpyo (backed by the Pleos Vehicle SDK). */
interface VehicleGateway {
    val connected: StateFlow<Boolean>
    val gear: StateFlow<GearPosition>
    val steeringAngleDeg: StateFlow<Float>
    val turnSignalOn: StateFlow<Boolean>
    val brakePressed: StateFlow<Boolean>
    val occupancy: StateFlow<SeatOccupancy>
    val raining: StateFlow<Boolean>
    val fuelPercent: StateFlow<Int?>
    val evSocPercent: StateFlow<Int?>

    fun start()
    fun stop()

    suspend fun readCabin(): CabinSnapshot

    /** Ambient light for the driver zone, or every zone when [allZones] is true. */
    suspend fun setAmbient(brightness: Int? = null, argb: Int? = null, allZones: Boolean = false): Boolean
    suspend fun setInteriorLightsOff(): Boolean
    suspend fun setClimate(
        power: Boolean? = null,
        temperatureC: Float? = null,
        recirculation: Boolean? = null,
        fanSpeed: Int? = null,
        fanDirection: FanDirection? = null,
    ): Boolean
    /** Positive = heating level, negative = cooling (AAOS HVAC_SEAT_TEMPERATURE convention). */
    suspend fun setDriverSeatTemperature(level: Int): Boolean
    suspend fun setDriverSeatVentilation(level: Int): Boolean
    suspend fun setDriverWindow(openPercent: Int): Boolean
    suspend fun setDoorsLocked(locked: Boolean): Boolean
}
