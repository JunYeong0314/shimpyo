package com.shimpyo.app.data.navi

import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.shimpyo.app.core.time.AppClock
import com.shimpyo.app.domain.model.GeoPoint
import com.shimpyo.app.domain.model.NaviLoad
import com.shimpyo.app.domain.model.RestArea
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pleos NaviHelper SDK v3.0.5 adapter. Requests go to the Pleos Navigation app; results and road
 * events arrive on [NaviHelperEventListener] (which events arrive depends on the Navigation app).
 */
@Singleton
class PleosNaviGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: AppClock,
) : NaviGateway {

    private var helper: NaviHelper? = null
    private var routeActive = false
    private var waypointCount = 0

    private val _connected = MutableStateFlow(false)
    private val _load = MutableStateFlow(NaviLoad())
    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 8)
    private val _location = MutableStateFlow<GeoPoint?>(null)
    private val _places = MutableStateFlow<List<SavedPlace>>(emptyList())
    private val _trip = MutableStateFlow<TripEstimate?>(null)

    override val connected: StateFlow<Boolean> = _connected.asStateFlow()
    override val load: StateFlow<NaviLoad> = _load.asStateFlow()
    override val events: SharedFlow<NaviEvent> = _events.asSharedFlow()
    override val currentLocation: StateFlow<GeoPoint?> = _location.asStateFlow()
    override val savedPlaces: StateFlow<List<SavedPlace>> = _places.asStateFlow()
    override val lastTripEstimate: StateFlow<TripEstimate?> = _trip.asStateFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onTBTInfo(tbtInfo: List<TBTInfo>) {
            _load.update { it.copy(nextTurnDistanceM = tbtInfo.firstOrNull()?.distance, updatedAtMillis = clock.nowMillis()) }
        }

        override fun onCautionPointInfo(cautionPoints: List<CautionPoint>) {
            _load.update { it.copy(cautionDistanceM = cautionPoints.minOfOrNull { c -> c.remainDistance }, updatedAtMillis = clock.nowMillis()) }
        }

        override fun onCameraAlert(cameraAlerts: List<CameraAlert>) {
            _load.update { it.copy(cameraDistanceM = cameraAlerts.minOfOrNull { c -> c.remainDistance }, updatedAtMillis = clock.nowMillis()) }
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
            waypointCount = (waypointCount - 1).coerceAtLeast(0)
            _events.tryEmit(NaviEvent.WaypointArrived(waypointArrivedInfo.name))
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            routeActive = false
            _events.tryEmit(NaviEvent.DestinationArrived(destinationArrivedInfo.name))
        }

        override fun onWayPointInfo(waypointInfo: List<WaypointInfo>) {
            waypointCount = waypointInfo.size
        }

        override fun onNavigationStatus(navigationStatus: NavigationStatus) {
            routeActive = navigationStatus == NavigationStatus.ROUTE_DRIVE
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            _location.value = GeoPoint(currentLocationInfo.latitude, currentLocationInfo.longitude)
        }

        override fun onBookmarkInfo(bookmarkInfo: List<BookmarkInfo>) {
            _places.value = bookmarkInfo.map { b ->
                SavedPlace(
                    name = b.name,
                    location = GeoPoint(b.latitude, b.longitude),
                    kind = when (b.category) {
                        BookmarkType.HOME -> PlaceKind.HOME
                        BookmarkType.OFFICE -> PlaceKind.OFFICE
                        else -> PlaceKind.OTHER
                    },
                )
            }
        }

        override fun onTripInfo(tripInfo: TripInfo) {
            // Units checked on the Pleos emulator (2026-11-09): distance in meters, eta in seconds, SoC in percent.
            Log.i(TAG, "TripInfo distance=${tripInfo.distance} eta=${tripInfo.eta} soc=${tripInfo.estimatedSocAtDest}")
            _trip.value = TripEstimate(distanceM = tripInfo.distance, etaSec = tripInfo.eta)
        }

        override fun onRouteCancelled() {
            routeActive = false
            _events.tryEmit(NaviEvent.RouteCancelled)
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "Navi error $errorCode")
            _events.tryEmit(NaviEvent.Error(errorCode.name))
        }
    }

    override fun start() {
        if (helper != null) return
        runCatching {
            val h = NaviHelper(context)
            helper = h
            h.initialize(
                {
                    _connected.value = true
                    h.addListener(listener)
                    h.getBookmarkInfo()
                    h.getCurrentLocationInfo()
                    h.getWaypointInfo()
                    h.getTBTInfo()
                },
                { t ->
                    Log.w(TAG, "NaviHelper init failed", t)
                    _connected.value = false
                },
            )
        }.onFailure {
            Log.w(TAG, "NaviHelper unavailable", it)
            helper = null
        }
    }

    override fun stop() {
        helper?.let { h -> runCatching { h.removeListener(listener); h.release() } }
        helper = null
        _connected.value = false
    }

    override fun addRestStop(area: RestArea, korean: Boolean): Boolean {
        val h = helper ?: return false
        val name = area.displayName(korean)
        return runCatching {
            if (routeActive) {
                // TODO(spec): Waypoints are limited to two. When both are used, ask the driver before
                //  replacing the second one instead of replacing it silently.
                val index = if (waypointCount >= 1) WaypointIndex.SECOND else WaypointIndex.FIRST
                // TODO(spec): poiId/poiSubId normally come from the navigation POI database. Confirm that a
                //  coordinates-only request is accepted, or resolve the rest area's POI ID first.
                h.addWaypoint(
                    RequestWaypointInfo(
                        latitude = area.location.latitude,
                        longitude = area.location.longitude,
                        poiName = name,
                        waypointIndex = index,
                        poiId = "",
                        address = name,
                        poiSubId = "0",
                    ),
                )
                waypointCount += 1
            } else {
                // No active route (free drive): guide to the rest area as the destination.
                h.requestRoute(
                    RouteInfo(
                        latitude = area.location.latitude,
                        longitude = area.location.longitude,
                        displayLatitude = null,
                        displayLongitude = null,
                        poiName = name,
                        poiId = "",
                        address = name,
                        poiSubId = "0",
                        routeOption = RouteOption.RECOMMENDED,
                        poiProvider = "",
                        isMobileRequest = false,
                    ),
                )
                routeActive = true
            }
            true
        }.getOrElse {
            Log.w(TAG, "addRestStop failed", it)
            false
        }
    }

    override fun refreshCurrentLocation() {
        runCatching { helper?.getCurrentLocationInfo() }
    }

    override fun requestTripEstimate(area: RestArea, korean: Boolean) {
        val h = helper ?: return
        _trip.value = null
        runCatching {
            h.requestTrip(
                TripRequest(
                    latitude = area.location.latitude,
                    longitude = area.location.longitude,
                    poiId = "",
                    poiName = area.displayName(korean),
                    poiSubId = "0",
                ),
            )
        }.onFailure { Log.w(TAG, "requestTrip failed", it) }
    }

    private companion object {
        const val TAG = "PleosNavi"
    }
}
