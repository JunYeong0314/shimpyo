package com.shimpyo.app.data.restarea

import com.shimpyo.app.domain.model.GeoPoint
import com.shimpyo.app.domain.model.RestArea
import com.shimpyo.app.domain.model.RestAreaKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class RestAreaPick(val area: RestArea, val distanceKm: Double?)

/**
 * Rest areas and drowsy-driving shelters.
 *
 * TODO(data): Sample data (Gyeongbu Expressway, Busan-bound, approximate coordinates) for the demo.
 *  Replace with Korea Expressway Corporation open data (rest area locations and facilities,
 *  drowsy-driving shelters) after checking its license and update cycle.
 * TODO(spec): Filter to rest areas ahead on the active route (NaviHelper route summary) instead of
 *  straight-line distance.
 */
@Singleton
class RestAreaRepository @Inject constructor() {

    private val areas = listOf(
        RestArea("seoul-mannam", "서울만남의광장휴게소", "Seoul Mannam Rest Area", GeoPoint(37.4596, 127.0420), RestAreaKind.SERVICE_AREA, hasCafe = true, hasEvCharger = true),
        RestArea("giheung", "기흥휴게소", "Giheung Rest Area", GeoPoint(37.2375, 127.0936), RestAreaKind.SERVICE_AREA, hasCafe = true, hasEvCharger = true),
        RestArea("anseong", "안성휴게소", "Anseong Rest Area", GeoPoint(36.9950, 127.2131), RestAreaKind.SERVICE_AREA, hasCafe = true, hasEvCharger = true, hasPetPark = true),
        RestArea("manghyang", "망향휴게소", "Manghyang Rest Area", GeoPoint(36.9040, 127.1630), RestAreaKind.SERVICE_AREA, hasCafe = true),
        RestArea("cheonan-samgeori", "천안삼거리휴게소", "Cheonan Samgeori Rest Area", GeoPoint(36.7869, 127.1828), RestAreaKind.SERVICE_AREA, hasCafe = true, hasEvCharger = true),
        RestArea("oksan", "옥산휴게소", "Oksan Rest Area", GeoPoint(36.6789, 127.3667), RestAreaKind.SERVICE_AREA, hasCafe = true, hasEvCharger = true),
        RestArea("cheongwon-shelter", "청원 졸음쉼터(샘플)", "Cheongwon Drowsy Shelter (sample)", GeoPoint(36.6000, 127.4000), RestAreaKind.DROWSY_SHELTER),
    )

    /**
     * Picks the best stop. Strong advice prefers full rest areas (a café enables a coffee nap);
     * light advice also accepts drowsy-driving shelters.
     */
    fun best(from: GeoPoint?, preferCafe: Boolean, allowShelter: Boolean, needEnergy: Boolean): RestAreaPick? {
        val pool = areas.filter { allowShelter || it.kind == RestAreaKind.SERVICE_AREA }
        // TODO(spec): Without a location fix this falls back to the first sample; replace with a proper
        //  "no location" prompt once real data is wired in.
        if (from == null) return pool.firstOrNull()?.let { RestAreaPick(it, null) }
        return pool
            .map { it to distanceKm(from, it.location) }
            .filter { (_, km) -> km in MIN_AHEAD_KM..MAX_REACH_KM }
            .minByOrNull { (area, km) ->
                var score = km
                if (preferCafe && area.hasCafe) score -= 2.0
                if (needEnergy && area.hasEvCharger) score -= 3.0
                score
            }
            ?.let { (area, km) -> RestAreaPick(area, km) }
    }

    private fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    private companion object {
        /** Anything closer is probably behind us or already passed. */
        const val MIN_AHEAD_KM = 1.5

        /** A tired driver should not be sent further than this; beyond it we say no stop was found. */
        const val MAX_REACH_KM = 60.0
    }
}
