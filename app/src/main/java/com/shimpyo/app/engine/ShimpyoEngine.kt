package com.shimpyo.app.engine

import android.content.Context
import androidx.annotation.StringRes
import com.shimpyo.app.R
import com.shimpyo.app.core.time.AppClock
import com.shimpyo.app.data.location.LocationGateway
import com.shimpyo.app.data.navi.NaviEvent
import com.shimpyo.app.data.navi.NaviGateway
import com.shimpyo.app.data.restarea.RestAreaRepository
import com.shimpyo.app.data.settings.SettingsRepository
import com.shimpyo.app.data.vehicle.VehicleGateway
import com.shimpyo.app.data.voice.VoiceGateway
import com.shimpyo.app.domain.logic.CabinOrchestrator
import com.shimpyo.app.domain.logic.FatigueRules
import com.shimpyo.app.domain.logic.RestAdvice
import com.shimpyo.app.domain.logic.SpeakGuard
import com.shimpyo.app.domain.logic.UtteranceParser
import com.shimpyo.app.domain.model.GearPosition
import com.shimpyo.app.domain.model.RestMode
import com.shimpyo.app.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import com.shimpyo.app.domain.model.RestArea
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * The 쉼표 rest loop: watch → ask → propose → guide → park → rest scene → wake → check → restore.
 * Lives as a process-wide singleton driven by the foreground service; screens observe [state] and
 * send [EngineIntent]s.
 */
@Singleton
class ShimpyoEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vehicle: VehicleGateway,
    private val navi: NaviGateway,
    private val voice: VoiceGateway,
    private val location: LocationGateway,
    private val restAreas: RestAreaRepository,
    private val settings: SettingsRepository,
    private val cabin: CabinOrchestrator,
    private val rules: FatigueRules,
    private val guard: SpeakGuard,
    private val clock: AppClock,
    private val widget: WidgetUpdater,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private var started = false
    private var dialogJob: Job? = null
    private var parkedSec = 0
    private var declines = 0
    @Volatile private var wakeNowRequested = false
    private var pendingYesNo: CompletableDeferred<Boolean?>? = null
    private var pendingMode: CompletableDeferred<RestMode?>? = null
    private var pendingDepart: CompletableDeferred<Boolean>? = null

    fun start() {
        if (started) return
        started = true
        vehicle.start()
        navi.start()
        voice.start(locale())
        location.start()
        _state.update { it.copy(nextCheckInAtSec = rules.firstCheckInMinutes(clock.localTime()) * 60) }

        scope.launch {
            settings.settings.collect { s ->
                _state.update { it.copy(napMinutes = s.napMinutes, lastRestEndedAtMillis = s.lastRestEndedAtMillis) }
            }
        }
        scope.launch {
            combine(vehicle.connected, navi.connected, voice.ttsReady, location.hasFix) { v, n, t, l ->
                Connections(vehicle = v, navi = n, voice = t, location = l)
            }.collect { c -> _state.update { it.copy(connections = c) } }
        }
        scope.launch { vehicle.gear.collect { onGearChanged(it) } }
        scope.launch { vehicle.occupancy.collect { o -> _state.update { it.copy(companionsOnBoard = o.hasCompanions) } } }
        scope.launch { navi.events.collect { onNaviEvent(it) } }
        scope.launch { voice.listening.collect { l -> _state.update { it.copy(listening = l) } } }
        scope.launch { voice.lastSpoken.collect { c -> _state.update { it.copy(caption = c) } } }
        scope.launch { tickLoop() }
    }

    fun onIntent(intent: EngineIntent) {
        when (intent) {
            EngineIntent.RestNow -> onRestNow()
            is EngineIntent.AnswerProposal -> pendingYesNo?.complete(intent.accept)
            is EngineIntent.ChooseMode -> {
                val pending = pendingMode
                if (pending != null && pending.isActive) {
                    pending.complete(intent.mode)
                } else if (_state.value.phase == Phase.ChoosingMode) {
                    launchDialog { beginRest(intent.mode) }
                }
            }
            EngineIntent.WakeNow -> wakeNowRequested = true
            EngineIntent.EndRest -> scope.launch { abortRest(spoken = false) }
            EngineIntent.Depart -> pendingDepart?.complete(true)
            EngineIntent.FiveMoreMinutes -> pendingDepart?.complete(false)
            is EngineIntent.SetTimeScale -> {
                clock.setScale(intent.scale)
                _state.update { it.copy(timeScale = clock.timeScale) }
            }
            is EngineIntent.AddDrivingMinutes ->
                _state.update { it.copy(continuousDrivingSec = it.continuousDrivingSec + intent.minutes * 60) }
            EngineIntent.TriggerCheckIn -> if (dialogJob?.isActive != true) launchDialog { runCheckIn() }
            EngineIntent.SimulateArrival -> scope.launch { onNaviEvent(NaviEvent.WaypointArrived(null)) }
            is EngineIntent.InjectAnswer -> voice.injectAnswer(intent.text)
        }
    }

    // region loop

    private suspend fun tickLoop() {
        while (currentCoroutineContext().isActive) {
            delay(TICK_MS)
            val dt = clock.timeScale
            val s = _state.value
            if (s.gear.isMoving) {
                parkedSec = 0
                _state.update { it.copy(continuousDrivingSec = it.continuousDrivingSec + dt) }
            } else if (s.gear == GearPosition.PARK) {
                parkedSec += dt
                val restedLongEnough = parkedSec >= rules.restCountsAfterMinutes * 60
                if (restedLongEnough && s.phase == Phase.Monitoring && s.continuousDrivingSec > 0) resetDriving()
            }
            maybeStartCheckIn()
            widget.publish(_state.value)
        }
    }

    private fun maybeStartCheckIn() {
        val s = _state.value
        if (s.phase != Phase.Monitoring || dialogJob?.isActive == true) return
        if (!s.gear.isMoving || s.continuousDrivingSec < s.nextCheckInAtSec) return
        if (!isCalm()) return
        launchDialog { runCheckIn() }
    }

    private fun isCalm(): Boolean = guard.isCalm(
        SpeakGuard.Inputs(
            navi = navi.load.value,
            nowMillis = clock.nowMillis(),
            turnSignalOn = vehicle.turnSignalOn.value,
            brakePressed = vehicle.brakePressed.value,
            steeringAngleDeg = vehicle.steeringAngleDeg.value,
        ),
    )

    private suspend fun waitForCalm(maxMs: Long = 60_000) {
        withTimeoutOrNull(maxMs) { while (!isCalm()) delay(500) }
    }

    // endregion

    // region dialogs

    private suspend fun runCheckIn() {
        setPhase(Phase.CheckingIn)
        val minutes = _state.value.continuousDrivingSec / 60
        voice.speak(str(R.string.tts_checkin, durationText(minutes)))
        var sleepiness = UtteranceParser.parseSleepiness(voice.listen()?.text)
        if (sleepiness == null) {
            voice.speak(str(R.string.tts_checkin_retry))
            sleepiness = UtteranceParser.parseSleepiness(voice.listen()?.text)
        }
        _state.update { it.copy(lastSleepiness = sleepiness) }

        if (sleepiness == null && minutes < 120) {
            // Asked twice without an answer: do not nag, try again later.
            snooze(rules.snoozeAfterDeclineMinutes)
            return
        }
        when (rules.advise(sleepiness, minutes)) {
            RestAdvice.NONE -> {
                val wait = rules.recheckAfterFineMinutes
                voice.speak(str(R.string.tts_checkin_ok, wait))
                snooze(wait)
            }
            RestAdvice.LIGHT_REST -> propose(strong = false)
            RestAdvice.STRONG_REST -> propose(strong = true)
        }
    }

    private suspend fun propose(strong: Boolean, userRequested: Boolean = false) {
        val needEnergy = (vehicle.fuelPercent.value ?: 100) < LOW_ENERGY_PERCENT ||
            (vehicle.evSocPercent.value ?: 100) < LOW_ENERGY_PERCENT
        // Routes are computed from the navigation's map-matched position, so prefer it over raw GPS.
        navi.refreshCurrentLocation()
        delay(LOCATION_REFRESH_MS)
        val pick = restAreas.best(
            from = navi.currentLocation.value ?: location.location.value,
            preferCafe = strong,
            allowShelter = !strong,
            needEnergy = needEnergy,
        )
        if (pick == null) {
            voice.speak(str(R.string.tts_no_rest_area))
            snooze(rules.snoozeAfterDeclineMinutes)
            return
        }
        val korean = isKorean()
        // Road distance from the navigation (requestTrip → onTripInfo); straight-line distance as fallback.
        navi.requestTripEstimate(pick.area, korean)
        val trip = withTimeoutOrNull(TRIP_WAIT_MS) { navi.lastTripEstimate.filterNotNull().first() }
        val distanceKm = trip?.distanceM?.takeIf { it > 0 }?.let { it / 1000.0 } ?: pick.distanceKm
        _state.update { it.copy(suggestedStop = SuggestedStop(pick.area, distanceKm)) }
        setPhase(Phase.Proposing(pick.area, distanceKm))
        waitForCalm()

        val line = buildList {
            when {
                userRequested -> add(str(R.string.tts_user_request))
                strong && _state.value.continuousDrivingSec >= LONG_DRIVE_SEC -> add(str(R.string.tts_propose_long_drive))
            }
            add(str(R.string.tts_propose, pick.area.displayName(korean), distanceText(distanceKm)))
            if (strong && pick.area.hasCafe) add(str(R.string.tts_propose_cafe))
        }.joinToString(" ")
        voice.speak(line)

        if (awaitYesNo() == true) {
            declines = 0
            if (requestRestStop(pick.area, korean)) {
                voice.speak(str(R.string.tts_waypoint_added, pick.area.displayName(korean)))
                setPhase(Phase.HeadingToRest(pick.area))
            } else {
                voice.speak(str(R.string.tts_waypoint_failed))
                snooze(rules.snoozeAfterDeclineMinutes)
            }
        } else {
            declines += 1
            val wait = if (declines >= rules.maxDeclinesBeforeQuiet) 60 else rules.snoozeAfterDeclineMinutes
            voice.speak(str(R.string.tts_snooze, wait))
            snooze(wait)
        }
    }

    /**
     * Sends the stop to the navigation and treats an error event shortly afterwards (for example
     * "route over distance") as a failure, so we never announce a stop the navigation rejected.
     */
    private suspend fun requestRestStop(area: RestArea, korean: Boolean): Boolean = coroutineScope {
        val error = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(ROUTE_CONFIRM_MS) { navi.events.first { it is NaviEvent.Error } }
        }
        val sent = navi.addRestStop(area, korean)
        val failed = error.await() != null
        sent && !failed
    }

    /** Voice and on-screen buttons race; whichever answers first wins. */
    private suspend fun awaitYesNo(): Boolean? = coroutineScope {
        val answer = CompletableDeferred<Boolean?>()
        pendingYesNo = answer
        val listening = launch {
            val heard = voice.listen(LISTEN_ANSWER_MS)
            answer.complete(UtteranceParser.parseYesNo(heard?.text)?.let { it == UtteranceParser.YesNo.YES })
        }
        val result = answer.await()
        listening.cancel()
        pendingYesNo = null
        result
    }

    private fun onRestNow() {
        val gear = _state.value.gear
        if (gear.isMoving) {
            launchDialog { propose(strong = true, userRequested = true) }
        } else {
            launchDialog { startChoosing() }
        }
    }

    private suspend fun startChoosing() {
        setPhase(Phase.ChoosingMode)
        val mode = coroutineScope {
            val choice = CompletableDeferred<RestMode?>()
            pendingMode = choice
            val listening = launch {
                voice.speak(str(R.string.tts_choose_mode, _state.value.napMinutes))
                choice.complete(UtteranceParser.parseRestMode(voice.listen(LISTEN_ANSWER_MS)?.text))
            }
            val result = choice.await()
            listening.cancel()
            pendingMode = null
            result
        }
        // No voice answer: the mode cards stay on screen and ChooseMode starts the rest.
        if (mode != null) beginRest(mode)
    }

    private suspend fun beginRest(mode: RestMode, extraMinutes: Int? = null) {
        if (mode == RestMode.STRETCH) {
            runStretch()
            return
        }
        val user = settings.current()
        val consent = user.consent
        val raining = vehicle.raining.value
        val totalSec = (extraMinutes ?: user.napMinutes) * 60
        setPhase(Phase.Resting(mode, totalSec, totalSec, waking = false))

        if (extraMinutes == null) {
            cabin.capture(companionsOnBoard = vehicle.occupancy.value.hasCompanions)
            voice.speak(str(if (mode == RestMode.COFFEE_NAP) R.string.tts_coffee_intro else R.string.tts_nap_intro))
            if (vehicle.occupancy.value.hasCompanions) voice.speak(str(R.string.tts_family_mode))
            // A short breathing guide; the ambient light rises and falls with each breath.
            repeat(BREATH_CYCLES) {
                cabin.breathe(inhale = true, consent = consent)
                voice.speak(str(R.string.tts_breath_in))
                delay(3_000)
                cabin.breathe(inhale = false, consent = consent)
                voice.speak(str(R.string.tts_breath_out))
                delay(4_000)
            }
        }
        cabin.enterRest(consent, raining)

        var remaining = totalSec
        var wakeStage = -1
        while (remaining > 0) {
            delay(TICK_MS)
            remaining -= clock.timeScale
            if (wakeNowRequested) {
                wakeNowRequested = false
                remaining = min(remaining, WAKE_RAMP_SEC)
            }
            val waking = remaining <= WAKE_RAMP_SEC
            if (waking) {
                val progress = 1f - remaining.coerceAtLeast(0) / WAKE_RAMP_SEC.toFloat()
                val stage = (progress * 4).toInt().coerceIn(0, 4)
                if (stage != wakeStage) {
                    wakeStage = stage
                    cabin.wakeStep(stage / 4f, consent, raining)
                }
            }
            setPhase(Phase.Resting(mode, totalSec, remaining.coerceAtLeast(0), waking))
        }
        cabin.wakeStep(1f, consent, raining)
        runConditionCheck(mode)
    }

    private suspend fun runStretch() {
        val totalSec = STRETCH_SEC
        val steps = listOf(R.string.tts_stretch_1, R.string.tts_stretch_2, R.string.tts_stretch_3, R.string.tts_stretch_4)
        val perStep = totalSec / steps.size
        var remaining = totalSec
        setPhase(Phase.Resting(RestMode.STRETCH, totalSec, remaining, waking = false))
        for (step in steps) {
            voice.speak(str(step))
            var elapsed = 0
            while (elapsed < perStep && remaining > 0) {
                delay(TICK_MS)
                elapsed += clock.timeScale
                remaining -= clock.timeScale
                setPhase(Phase.Resting(RestMode.STRETCH, totalSec, remaining.coerceAtLeast(0), waking = false))
            }
        }
        voice.speak(str(R.string.tts_stretch_done))
        setPhase(Phase.ConditionCheck(ConditionResult.GOOD))
        if (awaitDepart()) finishRest() else beginRest(RestMode.CAR_NAP, extraMinutes = EXTRA_MINUTES)
    }

    private suspend fun runConditionCheck(mode: RestMode) {
        setPhase(Phase.ConditionCheck(null))
        voice.speak(str(R.string.tts_wake_greeting))
        var correct = 0
        val latencies = mutableListOf<Long>()
        repeat(CHECK_ROUNDS) {
            val digits = (1..9).shuffled().take(3)
            _state.update { it.copy(expectedAnswer = digits.reversed().joinToString(" ")) }
            voice.speak(str(R.string.tts_reverse_digits, digits.joinToString(", ")))
            val heard = voice.listen(LISTEN_ANSWER_MS)
            heard?.responseLatencyMs?.let(latencies::add)
            if (UtteranceParser.parseDigits(heard?.text) == digits.reversed()) correct++
        }
        _state.update { it.copy(expectedAnswer = null) }
        val baseline = settings.current().baselineLatencyMs
        val average = latencies.takeIf { it.isNotEmpty() }?.average()
        val good = correct >= 1 && (average == null || average <= baseline * LATENCY_TOLERANCE)
        setPhase(Phase.ConditionCheck(if (good) ConditionResult.GOOD else ConditionResult.NEEDS_MORE))
        voice.speak(str(if (good) R.string.tts_condition_good else R.string.tts_condition_more))
        if (awaitDepart()) finishRest() else beginRest(mode, extraMinutes = EXTRA_MINUTES)
    }

    /** True = depart (button, or the driver simply shifts out of P); false = five more minutes. */
    private suspend fun awaitDepart(): Boolean {
        val choice = CompletableDeferred<Boolean>()
        pendingDepart = choice
        val result = choice.await()
        pendingDepart = null
        return result
    }

    private suspend fun finishRest() {
        cabin.restore()
        settings.setLastRestEnded(clock.nowMillis())
        resetDriving()
        setPhase(Phase.Monitoring)
        voice.speak(str(R.string.tts_resume_info, rules.firstCheckInMinutes(clock.localTime())))
    }

    private suspend fun abortRest(spoken: Boolean) {
        dialogJob?.cancel()
        voice.cancel()
        val hadScene = cabin.hasSnapshot
        cabin.restore()
        setPhase(Phase.Monitoring)
        if (spoken && hadScene) voice.speak(str(R.string.tts_rest_aborted))
    }

    // endregion

    // region events

    private fun onGearChanged(gear: GearPosition) {
        _state.update { it.copy(gear = gear) }
        when (_state.value.phase) {
            is Phase.ConditionCheck -> if (gear.isMoving) pendingDepart?.complete(true)
            is Phase.Resting, Phase.ChoosingMode -> if (gear.isMoving) scope.launch { abortRest(spoken = true) }
            is Phase.WaitingForPark -> if (gear == GearPosition.PARK) launchDialog { startChoosing() }
            else -> Unit
        }
    }

    private suspend fun onNaviEvent(event: NaviEvent) {
        // TODO(spec): If the driver cancels guidance while heading to the stop (NaviEvent.RouteCancelled),
        //  decide whether to keep waiting for a park near the rest area or return to monitoring.
        //  Note: during active guidance Pleos keeps the Navigation app in front, so the rest flow stays voice-only
        //  until arrival.
        val arrived = event is NaviEvent.WaypointArrived || event is NaviEvent.DestinationArrived
        val heading = _state.value.phase as? Phase.HeadingToRest
        if (!arrived || heading == null) return
        setPhase(Phase.WaitingForPark(heading.area))
        voice.speak(str(R.string.tts_arrived))
        if (_state.value.gear == GearPosition.PARK && dialogJob?.isActive != true) launchDialog { startChoosing() }
    }

    // endregion

    // region helpers

    private fun launchDialog(block: suspend () -> Unit) {
        dialogJob?.cancel()
        dialogJob = scope.launch { block() }
    }

    private fun setPhase(phase: Phase) = _state.update { it.copy(phase = phase) }

    private fun snooze(minutes: Int) {
        _state.update { it.copy(nextCheckInAtSec = it.continuousDrivingSec + minutes * 60, phase = Phase.Monitoring) }
    }

    private fun resetDriving() {
        parkedSec = 0
        _state.update {
            it.copy(
                continuousDrivingSec = 0,
                nextCheckInAtSec = rules.firstCheckInMinutes(clock.localTime()) * 60,
                suggestedStop = null,
            )
        }
    }

    private fun locale(): Locale = context.resources.configuration.locales[0] ?: Locale.KOREA

    private fun isKorean(): Boolean = locale().language == Locale.KOREAN.language

    private fun str(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun durationText(minutes: Int): String = when {
        minutes >= 60 && minutes % 60 == 0 -> str(R.string.duration_h, minutes / 60)
        minutes >= 60 -> str(R.string.duration_hm, minutes / 60, minutes % 60)
        else -> str(R.string.duration_m, minutes)
    }

    private fun distanceText(km: Double?): String = km?.let { str(R.string.distance_km, it) } ?: ""

    // endregion

    private companion object {
        const val TICK_MS = 1_000L
        const val LISTEN_ANSWER_MS = 9_000L
        const val WAKE_RAMP_SEC = 120
        const val STRETCH_SEC = 5 * 60
        const val EXTRA_MINUTES = 5
        const val BREATH_CYCLES = 3
        const val CHECK_ROUNDS = 2
        const val LATENCY_TOLERANCE = 1.6
        const val LONG_DRIVE_SEC = 105 * 60
        const val LOW_ENERGY_PERCENT = 20
        const val ROUTE_CONFIRM_MS = 4_000L
        const val TRIP_WAIT_MS = 2_000L
        const val LOCATION_REFRESH_MS = 400L
    }
}
