package com.shimpyo.app.domain.logic

import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

enum class RestAdvice { NONE, LIGHT_REST, STRONG_REST }

/**
 * Draft thresholds from the scenario spec (research/03_user_scenario.md §7). Values are not
 * clinically validated and need tuning with real drives.
 *
 * TODO(spec): The car's own driver-attention warning (Telltale DRIVER_ATTENTION) would be a stronger
 *  signal, but registerTelltales needs PRIVATE_CAR_SAFETY (signature|privileged). Ask Pleos App Market
 *  whether a third-party app can receive DAW events before adding it.
 */
@Singleton
class FatigueRules @Inject constructor() {

    /** Minutes of continuous driving before the first voice check-in. */
    fun firstCheckInMinutes(now: LocalTime): Int = if (isCircadianDip(now)) 60 else 90

    fun advise(sleepiness: Int?, continuousMinutes: Int): RestAdvice = when {
        (sleepiness ?: 0) >= 7 || continuousMinutes >= 120 -> RestAdvice.STRONG_REST
        (sleepiness ?: 0) >= 5 -> RestAdvice.LIGHT_REST
        else -> RestAdvice.NONE
    }

    /** A stop of this length (gear P) counts as a real break and resets continuous driving. */
    val restCountsAfterMinutes: Int = 10
    val snoozeAfterDeclineMinutes: Int = 20
    val recheckAfterFineMinutes: Int = 30
    val maxDeclinesBeforeQuiet: Int = 3

    /** Night (02–06) and post-lunch (14–16) dips when drowsiness rises. */
    fun isCircadianDip(now: LocalTime): Boolean {
        val h = now.hour
        return h in 2..5 || h in 14..15
    }
}
