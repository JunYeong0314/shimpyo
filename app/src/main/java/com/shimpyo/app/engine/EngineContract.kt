package com.shimpyo.app.engine

import com.shimpyo.app.domain.model.GearPosition
import com.shimpyo.app.domain.model.RestArea
import com.shimpyo.app.domain.model.RestMode

/** Where the rest loop is (research/03_user_scenario.md §10). */
sealed interface Phase {
    data object Monitoring : Phase
    data object CheckingIn : Phase
    data class Proposing(val area: RestArea, val distanceKm: Double?) : Phase
    data class HeadingToRest(val area: RestArea) : Phase
    /** Arrived at [area]; the rest flow starts as soon as the driver shifts to P. */
    data class WaitingForPark(val area: RestArea) : Phase
    data object ChoosingMode : Phase
    data class Resting(val mode: RestMode, val totalSec: Int, val remainingSec: Int, val waking: Boolean) : Phase
    data class ConditionCheck(val result: ConditionResult?) : Phase
}

enum class ConditionResult { GOOD, NEEDS_MORE }

data class SuggestedStop(val area: RestArea, val distanceKm: Double?)

data class Connections(
    val vehicle: Boolean = false,
    val navi: Boolean = false,
    val voice: Boolean = false,
    val location: Boolean = false,
)

data class EngineState(
    val phase: Phase = Phase.Monitoring,
    val gear: GearPosition = GearPosition.UNKNOWN,
    val continuousDrivingSec: Int = 0,
    val nextCheckInAtSec: Int = 90 * 60,
    val lastSleepiness: Int? = null,
    val lastRestEndedAtMillis: Long? = null,
    val suggestedStop: SuggestedStop? = null,
    val listening: Boolean = false,
    val caption: String? = null,
    val companionsOnBoard: Boolean = false,
    val connections: Connections = Connections(),
    val timeScale: Int = 1,
    val napMinutes: Int = 15,
    /** Correct answer of the current condition-check round (demo controls only). */
    val expectedAnswer: String? = null,
)

/** Everything the UI, widget and demo controls can ask the engine to do. */
sealed interface EngineIntent {
    data object RestNow : EngineIntent
    data class AnswerProposal(val accept: Boolean) : EngineIntent
    data class ChooseMode(val mode: RestMode) : EngineIntent
    data object WakeNow : EngineIntent
    data object EndRest : EngineIntent
    data object Depart : EngineIntent
    data object FiveMoreMinutes : EngineIntent

    // Demo controls (debug builds)
    data class SetTimeScale(val scale: Int) : EngineIntent
    data class AddDrivingMinutes(val minutes: Int) : EngineIntent
    data object TriggerCheckIn : EngineIntent
    data object SimulateArrival : EngineIntent
    data class InjectAnswer(val text: String) : EngineIntent
}
