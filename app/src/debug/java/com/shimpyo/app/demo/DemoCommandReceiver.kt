package com.shimpyo.app.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shimpyo.app.core.demo.DemoUi
import com.shimpyo.app.domain.model.RestMode
import com.shimpyo.app.engine.EngineIntent
import com.shimpyo.app.engine.ShimpyoEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Debug builds only: drives the demo flow from adb so the contest video can be recorded without the
 * on-screen demo panel. Example:
 *
 *   adb shell am broadcast --user current -n com.shimpyo.app/.demo.DemoCommandReceiver --es cmd answer --es text 7
 *
 * Commands: panel(show), scale(value), add(minutes), checkin, arrive, answer(text), answer_expected,
 * rest_now, yes, no, mode(mode = CAR_NAP | COFFEE_NAP | STRETCH), wake, end, depart, more.
 */
class DemoCommandReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun engine(): ShimpyoEngine
    }

    override fun onReceive(context: Context, intent: Intent) {
        val engine = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java).engine()
        val cmd = intent.getStringExtra("cmd")
        Log.i(TAG, "cmd=$cmd extras=${intent.extras?.keySet()?.associateWith { intent.extras?.get(it) }}")
        val engineIntent: EngineIntent? = when (cmd) {
            "panel" -> {
                DemoUi.setPanelVisible(intent.getBooleanExtra("show", true))
                null
            }
            "scale" -> EngineIntent.SetTimeScale(intent.getIntExtra("value", 1))
            "add" -> EngineIntent.AddDrivingMinutes(intent.getIntExtra("minutes", 30))
            "checkin" -> EngineIntent.TriggerCheckIn
            "arrive" -> EngineIntent.SimulateArrival
            "answer" -> EngineIntent.InjectAnswer(intent.getStringExtra("text").orEmpty())
            "answer_expected" -> engine.state.value.expectedAnswer?.let { EngineIntent.InjectAnswer(it) }
            "rest_now" -> EngineIntent.RestNow
            "yes" -> EngineIntent.AnswerProposal(true)
            "no" -> EngineIntent.AnswerProposal(false)
            "mode" -> runCatching { RestMode.valueOf(intent.getStringExtra("mode").orEmpty()) }.getOrNull()
                ?.let { EngineIntent.ChooseMode(it) }
            "wake" -> EngineIntent.WakeNow
            "end" -> EngineIntent.EndRest
            "depart" -> EngineIntent.Depart
            "more" -> EngineIntent.FiveMoreMinutes
            else -> {
                Log.w(TAG, "unknown cmd=$cmd")
                null
            }
        }
        engineIntent?.let(engine::onIntent)
    }

    private companion object {
        const val TAG = "ShimpyoDemo"
    }
}
