package com.shimpyo.app.ui.main

import com.shimpyo.app.core.mvi.UiEffect
import com.shimpyo.app.core.mvi.UiIntent
import com.shimpyo.app.core.mvi.UiState
import com.shimpyo.app.domain.model.RestMode
import com.shimpyo.app.engine.Connections
import com.shimpyo.app.engine.EngineState
import com.shimpyo.app.engine.Phase
import com.shimpyo.app.engine.SuggestedStop

/** Which surface the car shows: parked home, distraction-optimized driving view, or the rest flow. */
enum class Surface { HOME, DRIVING, REST }

data class MainState(
    val surface: Surface = Surface.HOME,
    val phase: Phase = Phase.Monitoring,
    val continuousMinutes: Int = 0,
    val minutesToCheckIn: Int = 0,
    val lastRestEndedAtMillis: Long? = null,
    val suggestedStop: SuggestedStop? = null,
    val listening: Boolean = false,
    val caption: String? = null,
    val companionsOnBoard: Boolean = false,
    val connections: Connections = Connections(),
    val napMinutes: Int = 15,
    val timeScale: Int = 1,
    val demoControls: Boolean = false,
    val expectedAnswer: String? = null,
) : UiState

sealed interface MainIntent : UiIntent {
    data object RestNow : MainIntent
    data class AnswerProposal(val accept: Boolean) : MainIntent
    data class ChooseMode(val mode: RestMode) : MainIntent
    data object WakeNow : MainIntent
    data object EndRest : MainIntent
    data object Depart : MainIntent
    data object FiveMoreMinutes : MainIntent

    // Demo controls
    data class SetTimeScale(val scale: Int) : MainIntent
    data class AddDrivingMinutes(val minutes: Int) : MainIntent
    data object TriggerCheckIn : MainIntent
    data object SimulateArrival : MainIntent
    data class Answer(val text: String) : MainIntent
}

/** The main surface reacts only through state; no one-off effects yet. */
sealed interface MainEffect : UiEffect

fun EngineState.surface(): Surface = when {
    phase is Phase.ChoosingMode || phase is Phase.Resting || phase is Phase.ConditionCheck -> Surface.REST
    gear.isMoving -> Surface.DRIVING
    else -> Surface.HOME
}
