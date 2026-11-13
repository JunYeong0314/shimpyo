package com.shimpyo.app.data.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.shimpyo.app.domain.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface LocationGateway {
    val location: StateFlow<GeoPoint?>
    val hasFix: StateFlow<Boolean>
    fun start()
    fun stop()
    fun hasPermission(): Boolean

    companion object {
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    }
}

/**
 * Pleos FusedLocation SDK v1.1.3 adapter. FUSED_LOCATION is a dangerous permission, so updates start
 * only after the runtime grant (requested during onboarding, while parked).
 *
 * TODO(spec): Use LocationData.confidence (scale undocumented) plus a missing fix while parked to
 *  detect underground/enclosed parking, and block engine-on naps there (CO risk).
 * TODO(spec): addProximityAlert around the chosen rest area could replace the navigation arrival event
 *  when the driver parks at the rest area without an active route.
 */
@Singleton
class PleosLocationGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationGateway {

    private var fused: FusedLocation? = null
    private val _location = MutableStateFlow<GeoPoint?>(null)
    private val _hasFix = MutableStateFlow(false)
    override val location: StateFlow<GeoPoint?> = _location.asStateFlow()
    override val hasFix: StateFlow<Boolean> = _hasFix.asStateFlow()

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location == null) return
            _location.value = GeoPoint(location.latitude, location.longitude)
            _hasFix.value = true
        }

        override fun onError(exception: Exception) {
            Log.w(TAG, "Location error: ${exception.message}")
        }
    }

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, LocationGateway.PERMISSION) == PackageManager.PERMISSION_GRANTED

    override fun start() {
        if (fused != null || !hasPermission()) return
        runCatching {
            val f = FusedLocation(context)
            f.initialize()
            fused = f
            if (!f.isSupported()) {
                Log.i(TAG, "FusedLocation not supported on this vehicle")
                return
            }
            f.getLastKnownLocation(listener)
            f.registerFusedLocationCallback(listener)
        }.onFailure { Log.w(TAG, "FusedLocation unavailable", it) }
    }

    override fun stop() {
        fused?.let { runCatching { it.unregisterFusedLocationCallback(listener); it.release() } }
        fused = null
    }

    private companion object {
        const val TAG = "PleosLocation"
    }
}
