package com.shimpyo.app.data.navi

import com.shimpyo.app.domain.model.GeoPoint
import com.shimpyo.app.domain.model.NaviLoad
import com.shimpyo.app.domain.model.RestArea
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface NaviEvent {
    data class WaypointArrived(val name: String?) : NaviEvent
    data class DestinationArrived(val name: String?) : NaviEvent
    data object RouteCancelled : NaviEvent
    data class Error(val code: String) : NaviEvent
}

enum class PlaceKind { HOME, OFFICE, OTHER }

data class SavedPlace(val name: String, val location: GeoPoint, val kind: PlaceKind)

data class TripEstimate(val distanceM: Int, val etaSec: Int)

/** Navigation context and route requests (backed by the Pleos NaviHelper SDK). */
interface NaviGateway {
    val connected: StateFlow<Boolean>
    val load: StateFlow<NaviLoad>
    val events: SharedFlow<NaviEvent>
    val currentLocation: StateFlow<GeoPoint?>
    val savedPlaces: StateFlow<List<SavedPlace>>
    val lastTripEstimate: StateFlow<TripEstimate?>

    fun start()
    fun stop()

    /** Adds the rest area as a waypoint (or as the destination when no route is active). */
    fun addRestStop(area: RestArea, korean: Boolean): Boolean

    /** Asks the navigation for its map-matched position (routes are computed from it). */
    fun refreshCurrentLocation()

    /** Road distance / ETA to [area]; the answer arrives on [lastTripEstimate] (reset to null first). */
    fun requestTripEstimate(area: RestArea, korean: Boolean)
}
