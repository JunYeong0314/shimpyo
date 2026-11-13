package com.shimpyo.app.domain.logic

import com.shimpyo.app.domain.model.NaviLoad
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * "A passenger stops talking at tricky moments; so should the car."
 * Decides whether this is a calm moment to start a voice conversation.
 *
 * TODO(spec): Add vehicle speed (AAOS CarPropertyManager PERF_VEHICLE_SPEED) to turn distances into
 *  time-to-maneuver. The Vehicle SDK removed VehicleSpeedListener in 2.3.2.
 */
@Singleton
class SpeakGuard @Inject constructor() {

    data class Inputs(
        val navi: NaviLoad,
        val nowMillis: Long,
        val turnSignalOn: Boolean,
        val brakePressed: Boolean,
        val steeringAngleDeg: Float,
    )

    fun isCalm(i: Inputs): Boolean {
        val fresh = i.nowMillis - i.navi.updatedAtMillis < NAVI_FRESH_MS
        val turnNear = fresh && (i.navi.nextTurnDistanceM ?: Double.MAX_VALUE) < TURN_QUIET_M
        val cautionNear = fresh && (i.navi.cautionDistanceM ?: Int.MAX_VALUE) < CAUTION_QUIET_M
        val cameraNear = fresh && (i.navi.cameraDistanceM ?: Int.MAX_VALUE) < CAUTION_QUIET_M
        return !turnNear && !cautionNear && !cameraNear &&
            !i.turnSignalOn && !i.brakePressed && abs(i.steeringAngleDeg) < STEERING_QUIET_DEG
    }

    private companion object {
        const val NAVI_FRESH_MS = 15_000L
        const val TURN_QUIET_M = 500.0
        const val CAUTION_QUIET_M = 300
        const val STEERING_QUIET_DEG = 30f
    }
}
