package com.shimpyo.app.domain.model

enum class GearPosition {
    PARK, DRIVE, REVERSE, NEUTRAL, UNKNOWN;

    val isMoving: Boolean get() = this == DRIVE || this == REVERSE
}

data class GeoPoint(val latitude: Double, val longitude: Double)

data class SeatOccupancy(
    val driver: Boolean = true,
    val passenger: Boolean = false,
    val rearLeft: Boolean = false,
    val rearRight: Boolean = false,
) {
    val hasCompanions: Boolean get() = passenger || rearLeft || rearRight
}

enum class RestAreaKind { SERVICE_AREA, DROWSY_SHELTER }

data class RestArea(
    val id: String,
    val nameKo: String,
    val nameEn: String,
    val location: GeoPoint,
    val kind: RestAreaKind,
    val hasCafe: Boolean = false,
    val hasEvCharger: Boolean = false,
    val hasPetPark: Boolean = false,
) {
    fun displayName(korean: Boolean) = if (korean) nameKo else nameEn
}

enum class RestMode { CAR_NAP, COFFEE_NAP, STRETCH }

/** What the navigation reports about the road ahead (null = unknown or nothing near). */
data class NaviLoad(
    val nextTurnDistanceM: Double? = null,
    val cautionDistanceM: Int? = null,
    val cameraDistanceM: Int? = null,
    val updatedAtMillis: Long = 0L,
)

enum class FanDirection { FACE, FLOOR, FACE_AND_FLOOR, DEFROST }

/** Cabin settings captured before a break so they can be restored afterwards. */
data class CabinSnapshot(
    val ambientBrightness: Int? = null,
    val ambientColor: Int? = null,
    val hvacPower: Boolean? = null,
    val hvacTemperatureC: Float? = null,
    val recirculation: Boolean? = null,
    val fanSpeed: Int? = null,
    val fanDirection: FanDirection? = null,
    val seatTemperatureLevel: Int? = null,
    val seatVentilationLevel: Int? = null,
    val driverWindowPercent: Int? = null,
    val doorsLocked: Boolean? = null,
)

/** Which cabin functions the driver allowed Shimpyo to adjust (onboarding consent). */
data class ControlConsent(
    val lights: Boolean = true,
    val climate: Boolean = true,
    val seat: Boolean = true,
    val window: Boolean = true,
    val door: Boolean = true,
)
