package com.shimpyo.app.ui.main

import androidx.lifecycle.viewModelScope
import com.shimpyo.app.BuildConfig
import com.shimpyo.app.core.demo.DemoUi
import com.shimpyo.app.core.mvi.MviViewModel
import com.shimpyo.app.engine.EngineIntent
import com.shimpyo.app.engine.EngineState
import com.shimpyo.app.engine.ShimpyoEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val engine: ShimpyoEngine,
) : MviViewModel<MainState, MainIntent, MainEffect>(MainState(demoControls = BuildConfig.DEBUG)) {

    init {
        engine.start()
        viewModelScope.launch {
            engine.state.collect { s -> setState { reduce(s) } }
        }
        if (BuildConfig.DEBUG) {
            viewModelScope.launch {
                DemoUi.panelVisible.collect { visible -> setState { copy(demoControls = visible) } }
            }
        }
    }

    override suspend fun handleIntent(intent: MainIntent) {
        engine.onIntent(
            when (intent) {
                MainIntent.RestNow -> EngineIntent.RestNow
                is MainIntent.AnswerProposal -> EngineIntent.AnswerProposal(intent.accept)
                is MainIntent.ChooseMode -> EngineIntent.ChooseMode(intent.mode)
                MainIntent.WakeNow -> EngineIntent.WakeNow
                MainIntent.EndRest -> EngineIntent.EndRest
                MainIntent.Depart -> EngineIntent.Depart
                MainIntent.FiveMoreMinutes -> EngineIntent.FiveMoreMinutes
                is MainIntent.SetTimeScale -> EngineIntent.SetTimeScale(intent.scale)
                is MainIntent.AddDrivingMinutes -> EngineIntent.AddDrivingMinutes(intent.minutes)
                MainIntent.TriggerCheckIn -> EngineIntent.TriggerCheckIn
                MainIntent.SimulateArrival -> EngineIntent.SimulateArrival
                is MainIntent.Answer -> EngineIntent.InjectAnswer(intent.text)
            },
        )
    }

    private fun MainState.reduce(s: EngineState) = copy(
        surface = s.surface(),
        phase = s.phase,
        continuousMinutes = s.continuousDrivingSec / 60,
        minutesToCheckIn = ((s.nextCheckInAtSec - s.continuousDrivingSec) / 60).coerceAtLeast(0),
        lastRestEndedAtMillis = s.lastRestEndedAtMillis,
        suggestedStop = s.suggestedStop,
        listening = s.listening,
        caption = s.caption,
        companionsOnBoard = s.companionsOnBoard,
        connections = s.connections,
        napMinutes = s.napMinutes,
        timeScale = s.timeScale,
        expectedAnswer = s.expectedAnswer,
    )
}
