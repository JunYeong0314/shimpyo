package com.shimpyo.app.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.util.Log
import com.shimpyo.app.domain.model.CabinSnapshot
import com.shimpyo.app.domain.model.FanDirection
import com.shimpyo.app.domain.model.GearPosition
import com.shimpyo.app.domain.model.SeatOccupancy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Pleos Vehicle SDK v2.3.2 adapter. Every call is callback based; this class turns them into
 * StateFlows (state) and suspend functions (control) with a timeout so a missing capability never
 * blocks the rest scene.
 *
 * Area IDs differ per vehicle (the emulator, for example, rejects a single driver-seat area for
 * HVAC, ambient light and door lock), so the supported areas are resolved once with the
 * check*Capabilities APIs and cached in [areas].
 *
 * TODO(spec): Driver seat is ROW_1_LEFT (left-hand drive). Right-hand-drive markets need ROW_1_RIGHT.
 * TODO(spec): Clamp values to the AreaBounds the capability APIs return (temperature, fan speed).
 */
@Singleton
class PleosVehicleGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : VehicleGateway {

    private var vehicle: Vehicle? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _connected = MutableStateFlow(false)
    private val _gear = MutableStateFlow(GearPosition.UNKNOWN)
    private val _steering = MutableStateFlow(0f)
    private val _turnSignal = MutableStateFlow(false)
    private val _brake = MutableStateFlow(false)
    private val _occupancy = MutableStateFlow(SeatOccupancy())
    private val _raining = MutableStateFlow(false)
    private val _fuel = MutableStateFlow<Int?>(null)
    private val _soc = MutableStateFlow<Int?>(null)

    override val connected: StateFlow<Boolean> = _connected.asStateFlow()
    override val gear: StateFlow<GearPosition> = _gear.asStateFlow()
    override val steeringAngleDeg: StateFlow<Float> = _steering.asStateFlow()
    override val turnSignalOn: StateFlow<Boolean> = _turnSignal.asStateFlow()
    override val brakePressed: StateFlow<Boolean> = _brake.asStateFlow()
    override val occupancy: StateFlow<SeatOccupancy> = _occupancy.asStateFlow()
    override val raining: StateFlow<Boolean> = _raining.asStateFlow()
    override val fuelPercent: StateFlow<Int?> = _fuel.asStateFlow()
    override val evSocPercent: StateFlow<Int?> = _soc.asStateFlow()

    /** Supported areas per property, resolved from the capability APIs (fallbacks until resolved). */
    private data class Areas(
        val ambientBrightness: List<SeatArea> = emptyList(),
        val ambientColor: List<SeatArea> = emptyList(),
        val ambientSwitch: List<SeatArea> = emptyList(),
        val hvacPower: SeatArea? = null,
        val hvacRecirculation: SeatArea? = null,
        val hvacTemperature: SeatArea? = null,
        val hvacFanSpeed: SeatArea? = null,
        val hvacFanDirection: SeatArea? = null,
        val readingLight: SeatArea? = null,
        val driverWindow: WindowArea? = null,
        val doorLock: List<DoorArea> = emptyList(),
    )

    @Volatile private var areas = Areas()

    private val driverSeat get() = SeatArea(SeatValue.ROW_1_LEFT)
    private val fallbackAllSeats get() = SeatArea(
        SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT,
    )

    // region listeners
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) { _gear.value = gear.toGearPosition() }
        override fun onFailed(e: Exception) = logFailure("gear", e)
    }
    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) { _steering.value = angle ?: 0f }
        override fun onFailed(e: Exception) = logFailure("steering", e)
    }
    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
            _turnSignal.value = state != null && state != TurnSignalValue.OFF
        }
        override fun onFailed(e: Exception) = logFailure("turnSignal", e)
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) { _brake.value = pressed == true }
        override fun onFailed(e: Exception) = logFailure("brake", e)
    }
    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) { _raining.value = state == WiperState.ON }
        override fun onFailed(e: Exception) = logFailure("wiper", e)
    }
    private val fuelListener = object : FuelPercentListener {
        override fun onFuelPercentUpdated(percent: Int?) { _fuel.value = percent }
        override fun onFailed(e: Exception) = logFailure("fuel", e)
    }
    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(percent: Int?) { _soc.value = percent }
        override fun onFailed(e: Exception) = logFailure("soc", e)
    }
    private val occupancyListeners = mapOf(
        SeatValue.ROW_1_RIGHT to occupancyListener { o, v -> o.copy(passenger = v) },
        SeatValue.ROW_2_LEFT to occupancyListener { o, v -> o.copy(rearLeft = v) },
        SeatValue.ROW_2_RIGHT to occupancyListener { o, v -> o.copy(rearRight = v) },
    )

    private fun occupancyListener(apply: (SeatOccupancy, Boolean) -> SeatOccupancy) = object : SeatOccupancyListener {
        override fun onSeatOccupancyUpdated(occupied: Boolean?) { _occupancy.update { apply(it, occupied == true) } }
        override fun onFailed(e: Exception) = logFailure("occupancy", e)
    }
    // endregion

    override fun start() {
        if (vehicle != null) return
        runCatching {
            val v = Vehicle(context)
            v.initialize()
            vehicle = v
            v.getGear().registerGearSelection(gearListener)
            v.getGear().getCurrentGearSelectionState({ g -> _gear.value = g.toGearPosition() }, { e -> logFailure("gear.get", e) })
            v.getSteeringWheel().registerSteeringWheelAngle(steeringListener)
            v.getTurnSignal().registerTurnSignalState(turnSignalListener)
            v.getBrake().registerBrakePedalStatus(brakeListener)
            v.getWiper().registerWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD), wiperListener)
            v.getFuel().registerFuelPercent(fuelListener)
            v.getEvBattery().registerEvBatteryStateOfCharge(socListener)
            occupancyListeners.forEach { (seat, listener) -> v.getSeat().registerSeatOccupancy(SeatArea(seat), listener) }
            _connected.value = true
            scope.launch { resolveAreas(v) }
        }.onFailure {
            Log.w(TAG, "Vehicle SDK unavailable", it)
            _connected.value = false
        }
    }

    override fun stop() {
        val v = vehicle ?: return
        runCatching {
            v.getGear().unregisterGearSelection(gearListener)
            v.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener)
            v.getTurnSignal().unregisterTurnSignalState(turnSignalListener)
            v.getBrake().unregisterBrakePedalStatus(brakeListener)
            v.getWiper().unregisterWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD), wiperListener)
            v.getFuel().unregisterFuelPercent(fuelListener)
            v.getEvBattery().unregisterEvBatteryStateOfCharge(socListener)
            occupancyListeners.forEach { (seat, listener) -> v.getSeat().unregisterSeatOccupancy(SeatArea(seat), listener) }
            v.release()
        }
        vehicle = null
        _connected.value = false
    }

    /** Asks the vehicle which areas each property supports, so control calls use valid area IDs. */
    private suspend fun resolveAreas(v: Vehicle) {
        val light = v.getLight()
        val hvac = v.getHVAC()
        val resolved = Areas(
            ambientBrightness = read<Map<SeatArea, *>> { ok, err ->
                light.checkAmbientLightBrightnessCapabilities({ _, map -> ok(map) }, err)
            }?.keys?.toList().orEmpty(),
            ambientColor = read<List<SeatArea>> { ok, err ->
                light.checkAmbientLightBrightnessColorCapabilities({ _, list -> ok(list) }, err)
            }.orEmpty(),
            ambientSwitch = read<Map<SeatArea, *>> { ok, err ->
                light.checkAmbientLightSwitchCapabilities({ _, map -> ok(map) }, err)
            }?.keys?.toList().orEmpty(),
            hvacPower = read<List<SeatArea>> { ok, err -> hvac.checkHvacPowerCapabilities({ list -> ok(list) }, err) }?.driverOrFirst(),
            hvacRecirculation = read<List<SeatArea>> { ok, err ->
                hvac.checkHvacRecirculationCapabilities({ list -> ok(list) }, err)
            }?.driverOrFirst(),
            hvacTemperature = read<Map<SeatArea, *>> { ok, err ->
                hvac.checkHvacTemperatureCapabilities({ map -> ok(map) }, err)
            }?.keys?.toList()?.driverOrFirst(),
            hvacFanSpeed = read<Map<SeatArea, *>> { ok, err -> hvac.checkHvacFanSpeedCapabilities({ map -> ok(map) }, err) }
                ?.keys?.toList()?.driverOrFirst(),
            hvacFanDirection = read<Map<SeatArea, *>> { ok, err -> hvac.checkHvacFanDirectionCapabilities({ map -> ok(map) }, err) }
                ?.keys?.toList()?.driverOrFirst(),
            readingLight = read<Map<SeatArea, *>> { ok, err -> light.checkReadingLightSwitchCapabilities({ map -> ok(map) }, err) }
                ?.keys?.toList()?.driverOrFirst(),
            driverWindow = read<Map<WindowArea, *>> { ok, err -> v.getWindow().checkWindowPositionCapabilities({ map -> ok(map) }, err) }
                ?.keys?.let { keys -> keys.firstOrNull { WindowValue.ROW_1_LEFT in it.values } ?: keys.firstOrNull() },
            doorLock = read<List<DoorArea>> { ok, err -> v.getDoor().checkDoorLockCapabilities({ list -> ok(list) }, err) }.orEmpty(),
        )
        areas = resolved
        Log.i(TAG, "Resolved areas: $resolved")
    }

    override suspend fun readCabin(): CabinSnapshot {
        val v = vehicle ?: return CabinSnapshot()
        val light = v.getLight()
        val hvac = v.getHVAC()
        val seat = v.getSeat()
        val a = areas
        val ambient = a.ambientBrightness.driverOrFirst() ?: driverSeat
        val ambientColor = a.ambientColor.driverOrFirst() ?: driverSeat
        return CabinSnapshot(
            ambientBrightness = read<Int> { ok, err -> light.getAmbientLightBrightness(ambient, ok, err) },
            ambientColor = read<Int> { ok, err -> light.getAmbientLightBrightnessColor(ambientColor, ok, err) },
            hvacPower = read<Boolean> { ok, err -> hvac.getHvacPowerState(a.hvacPower ?: fallbackAllSeats, ok, err) },
            hvacTemperatureC = read<Float> { ok, err ->
                hvac.getHvacTemperature(a.hvacTemperature ?: driverSeat, TemperatureUnit.CELSIUS, ok, err)
            },
            recirculation = read<Boolean> { ok, err -> hvac.getHvacRecirculationState(a.hvacRecirculation ?: fallbackAllSeats, ok, err) },
            fanSpeed = read<Int> { ok, err -> hvac.getHvacFanSpeed(a.hvacFanSpeed ?: driverSeat, ok, err) },
            fanDirection = read<FanDirectionType> { ok, err -> hvac.getHvacFanDirection(a.hvacFanDirection ?: driverSeat, ok, err) }?.toDomain(),
            seatTemperatureLevel = read<Int> { ok, err -> seat.getHvacSeatTemperature(driverSeat, ok, err) },
            seatVentilationLevel = read<Int> { ok, err -> seat.getHvacSeatVentilationState(driverSeat, ok, err) },
            driverWindowPercent = read<Int> { ok, err ->
                v.getWindow().getCurrentWindowPosition(a.driverWindow ?: WindowArea(WindowValue.ROW_1_LEFT), ok, err)
            },
            doorsLocked = read<Boolean> { ok, err ->
                v.getDoor().getDoorLockState(a.doorLock.firstOrNull() ?: DoorArea(DoorValue.ROW_1_LEFT), ok, err)
            },
        )
    }

    override suspend fun setAmbient(brightness: Int?, argb: Int?, allZones: Boolean): Boolean {
        val light = vehicle?.getLight() ?: return false
        val a = areas
        fun pick(list: List<SeatArea>): List<SeatArea> = when {
            list.isEmpty() -> listOf(driverSeat)
            allZones -> list
            else -> listOfNotNull(list.driverOrFirst())
        }
        var any = false
        for (area in pick(a.ambientSwitch)) {
            command { done -> light.setAmbientLightSwitch(area, LightSwitch.ON, { _, _ -> done(true) }, { done(false) }) }
        }
        if (brightness != null) {
            for (area in pick(a.ambientBrightness)) {
                any = command { done ->
                    light.setAmbientLightBrightness(area, brightness.coerceIn(0, 100), { _, _ -> done(true) }, { done(false) })
                } || any
            }
        }
        if (argb != null) {
            // Alpha is fixed to 0xFF by the SDK.
            for (area in pick(a.ambientColor)) {
                any = command { done ->
                    light.setAmbientLightBrightnessColor(area, argb or 0xFF000000.toInt(), { _, _ -> done(true) }, { done(false) })
                } || any
            }
        }
        return any
    }

    override suspend fun setInteriorLightsOff(): Boolean {
        val light = vehicle?.getLight() ?: return false
        val cabin = command { done -> light.setCabinLightSwitch(LightSwitch.OFF, { done(true) }, { done(false) }) }
        val reading = command { done ->
            light.setReadingLightSwitch(areas.readingLight ?: driverSeat, LightSwitch.OFF, { done(true) }, { done(false) })
        }
        return cabin || reading
    }

    override suspend fun setClimate(
        power: Boolean?,
        temperatureC: Float?,
        recirculation: Boolean?,
        fanSpeed: Int?,
        fanDirection: FanDirection?,
    ): Boolean {
        val hvac = vehicle?.getHVAC() ?: return false
        val a = areas
        var ok = true
        if (power != null) ok = command { done ->
            hvac.setHvacPower(a.hvacPower ?: fallbackAllSeats, power, { _, _ -> done(true) }, { done(false) })
        } && ok
        if (recirculation != null) ok = command { done ->
            hvac.setHvacRecirculation(a.hvacRecirculation ?: fallbackAllSeats, recirculation, { _, _ -> done(true) }, { done(false) })
        } && ok
        if (temperatureC != null) ok = command { done ->
            hvac.setHvacTemperature(a.hvacTemperature ?: driverSeat, temperatureC, TemperatureUnit.CELSIUS, { _, _ -> done(true) }, { done(false) })
        } && ok
        if (fanSpeed != null) ok = command { done ->
            hvac.setHvacFanSpeed(a.hvacFanSpeed ?: driverSeat, fanSpeed, { _, _ -> done(true) }, { done(false) })
        } && ok
        if (fanDirection != null) ok = command { done ->
            hvac.setHvacFanDirection(a.hvacFanDirection ?: driverSeat, fanDirection.toSdk(), { _, _ -> done(true) }, { done(false) })
        } && ok
        return ok
    }

    override suspend fun setDriverSeatTemperature(level: Int): Boolean {
        val seat = vehicle?.getSeat() ?: return false
        return command { done -> seat.setHvacSeatTemperature(driverSeat, level, { _, _ -> done(true) }, { done(false) }) }
    }

    override suspend fun setDriverSeatVentilation(level: Int): Boolean {
        val seat = vehicle?.getSeat() ?: return false
        return command { done -> seat.setHvacSeatVentilation(driverSeat, level, { _, _ -> done(true) }, { done(false) }) }
    }

    override suspend fun setDriverWindow(openPercent: Int): Boolean {
        val window = vehicle?.getWindow() ?: return false
        // TODO(spec): Confirm the position scale (0 = closed, 100 = fully open) on a real vehicle.
        return command { done ->
            window.setWindowPosition(
                areas.driverWindow ?: WindowArea(WindowValue.ROW_1_LEFT), openPercent.coerceIn(0, 100), { _, _ -> done(true) }, { done(false) },
            )
        }
    }

    override suspend fun setDoorsLocked(locked: Boolean): Boolean {
        val door = vehicle?.getDoor() ?: return false
        val targets = areas.doorLock.ifEmpty {
            listOf(DoorArea(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT))
        }
        var any = false
        for (area in targets) {
            any = command { done -> door.setDoorLock(area, locked, { _, _ -> done(true) }, { done(false) }) } || any
        }
        return any
    }

    // region helpers
    private fun List<SeatArea>.driverOrFirst(): SeatArea? =
        firstOrNull { SeatValue.ROW_1_LEFT in it.values } ?: firstOrNull()

    private suspend fun command(block: (done: (Boolean) -> Unit) -> Unit): Boolean =
        withTimeoutOrNull(CALL_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                runCatching { block { ok -> if (cont.isActive) cont.resume(ok) } }
                    .onFailure { if (cont.isActive) cont.resume(false) }
            }
        } ?: false

    private suspend fun <T> read(block: (onValue: (T?) -> Unit, onError: (Exception?) -> Unit) -> Unit): T? =
        withTimeoutOrNull(CALL_TIMEOUT_MS) {
            suspendCancellableCoroutine<T?> { cont ->
                runCatching { block({ value -> if (cont.isActive) cont.resume(value) }, { if (cont.isActive) cont.resume(null) }) }
                    .onFailure { if (cont.isActive) cont.resume(null) }
            }
        }

    private fun logFailure(what: String, e: Exception?) {
        Log.w(TAG, "Vehicle $what failed: ${e?.message}")
    }
    // endregion

    private companion object {
        const val TAG = "PleosVehicle"
        const val CALL_TIMEOUT_MS = 3_000L
    }
}

private fun VehicleGear?.toGearPosition(): GearPosition = when (this) {
    VehicleGear.GEAR_PARK -> GearPosition.PARK
    VehicleGear.GEAR_REVERSE -> GearPosition.REVERSE
    VehicleGear.GEAR_NEUTRAL -> GearPosition.NEUTRAL
    null, VehicleGear.GEAR_UNKNOWN -> GearPosition.UNKNOWN
    else -> GearPosition.DRIVE // GEAR_DRIVE and manual gears 1..9
}

private fun FanDirection.toSdk(): FanDirectionType = when (this) {
    FanDirection.FACE -> FanDirectionType.FACE
    FanDirection.FLOOR -> FanDirectionType.FLOOR
    FanDirection.FACE_AND_FLOOR -> FanDirectionType.FACE_AND_FLOOR
    FanDirection.DEFROST -> FanDirectionType.DEFROST
}

private fun FanDirectionType.toDomain(): FanDirection? = when (this) {
    FanDirectionType.FACE -> FanDirection.FACE
    FanDirectionType.FLOOR -> FanDirection.FLOOR
    FanDirectionType.FACE_AND_FLOOR -> FanDirection.FACE_AND_FLOOR
    FanDirectionType.DEFROST -> FanDirection.DEFROST
    else -> null
}
